package gitlet;


import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date; // get current time
import java.util.Locale;
import java.util.TreeMap;

import static gitlet.Utils.*;

/** Represents a gitlet commit object.
 *  contains all the information needed for a commit
 *  does at a high level.
 *
 *  @author x-yy-x
 */
public class Commit implements Serializable {
    /** The message of this Commit. */
    private final String message;
    /** the time when constructing a commit */
    private final Date timestamp;
    /** mapping a filename to a blob's SHA-1 */
    private TreeMap<String, String> findBlobId;
    /** the sha1 value of its parents, using as pointer */
    private final String parent1ID;
    /* very important: can't be the Commit instance
       otherwise huge problem exists
       especially when this commit is deserialized from a file (because the location of parents changes)
     */
    private final String parent2ID;

    /** constructor */
    Commit(String message, Date timestamp, TreeMap<String, String> findBlobId, Commit parent1, Commit parent2) {
        this.message = message;
        this.timestamp = timestamp;
        this.findBlobId = findBlobId;
        this.parent1ID = parent1 == null ? "" : sha1OfObject(parent1);
        this.parent2ID = parent2 == null ? "" : sha1OfObject(parent2);
    }


    /** string representation of a commit */
    @Override
    public String toString() {
        String ans = "===" + System.lineSeparator()
                + "commit " + sha1OfObject(this) + System.lineSeparator();
        if (!this.parent2ID.isEmpty()) {
            ans += "Merge: " + parent1ID.substring(0, 7) + " " + parent2ID.substring(0, 7) + System.lineSeparator();
        }
        ans += getFormattedLogDate(this.timestamp) + System.lineSeparator() + this.message + System.lineSeparator();
        return ans;
    }


    private String getFormattedLogDate(Date commitDate) {
        SimpleDateFormat formatter = new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy Z", Locale.US);

        return "Date: " + formatter.format(commitDate);
    }


    /** dereference the sha1 pointer */
    public Commit getParent1() {
        if (parent1ID.isEmpty()) {
            return null;
        }
        return readObject(join(Repository.COMMITS_DIR, parent1ID), Commit.class);
    }

    public Commit getParent2() {
        if (parent2ID.isEmpty()) {
            return null;
        }
        return readObject(join(Repository.COMMITS_DIR, parent2ID), Commit.class);
    }

    @Override
    public int hashCode() {
        return sha1OfObject(this).hashCode();
    }

    /** very important
     * otherwise the same commit might have inconsistent hashcode when backtracking
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        Commit otherCommit = (Commit) other;

        return this.hashCode() == other.hashCode();
    }

    public TreeMap<String, String> getMapFromFilenameToId() {
        return findBlobId;
    }

    public String getMessage() {
        return message;
    }

    public String getParent1ID() {
        return parent1ID;
    }
}
