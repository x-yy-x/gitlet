package gitlet;

import java.io.File;
import java.util.*;

import static gitlet.Utils.*;


/** Represents a gitlet repository.
 *  handles all the command here at the back end.
 *  does at a high level.
 *
 *  @author x-yy-x
 */
public class Repository {
    /* the file structure:
        .gitlet/
        - objects/
            - commits/
                - ...files of commits (class serialization) sha1:serializedCommit
            - blobs/
                - ...files of blob (file content) sha1:fileContent
        - branches/
            - master
            - other branches branchName:commitID(sha1)
        - head (latest version) commitID
        - branch (latest branch) name
        - stages/
            - addStage/ (Blobs) name:content
            - removeStage/ name:name(just for convenience because we don't care what has been removed)
        - remotes/
            - remote-name name:location

     */
    /** The current working directory. */
    private static  File CWD = new File(System.getProperty("user.dir"));
    /** The .gitlet directory. */
    public static  File GITLET_DIR = join(CWD, ".gitlet");
    /** The objects directory **/
    private static  File OBJECTS_DIR = join(GITLET_DIR, "objects");
    /** The commits directory， containing serialized Commit objects. */
    public static  File COMMITS_DIR = join(OBJECTS_DIR, "commits");
    /** The blobs directory, containing Blob (file content) objects. */
    private static  File BLOBS_DIR = join(OBJECTS_DIR, "blobs");
    /** The branches directory, containing branch name files, which hold HEAD Commit IDs */
    private static  File BRANCHES_DIR = join(GITLET_DIR, "branches");
    /** The stages directory */
    private static  File STAGES_DIR = join(GITLET_DIR, "stages");
    /** The addStage directory */
    private static  File ADDSTAGE_DIR = join(STAGES_DIR, "addStage");
    /** The removeStage directory */
    private static  File REMOVESTAGE_DIR = join(STAGES_DIR, "removeStage");
    /** The remotes directory */
    private static  File REMOTES_DIR = join(GITLET_DIR, "remotes");

    private  File headFIle = join(GITLET_DIR, "head");
    private  File currentBranchFile = join(GITLET_DIR, "branch");

    Repository() {
    }

    /* create the whole directory */
    private void createInitialDirectory() {
        GITLET_DIR.mkdir();
        OBJECTS_DIR.mkdir();
        COMMITS_DIR.mkdir();
        BLOBS_DIR.mkdir();
        BRANCHES_DIR.mkdir();
        STAGES_DIR.mkdir();
        ADDSTAGE_DIR.mkdir();
        REMOVESTAGE_DIR.mkdir();
        REMOTES_DIR.mkdir();
    }

    /**
     * init command
     * Creates a new Gitlet version-control system in the current directory.
     * automatically start with one commit: a commit that contains no files and has the commit message initial commit
     * it will have a single branch: master
     */
    public void init() {
        if (GITLET_DIR.exists()) {
            System.out.println("A Gitlet version-control system already exists in the current directory.");
            return;
        }

        createInitialDirectory();

        // get time
        long epochTimeMillis = 0L;
        Date initialDate = new Date(epochTimeMillis);

        Commit initialCommit = new Commit("initial commit", initialDate, new TreeMap<String, String>(), null, null);
        String commitID = sha1OfObject(initialCommit);

        // set branch and head
        setHeadAndBranch(commitID, "master");
        saveCommit(initialCommit, commitID);
    }


    /**
     * save the commit
     * @param commit: the commit to be saved
     * @param commitId: the sha1 of commit
     */
    private void saveCommit(Commit commit, String commitId) {
        File commitFile = join(COMMITS_DIR, commitId);
        writeObject(commitFile, commit);
    }


    /** point the head to the commit given as well as current branch */
    private void setHeadAndBranch(String commitID, String branchName) {
        // set the current branch
        writeContents(currentBranchFile, branchName);

        // write in or overwrite the content of current branch (in the branches directory)
        File currentBranchContent = join(BRANCHES_DIR, branchName);
        writeContents(currentBranchContent, commitID);

        // set head
        writeContents(headFIle, commitID);
    }


    /// check if there is an initialized gitlet working directory
    private boolean checkIsInitialized() {
        if (GITLET_DIR.exists()) {
            return true;
        }
        System.out.println("Not in an initialized Gitlet directory.");
        return false;
    }

