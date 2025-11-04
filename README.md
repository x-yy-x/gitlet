# gitlet
A functional command-line implementation of a distributed version control system, modeled after Git. Completed as a major project for UC Berkeley's CS 61B.


## Classes and Data Structures

### Main
This is the entry point of the program. It takes in arguments from the command line and based on the command calls the corresponding command in Repository, which will actually execute the logic of the command.It also validates the arguments based on the command to ensure that they are valid.

#### Fields
This class has no fields.



### Repository
This is where the main logic of our program will live.It handles with the command and use serialization to store content.
#### Fields
all the files and directory path needed


### Commit
This class represents a single snapshot in the project history. A Commit is content addressable via its unique SHA-1 ID.

#### Fields
1.private final String message -- store the message of this Commit

2.private final Date timestamp -- store the date when the Commit is commited

3.private TreeMap<String, String> findBlobId -- The file tree map, mapping a tracked file path (String) to its content SHA-1 ID (Blob ID). Using tree map instead of hash map prevents the inconsistent SHA-1 value when deserialized.

4.private final String parent1ID -- store the SHA-1 ID of the primary parent commit. Storing the ID prevents expensive recursive deserialization of the commit chain.

5.private final String parent2ID -- The SHA-1 ID of the secondary parent, present only in merge commits.

### Utils
This class contains essential helper utilities. It handles low-level tasks such as calculating SHA-1 hashes for files and objects, and performing reliable I/O operations (reading/writing raw content, object serialization/deserialization) on the file system.
#### Fields
static final int UID_LENGTH = 40 -- The length of a complete SHA-1 UID as a hexadecimal numeral.

## Algorithms
The system utilizes a Directed Acyclic Graph (DAG) to model history and relies on content-addressing (SHA-1) for data integrity.

### Add/Commit
Add: CWD files are read, converted to Blobs, and their content SHA-1 is calculated. Files are staged by writing their content into the addStage/ if they differ from the current HEAD.

Commit: A new Commit object is created. Its tree map is inherited from the parent and updated with files from the staging area. The Commit object is serialized, its final SHA-1 is computed, and the active branch pointer and head pointer are updated to point to the new Commit.
### Log/Global-Log/Find
History is viewed by traversing the Commit DAG backward from the current HEAD, following the parent1ID pointer. This traversal is used for log (current branch).
While for global-log (all commits) and find (searching by message), just list all the files in Commit Directory and search.

### Status
The command compares three states to determine repository condition:

* HEAD Commit's tracked files.

* Staging Area (addStage and removeStage).

* Working Directory (CWD).

The check reports four categories of file states:

* Staged for Addition/Removal: Files explicitly marked in the staging area.

* Modifications Not Staged for Commit: Files that are tracked but have been modified in the CWD, or files that were staged but then modified/deleted in the CWD.

* Untracked Files: Rest (Files present in the CWD but not tracked by the HEAD commit nor present in the staging area, and that would be destroyed by a checkout or reset.)

### Branch / Rm-Branch
Branch: Creates a new file in the branches directory whose name is the new branch name and whose content is the SHA-1 ID of the current HEAD commit.

Rm-Branch: Deletes the corresponding branch file from the branches directory, removing the reference pointer.

### Checkout/Reset
Restoration: These commands overwrite the Working Directory (CWD) to match a specific snapshot. Based on arguments (checkout from previous commit, specific commit or branch), they use a target Commit's file map to locate the required Blob content by SHA-1 in the blobs directory and write the raw content back to the CWD.

### Merge/Split Point:
Split Point: The latest common ancestor is found using Breadth-First Search (BFS), starting from both branch heads and meeting at the overlap in their history.

Resolution: File states are compared across the Split, Current HEAD, and Given Branch HEAD to determine necessary modifications, resulting in: fast-forwarding, non-conflicting updates (applied and staged), or conflicts (manually flagged in the CWD).

### Remote Operations (`Add-remote`, `Rm- remote`, `Push`, `Fetch`, `Pull`):
Add-remote simply connects to the remote repository by copying its file path into remotes directory. And rm-remote removes it.

`Push`:
Attempts to append the current branch’s commits to the end of the given branch at the given remote, updating the remote branch pointer to the local branch's HEAD commit ID.

`Fetch`:
Traverses the remote branch's history to identify all Commits and Blobs missing from the local repository. Copies all missing objects by their SHA-1 ID into the local objects/ directory. Creates/updates a local remote-tracking branch pointer ([remote]/[branch]) pointing to the remote's HEAD commit ID.

`Pull`:
This command simply executes fetch on the specified remote branch, followed immediately by a merge of the newly created local remote-tracking branch into head.

## Persistence
All persistent data is stored within the .gitlet directory using serialization (for metadata) and raw file storage (for content).

Serialization: Commit objects, the staging area's file maps, and the remote configurations are persisted using Java's object serialization (or a utility like writeObject/readObject) to preserve their complex structure.

Content Storage: Blob content and branch pointers (containing only a single commit ID) are stored as plain text files.

Detailed directory structure:

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