    /** add command
     * Adds a copy of the file as it currently exists to the staging area
     * overwrites the previous entry in the staging area with the new contents
     * @param fileName: the modified CWD file to be added in the staging area
     */
    public void add(String fileName) {
        if (!checkIsInitialized()) {
            return;
        }

        File fileToBeStaged = join(ADDSTAGE_DIR, fileName);
        File userFile = join(CWD, fileName);
        if (!userFile.exists()) {
            System.out.println("File does not exist.");
            return;
        }

        Commit latestCommit = getLatestCommit();

        String sha1ofUserFile = sha1OfFile(userFile);

        // if it is in removeStage
        File sameFileInRemoveStage = join(REMOVESTAGE_DIR, fileName);
        if (sameFileInRemoveStage.exists()) {
            restrictedDelete(sameFileInRemoveStage);
        }

        // if it is identical to previous version
        if (Objects.equals(latestCommit.getMapFromFilenameToId().get(fileName), sha1ofUserFile)) {
            restrictedDelete(fileToBeStaged);
            return;
        }

        // write or overwrite
        writeContentsInFile(fileToBeStaged, userFile);
    }

    private Commit getLatestCommit() {
        String latestCommitId = readContentsAsString(headFIle);
        File commitNeededFile = join(COMMITS_DIR, latestCommitId);
        return readObject(commitNeededFile, Commit.class);
    }


    /** commit command
     * Saves a snapshot of tracked files in the current commit and staging area
     * so they can be restored at a later time, creating a new commit.
     * @param message: commit message
     * @param parent2: the commit(of the other branch) to be merged from. If isn't a merge commit, just pass in null.
     */
    public void commit(String message, Commit parent2) {
        if (!checkIsInitialized()) {
            return;
        }

        // get time
        Date currentDate = new Date();

        // get latest commit
        Commit latestCommit = getLatestCommit();

        // track the file
        TreeMap<String, String> nameToBlobId = new TreeMap<>(latestCommit.getMapFromFilenameToId());

        List<String> filesInAddStage = plainFilenamesIn(ADDSTAGE_DIR);
        List<String> filesInRemoveStage = plainFilenamesIn(REMOVESTAGE_DIR);

        if (filesInAddStage.isEmpty() && filesInRemoveStage.isEmpty()) {
            System.out.println("No changes added to the commit.");
            return;
        }

        /// handle addStage
        for (String addFileName: filesInAddStage) {
            File sourceFile = join(ADDSTAGE_DIR, addFileName);
            String sha1ofThisBlob = sha1OfFile(sourceFile);
            File destinationFile = join(BLOBS_DIR, sha1ofThisBlob);
            writeContentsInFile(destinationFile, sourceFile);
            restrictedDelete(sourceFile);
            nameToBlobId.put(addFileName, sha1ofThisBlob);
        }

        // handle RemoveStage
        for (String removeFileName: filesInRemoveStage) {
            File sourceFile = join(REMOVESTAGE_DIR, removeFileName);
            restrictedDelete(sourceFile);
            nameToBlobId.remove(removeFileName);
        }

        // then commit
        Commit newCommit = new Commit(message, currentDate, nameToBlobId, latestCommit, parent2);
        String commitID = sha1OfObject(newCommit);

        // set branch and head
        setHeadAndBranch(commitID, readContentsAsString(currentBranchFile));
        saveCommit(newCommit, commitID);
    }


    /** rm command
     * Unstage the file if it is currently staged for addition.
     * If the file is tracked in the current commit, stage it for removal and remove the file from CWD
     * @param fileToBeRemoved:file to be removed
     */
    public void rm(String fileToBeRemoved) {
        if (!checkIsInitialized()) {
            return;
        }

        //if it is currently staged for addition
        File imaginedAddStageFile = join(ADDSTAGE_DIR, fileToBeRemoved);
        Commit latestCommit = getLatestCommit();
        boolean hasWork = false;
        if (imaginedAddStageFile.exists()) {
            hasWork = true;
            restrictedDelete(imaginedAddStageFile);
        }

        if (latestCommit.getMapFromFilenameToId().containsKey(fileToBeRemoved)) {
            hasWork = true;
            // latestCommit.findBlobId.remove(fileToBeRemoved);
            restrictedDelete(join(CWD, fileToBeRemoved));
            File fileInRemoveStage = join(REMOVESTAGE_DIR, fileToBeRemoved);
            writeContents(fileInRemoveStage, fileToBeRemoved);
        }

        if (!hasWork) {
            System.out.println("No reason to remove the file.");
        }
    }


    /** log commit
     * Starting at the current head commit
     * display information about each commit backwards along the commit tree until the initial commit
     * ignoring any second parents found in merge commits
     */
    public void log() {
        if (!checkIsInitialized()) {
            return;
        }

        Commit currentCommit = getLatestCommit();

        while (currentCommit != null) {
            System.out.println(currentCommit.toString());

            String parentID = currentCommit.getParent1ID();

            if (Objects.equals(parentID, "")) {
                currentCommit = null; // initial commit
            } else {
                currentCommit = currentCommit.getParent1();
            }
        }
    }


    /** global-log commit
     * displays information about all commits ever made.
     */
    public void globalLog() {
        if (!checkIsInitialized()) {
            return;
        }

        List<String> sha1OfAllCommits = plainFilenamesIn(COMMITS_DIR);

        for (String fileName: sha1OfAllCommits) {
            File commitContentFile = join(COMMITS_DIR, fileName);
            System.out.println(readObject(commitContentFile, Commit.class).toString());
        }
    }


    /** find command
     *  Prints out the ids of all commits that have the given commit message
     * @param message: the message the commit is expected to have
     */
    public void find(String message) {
        if (!checkIsInitialized()) {
            return;
        }

        List<String> sha1OfAllCommits = plainFilenamesIn(COMMITS_DIR);
        boolean find = false;

        for (String fileName: sha1OfAllCommits) {
            File commitContentFile = join(COMMITS_DIR, fileName);
            Commit current = readObject(commitContentFile, Commit.class);
            if (Objects.equals(current.getMessage(), message)) {
                System.out.println(sha1OfObject(current));
                find = true;
            }
        }

        if (!find) {
            System.out.println("Found no commit with that message.");
        }
    }


    /** status command
     * Displays what branches currently exist
     * also displays what files have been staged for addition or removal
     * as well as modifications not staged and untracked files
     */
    public void status() {
        if (!checkIsInitialized()) {
            return;
        }
        System.out.println("=== Branches ===");

        List<String> branchesNames = plainFilenamesIn(BRANCHES_DIR);
        String currentBranchName = readContentsAsString(currentBranchFile);

        // branches section
        for (String branchName: branchesNames) {
            if (currentBranchName.equals(branchName)) {
                System.out.println("*" + branchName);
                continue;
            }
            System.out.println(branchName);
        }
        System.out.println();

        // List<String> cwdFileNames = plainFilenamesIn(CWD);   this is immutable

        // getting rid of other 3 kinds, the remains are the untracked files
        Set<String> allFilesInCWD = new HashSet<>(plainFilenamesIn(CWD));
        Commit latestCommit = getLatestCommit();
        Set<String> trackedFiles = latestCommit.getMapFromFilenameToId().keySet();
        Set<String> modificationsNotStagedDeleted = new HashSet<>();
        Set<String> modificationsNotStagedModified = new HashSet<>();

        // stage file section
        System.out.println("=== Staged Files ===");
        List<String> addFileNames = plainFilenamesIn(ADDSTAGE_DIR);
        for (String addFilename: addFileNames) {
            File addFile = join(ADDSTAGE_DIR, addFilename);
            File currentFile = join(CWD, addFilename);
            if (currentFile.exists()) {
                if (!sha1OfFile(currentFile).equals(sha1OfFile(addFile))) {
                    // Staged for addition, but with different contents than in the working directory;
                    modificationsNotStagedModified.add(addFilename);
                }
                // rules out files present in the working directory but neither staged for addition
                allFilesInCWD.remove(addFilename);
            } else {
                // Staged for addition, but deleted in the working directory.
                modificationsNotStagedDeleted.add(addFilename);
            }
            System.out.println(addFilename);
        }
        System.out.println();

        // remove file section
        System.out.println("=== Removed Files ===");
        List<String> removeFilenames = plainFilenamesIn(REMOVESTAGE_DIR);
        for (String filename: removeFilenames) {
            if (join(CWD, filename).exists() && !trackedFiles.contains(filename)) {
                // This includes files that have been staged for removal while re-created without Gitlet’s knowledge.
                allFilesInCWD.add(filename);
            }
            System.out.println(filename);
        }
        System.out.println();


        // next 2 sections
        for (String trackedFileName: trackedFiles) {
            File cwdFile = join(CWD, trackedFileName);
            String sha1OfTrackedFile = latestCommit.getMapFromFilenameToId().get(trackedFileName);
            if (!cwdFile.exists()) {
                if (!join(REMOVESTAGE_DIR, trackedFileName).exists()) {
                    // Not staged for removal, but tracked in the current commit and deleted from the working directory.
                    modificationsNotStagedDeleted.add(trackedFileName);
                }
            } else {
                if (!sha1OfFile(cwdFile).equals(sha1OfTrackedFile) && !join(ADDSTAGE_DIR, trackedFileName).exists()) {
                    // Tracked in the current commit, changed in the working directory, but not staged.
                    modificationsNotStagedModified.add(trackedFileName);
                }
                if (allFilesInCWD.contains(trackedFileName)) {
                    // files present in the working directory but neither staged for tracked
                    allFilesInCWD.remove(trackedFileName);
                }
            }
        }

        ///  Modifications Not Staged For Commit
        System.out.println("=== Modifications Not Staged For Commit ===");

        /* Tracked in the current commit, changed in the working directory, but not staged.
           Staged for addition, but with different contents than in the working directory.
         */
        for (String fileName: modificationsNotStagedDeleted) {
            System.out.println(fileName + "(deleted)");
        }


        /* Staged for addition, but deleted in the working directory.
           Not staged for removal, but tracked in the current commit and deleted from the working directory.
         */
        for (String fileName: modificationsNotStagedModified) {
            System.out.println(fileName + "(modified)");
        }
        System.out.println();

        /// Untracked Files (files present in the working directory but neither staged for addition nor tracked)
        ///This includes files that have been staged for removal, but then re-created without Gitlet’s knowledge.
        System.out.println("=== Untracked Files ===");
        for (String fileName: allFilesInCWD) {
            System.out.println(fileName);
        }
        System.out.println();
    }


    /** branch command
     * Creates a new branch with the given name, and points it at the current head commit
     * @param newBranchName: the name of the new branch
     */
    public void branch(String newBranchName) {
        if (!checkIsInitialized()) {
            return;
        }

        String headOfCommit = readContentsAsString(headFIle);
        File newBranchFile = join(BRANCHES_DIR, newBranchName);
        if (newBranchFile.exists()) {
            System.out.println("A branch with that name already exists.");
            return;
        }
        writeContents(newBranchFile, headOfCommit);
    }


    /** checkout a file from head
     * Takes the version of the file as it exists in the head commit and puts it in the working directory
     * overwriting the version of the file that’s already there if there is one
     * The new version of the file is not staged.
     * @param filename: the name of the file to be checked out
     */
    public void checkoutForFilename(String filename) {
        if (!checkIsInitialized()) {
            return;
        }

        Commit latestCommit = getLatestCommit();
        if (latestCommit.getMapFromFilenameToId().containsKey(filename)) {
            File sourceFile = join(BLOBS_DIR, latestCommit.getMapFromFilenameToId().get(filename));
            writeContentsInFile(join(CWD, filename), sourceFile);
            return;
        }
        System.out.println("File does not exist in that commit.");
    }


    /** checkout file from specific commit
     * Takes the version of the file as it exists in the commit with the given id
     * puts it in the working directory
     * overwriting the version of the file that’s already there if there is one
     * The new version of the file is not staged.
     * @param commitId: sha1 of the commit
     * @param filename: the name of the file to be checked out
     */
    public void checkoutForSpecificFilename(String commitId, String filename) {
        if (!checkIsInitialized()) {
            return;
        }

        commitId = findFullCommitId(commitId);
        if (commitId.isEmpty()) {
            return;
        }
        File commitFile = join(COMMITS_DIR, commitId);

        Commit neededCommit = readObject(commitFile, Commit.class);
        if (neededCommit.getMapFromFilenameToId().containsKey(filename)) {
            File sourceFile = join(BLOBS_DIR, neededCommit.getMapFromFilenameToId().get(filename));
            writeContentsInFile(join(CWD, filename), sourceFile);
            return;
        }
        System.out.println("File does not exist in that commit.");
    }


    /** checkout branch
     * Takes all files in the commit at the head of the given branch
     * puts them in the working directory, overwriting the versions of the files that are already there if they exist
     * Also, at the end of this command, the given branch will now be considered the current branch (HEAD)
     * The staging area is cleared
     * @param branchName: the branch to be checked out
     */
    public void checkoutBranch(String branchName) {
        if (!checkIsInitialized()) {
            return;
        }

        File branchFile = join(BRANCHES_DIR, branchName);
        if (!branchFile.exists()) {
            System.out.println("No such branch exists.");
            return;
        }

        if (branchName.equals(readContentsAsString(currentBranchFile))) {
            System.out.println("No need to checkout the current branch.");
            return;
        }

        Commit latestCommit = getLatestCommit();
        Set<String> trackedFiles = latestCommit.getMapFromFilenameToId().keySet();
        List<String> addStageFiles = plainFilenamesIn(ADDSTAGE_DIR);
        List<String> cwdFiles = plainFilenamesIn(CWD);

        String destSHA1 = readContentsAsString(branchFile);
        Commit commitOfBranch = readObject(join(COMMITS_DIR, destSHA1), Commit.class);
        Set<String> filesNeeded = commitOfBranch.getMapFromFilenameToId().keySet();


        if (hasUntrackedFileToBeOverwritten(cwdFiles, trackedFiles, addStageFiles, filesNeeded)) {
            return;
        }


        for (String fileNeededName: filesNeeded) {
            File neededFile = join(CWD, fileNeededName);
            File sourceFile = join(BLOBS_DIR, commitOfBranch.getMapFromFilenameToId().get(fileNeededName));
            writeContentsInFile(neededFile, sourceFile);
        }

        // Any files that are tracked in the current branch but are not present in the checked-out branch are deleted.
        for (String trackedFileName: trackedFiles) {
            if (!filesNeeded.contains(trackedFileName)) {
                restrictedDelete(join(CWD, trackedFileName));
            }
        }

        clearDir(ADDSTAGE_DIR);
        clearDir(REMOVESTAGE_DIR);

        setHeadAndBranch(destSHA1, branchName);
    }


    /** remove branch command
     *  Deletes the branch with the given name.(just a pointer) */
    public void removeBranch(String branchName) {
        if (!checkIsInitialized()) {
            return;
        }

        File branchFileToBeRemoved = join(BRANCHES_DIR, branchName);
        if (!branchFileToBeRemoved.exists()) {
            System.out.println("A branch with that name does not exist.");
            return;
        }

        if (readContentsAsString(currentBranchFile).equals(branchName)) {
            System.out.println("Cannot remove the current branch.");
            return;
        }

        restrictedDelete(branchFileToBeRemoved);
    }


    /**  If a working file is untracked in the current branch and would be overwritten by the reset
     *  print `There is an untracked file in the way; delete it, or add and commit it first.`
     *  and exit */
    boolean hasUntrackedFileToBeOverwritten(List<String> cwdFiles, Set<String> trackedFiles, List<String> addStageFiles,
                                            Set<String> filesNeeded) {
        for (String cwdFilename: cwdFiles) {
            if (!trackedFiles.contains(cwdFilename)
                    && !addStageFiles.contains(cwdFilename)
                    && filesNeeded.contains(cwdFilename)) {
                System.out.println("There is an untracked file in the way; delete it, or add and commit it first.");
                return true;
            }
        }
        return false;
    }


    /// abbreviated commitId to full id
    private String findFullCommitId(String shortId) {
        List<String> allCommitIds = plainFilenamesIn(COMMITS_DIR);

        for (String currentId: allCommitIds) {
            if (currentId.startsWith(shortId)) {
                return currentId;
            }
        }

        System.out.println("No commit with that id exists.");
        return "";
    }


    /** reset command
     *  Checks out all the files tracked by the given commit.
     * @param commitID: the given commitID(sha1)
     */
    public void reset(String commitID, boolean notRemote) {
        if (!checkIsInitialized()) {
            return;
        }

        commitID = findFullCommitId(commitID);
        if (commitID.isEmpty()) {
            return;
        }

        Commit commitNeeded = readObject(join(COMMITS_DIR, commitID), Commit.class);
        Set<String> fileNeededNames = commitNeeded.getMapFromFilenameToId().keySet();

        Commit latestCommit = getLatestCommit();
        Set<String> trackedFiles = latestCommit.getMapFromFilenameToId().keySet();
        List<String> addStageFiles = plainFilenamesIn(ADDSTAGE_DIR);
        if (notRemote) {
            List<String> cwdFiles = plainFilenamesIn(CWD);

            if (hasUntrackedFileToBeOverwritten(cwdFiles, trackedFiles, addStageFiles, fileNeededNames)) {
                return;
            }

            // Removes tracked files that are not present in that commit.
            for (String cwdFile : cwdFiles) {
                if (!fileNeededNames.contains(cwdFile)) {
                    restrictedDelete(join(CWD, cwdFile));
                }
            }
        }

        // checks out all the files tracked by the given commit.
        for (String fileNeededName: fileNeededNames) {
            checkoutForSpecificFilename(commitID, fileNeededName);
        }


        // the staging area is cleared
        clearDir(ADDSTAGE_DIR);
        clearDir(REMOVESTAGE_DIR);

        setHeadAndBranch(commitID, getCurrentBranchName());
    }


    /// get current branch name
    String getCurrentBranchName() {
        return readContentsAsString(currentBranchFile);
    }

    /// get commit of the branch given
    Commit getCommitOfBranch(String branchName) {
        String objSha1 = readContentsAsString(join(BRANCHES_DIR, branchName));
        File objFile = join(COMMITS_DIR, objSha1);
        return readObject(objFile, Commit.class);
    }


    /** merge command
     * Merges files from the given branch into the current branch.
     * Find the split commit first (using BFS)
     * Compare the state of each file in current commit, branch commit and split commit (modified or not, exist or not)
     * and handle each file correspondingly
     * @param branchName: the name of the branch to be merged from
     */
    public void merge(String branchName) {
        if (!checkIsInitialized()) {
            return;
        }

        List<String> cwdFileNames = plainFilenamesIn(CWD);
        List<String> addStageFileNames = plainFilenamesIn(ADDSTAGE_DIR);
        List<String> removeStageFileNames = plainFilenamesIn(REMOVESTAGE_DIR);

        // If there are staged additions or removals present
        if (!addStageFileNames.isEmpty() || !removeStageFileNames.isEmpty()) {
            System.out.println("You have uncommitted changes.");
            return;
        }

        // If a branch with the given name does not exist
        if (!join(BRANCHES_DIR, branchName).exists()) {
            System.out.println("A branch with that name does not exist.");
            return;
        }

        if (getCurrentBranchName().equals(branchName)) {
            System.out.println("Cannot merge a branch with itself.");
            return;
        }

        Commit splitCommit = getSplitPoint(branchName);
        Commit currentCommit = getLatestCommit();
        Commit branchCommit = getCommitOfBranch(branchName);

        /*  If an untracked file in the current commit would be overwritten or deleted by the merge
         print There is an untracked file in the way; delete it, or add and commit it first.
         and exit */
        for (String cwdfile: cwdFileNames) {
            if (!currentCommit.getMapFromFilenameToId().containsKey(cwdfile)) {
                System.out.println("There is an untracked file in the way; delete it, or add and commit it first.");
                return;
            }
        }



        if (branchCommit.equals(splitCommit)) {
            System.out.println("Given branch is an ancestor of the current branch.");
            return;
        }

        if (currentCommit.equals(splitCommit)) {
            checkoutBranch(branchName);
            System.out.println("Current branch fast-forwarded.");
            return;
        }

        Set<String> allFileNames = new HashSet<>(splitCommit.getMapFromFilenameToId().keySet());
        allFileNames.addAll(currentCommit.getMapFromFilenameToId().keySet());
        allFileNames.addAll(branchCommit.getMapFromFilenameToId().keySet());

        boolean conflictOccurred = false;
        for (String fileName: allFileNames) {
            switch (checkWhatToDoInMerge(fileName, currentCommit, splitCommit, branchCommit)) {
                case SIGN_TO_CHANGE:
                    changeFileTo(fileName, branchCommit);
                    break;
                case SIGN_TO_REMAIN:
                    break;
                case SIGN_OF_CONFLICT:
                    conflictOccurred = true;
                    updateConflictFile(fileName, branchCommit);
                    break;
                default:
                    break;
            }
        }
        String mergeMessage = "Merged " + branchName + " into " + getCurrentBranchName() + ".";
        commit(mergeMessage, branchCommit);

        if (conflictOccurred) {
            System.out.println("Encountered a merge conflict.");
        }
    }

    /// if the file state in split and current is the same (while different from branch), change to the branch state
    private void changeFileTo(String fileName, Commit branch) {
        Commit current = getLatestCommit();
        if (branch.getMapFromFilenameToId().containsKey(fileName)) {
            checkoutForSpecificFilename(sha1OfObject(branch), fileName);
            add(fileName);
        } else {
            rm(fileName);
        }
    }

    /// get content from the file in specific commit
    private String getBlobContent(Commit commit, String fileName) {
        if (commit.getMapFromFilenameToId().containsKey(fileName)) {
            String blobId = commit.getMapFromFilenameToId().get(fileName);
            return readContentsAsString(join(BLOBS_DIR, blobId));
        }
        return "";
    }

    /** if the file state in either current or branch is different from split (while they themselves are not the same)
     *  handle a conflict
     */
    private void updateConflictFile(String fileName, Commit givenBranch) {
        Commit currentHead = getLatestCommit();

        String headContent = getBlobContent(currentHead, fileName);
        String branchContent = getBlobContent(givenBranch, fileName);

        String content = "<<<<<<< HEAD" + System.lineSeparator()
                + headContent
                + "=======" + System.lineSeparator()
                + branchContent + ">>>>>>>" + System.lineSeparator();

        writeContents(join(CWD, fileName), content);

        add(fileName);
    }


    private static final int SIGN_TO_CHANGE = 1;
    private static final int SIGN_TO_REMAIN = 2;
    private static final int SIGN_OF_CONFLICT = 3;
    /// based on states of current, split and branch, decide what to do.
    private int checkWhatToDoInMerge(String fileName, Commit current, Commit split, Commit branch) {
        if (isSameInCommit(fileName, current, split) 
                && isSameInCommit(fileName, current, branch) 
                && isSameInCommit(fileName, split, branch)) {
            return SIGN_TO_REMAIN;
        } else if (!isSameInCommit(fileName, current, split) 
                && !isSameInCommit(fileName, current, branch) 
                && !isSameInCommit(fileName, split, branch)) {
            return SIGN_OF_CONFLICT;
        } else {
            if (isSameInCommit(fileName, split, current)) {
                return SIGN_TO_CHANGE;
            } else if (isSameInCommit(fileName, split, branch)) {
                return SIGN_TO_REMAIN;
            } else {
                return SIGN_TO_REMAIN;
            }
        }
    }

    /// check if the states of a file in two commits are the same (exist or not, modified or not)
    private boolean isSameInCommit(String fileName, Commit commit1, Commit commit2) {
        boolean c1Contains = commit1.getMapFromFilenameToId().containsKey(fileName);
        boolean c2Contains = commit2.getMapFromFilenameToId().containsKey(fileName);
        String s1 = commit1.getMapFromFilenameToId().get(fileName);
        String s2 = commit2.getMapFromFilenameToId().get(fileName);

        if (c1Contains && c2Contains) {
            return s1.equals(s2);
        } else {
            return !c1Contains && !c2Contains;
        }
    }


    /// get the latest common ancestor of the current and given branch heads, using BFS
    private Commit getSplitPoint(String branchName) {
        Commit headCommit = getLatestCommit();
        Commit branchCommit = getCommitOfBranch(branchName);

        // build history from head commit
        HashSet<Commit> currentAncestors = new HashSet<>();
        bfsSearchHistoryFrom(headCommit, currentAncestors, null);

        // search whether the history of branch commit and that of head overlap, return the split point
        return bfsSearchHistoryFrom(branchCommit, new HashSet<>(), currentAncestors);
    }


    /** helper method: find whether the history of start commit contains Commit in destCommit
     * usage1: if no destCommit is passed in, build the searched history til init
     * usage2: else, find the first commit present in destCommit
     */
    private Commit bfsSearchHistoryFrom(Commit start, HashSet<Commit> visitedBranch, HashSet<Commit> destCommit) {
        Queue<Commit> queue = new LinkedList<>();
        visitedBranch.add(start);
        queue.offer(start);
        while (!queue.isEmpty()) {
            Commit current = queue.poll();
            Commit parent1 = current.getParent1();
            Commit parent2 = current.getParent2();

            if (destCommit != null) {
                if (destCommit.contains(current)) {
                    return current;
                }
            }

            // climb along parent1
            if (parent1 != null && !visitedBranch.contains(parent1)) {
                visitedBranch.add(parent1);
                queue.offer(parent1);
            }
            // climb along parent2
            if (parent2 != null && !visitedBranch.contains(parent2)) {
                visitedBranch.add(parent2);
                queue.offer(parent2);
            }
        }

        return null;
    }


    /// remotes commands

    /** helper method: change CWD and every directory related
     * in convenience for reuse the former code
     */
    private void changeCwd(File dest) {
        /** The current working directory. */
        CWD = dest;
        /** The .gitlet directory. */
        GITLET_DIR = join(CWD, ".gitlet");
        /** The objects directory **/
        OBJECTS_DIR = join(GITLET_DIR, "objects");
        /** The commits directory */
        COMMITS_DIR = join(OBJECTS_DIR, "commits");
        /** The blobs directory */
        BLOBS_DIR = join(OBJECTS_DIR, "blobs");
        /** The branches directory */
        BRANCHES_DIR = join(GITLET_DIR, "branches");
        /** The stages directory */
        STAGES_DIR = join(GITLET_DIR, "stages");
        /** The addStage directory */
        ADDSTAGE_DIR = join(STAGES_DIR, "addStage");
        /** The removeStage directory */
        REMOVESTAGE_DIR = join(STAGES_DIR, "removeStage");
        /** The remotes directory */
        REMOTES_DIR = join(GITLET_DIR, "remotes");

        headFIle = join(GITLET_DIR, "head");
        currentBranchFile = join(GITLET_DIR, "branch");
    }


    /** add-remote command
     * Saves the given login information under the given remote name.
     */
    public void addRemote(String remoteName, String remotePath) {
        File remoteFile = join(REMOTES_DIR, remoteName);

        if (remoteFile.exists()) {
            System.out.println("A remote with that name already exists.");
            return;
        }
        String systemSeparatedPath = remotePath.replace("/", File.separator);
        writeContents(remoteFile, systemSeparatedPath);
    }


    /**.rm-remote
     * Remove information associated with the given remote name.
     */
    public void rmRemote(String remoteName) {
        File remoteFile = join(REMOTES_DIR, remoteName);
        if (!remoteFile.exists()) {
            System.out.println("A remote with that name does not exist.");
            return;
        }
        restrictedDelete(remoteFile);
    }


    /** use string truncation to get the directory where the remote .gitlet is in
     * prepared for changing cwd back and forth (easier for code reuse)
     */
    private File getRemoteCwd(String remoteName) {
        String remoteGitletPath = readContentsAsString(join(REMOTES_DIR, remoteName));
        // prepare for the CWD change (reset)
        int lastSeparatorIndex = remoteGitletPath.lastIndexOf(File.separator);
        String remoteCwdPath = remoteGitletPath.substring(0, lastSeparatorIndex);
        return new File(remoteCwdPath);
    }


    /** push command
     * Attempts to append the current branch’s commits to the end of the given branch at the given remote.
     * This command only works if the remote branch’s head is in the history of the current local head
     */
    public void push(String remoteName, String remoteBranchName) {
        String remoteGitletPath = readContentsAsString(join(REMOTES_DIR, remoteName));
        File remoteCwdDir = getRemoteCwd(remoteName);
        File originCwd = CWD;

        File remoteGitletDir = new File(remoteGitletPath);

        if (!remoteGitletDir.exists()) {
            System.out.println("Remote directory not found.");
            return;
        }

        // temporarily change to remote repository
        changeCwd(remoteCwdDir);
        Commit remoteBranchHeadCommit = getCommitOfBranch(remoteBranchName);

        // If the remote branch’s head is not in the history of the current local head
        changeCwd(originCwd);
        Commit headCommit = getLatestCommit();
        HashSet<Commit> dest = new HashSet<>();
        dest.add(remoteBranchHeadCommit);
        HashSet<Commit> futureCommits = new HashSet<>();
        if (bfsSearchHistoryFrom(headCommit, futureCommits, dest) == null) {
            System.out.println("Please pull down remote changes before pushing.");
            return;
        }


        for (Commit commit: futureCommits) {
            copyCommit(commit, originCwd, remoteCwdDir);
        }
        String headId = sha1OfObject(headCommit);

        // change to remote repo
        changeCwd(remoteCwdDir);
        File currentBranchContent = join(BRANCHES_DIR, remoteBranchName);
        writeContents(currentBranchContent, headId);

        /* add an extra parameter for code reuse
           directly visit remote cwd would cause access control exception*/
        reset(headId, false);

        changeCwd(originCwd); // change back
    }


    /** helper method: copy a commit and all of its blobs from src repo to dest repo
     *
     * @param commit: the commit to be copied
     * @param src: where src repo lives
     * @param dest: where dest repo lives
     */
    private void copyCommit(Commit commit, File src, File dest) {
        changeCwd(src);
        File commitFileFromSrc = join(COMMITS_DIR, sha1OfObject(commit));
        changeCwd(dest);
        File commitFileFromDest = join(COMMITS_DIR, sha1OfObject(commit));
        writeContentsInFile(commitFileFromDest, commitFileFromSrc);

        // copy blobs
        for (String blobID: commit.getMapFromFilenameToId().values()) {
            changeCwd(src);
            File blobFileFromSrc = join(BLOBS_DIR, blobID);
            changeCwd(dest);
            File blobsFileFromDest = join(BLOBS_DIR, blobID);
            writeContentsInFile(blobsFileFromDest, blobFileFromSrc);
        }
    }


    /** fetch command
     * Brings down commits from the remote Gitlet repository into the local Gitlet repository.
     * into a branch named [remote name]/[remote branch name] in the local .gitlet
     */
    public void fetch(String remoteName, String remoteBranchName) {
        File originCwd = CWD;
        File remoteCwd = getRemoteCwd(remoteName);

        File remoteRepo = new File(readContentsAsString(join(REMOTES_DIR, remoteName)));
        if (!remoteRepo.exists()) {
            System.out.println("Remote directory not found.");
            return;
        }

        changeCwd(remoteCwd);
        File remoteBranchFile = join(BRANCHES_DIR, remoteBranchName);
        if (!remoteBranchFile.exists()) {
            System.out.println("That remote does not have that branch.");
            return;
        }

        // get all the commits from the given remote branch
        Commit remoteBranchHeadCommit = getCommitOfBranch(remoteBranchName);
        HashSet<Commit> remoteCommitsFromBranch = new HashSet<>();
        bfsSearchHistoryFrom(remoteBranchHeadCommit, remoteCommitsFromBranch, null);

        // copy
        for (Commit remoteCommit: remoteCommitsFromBranch) {
            copyCommit(remoteCommit, remoteCwd, originCwd);
        }
        // set back
        changeCwd(originCwd);

        // set the remote branch file in local
        File remoteNameUnderBranches = join(BRANCHES_DIR, remoteName);
        remoteNameUnderBranches.mkdir();
        File remoteBranchLocal = join(remoteNameUnderBranches, remoteBranchName);
        writeContents(remoteBranchLocal, sha1OfObject(remoteBranchHeadCommit));
    }


    /** pull command
     * fetch and merge
     */
    public void pull(String remoteName, String remoteBranchName) {
        fetch(remoteName, remoteBranchName);
        String remoteBranchNameLocal = remoteName + "/" + remoteBranchName;
        merge(remoteBranchNameLocal);
    }
}
