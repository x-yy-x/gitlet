package gitlet;

import java.util.Objects;

/** Driver class for Gitlet, a subset of the Git version-control system.
 *  @author x-yy-x
 */
public class Main {

    /** Usage: java gitlet.Main ARGS, where ARGS contains
     *  <COMMAND> <OPERAND1> <OPERAND2> ... 
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Please enter a command.");
            return;
        }
        Repository repo = new Repository();
        String firstArg = args[0];
        switch (firstArg) {
            case "init":
                // handle the `init` command
                if (isIncorrectOperands(args, 1)) {
                    return;
                }
                repo.init();
                break;
            case "add":
                // handle the `add [filename]` command
                if (isIncorrectOperands(args, 2)) {
                    return;
                }
                repo.add(args[1]);
                break;
            case "commit":
                // handle commit [message]
                if (isIncorrectOperands(args, 2)) {
                    return;
                }
                if (Objects.equals(args[1], "")) {
                    System.out.println("Please enter a commit message.");
                    return;
                }
                repo.commit(args[1], null);
                break;
            case "rm":
                // handle rm [file name]
                if (isIncorrectOperands(args, 2)) {
                    return;
                }
                repo.rm(args[1]);
                break;
            case "log":
                // handle log
                if (isIncorrectOperands(args, 1)) {
                    return;
                }
                repo.log();
                break;
            case "global-log":
                // handle global-log
                if (isIncorrectOperands(args, 1)) {
                    return;
                }
                repo.globalLog();
                break;
            case "find":
                // handle find
                if (isIncorrectOperands(args, 2)) {
                    return;
                }
                repo.find(args[1]);
                break;
            case "status":
                // handle status
                if (isIncorrectOperands(args, 1)) {
                    return;
                }
                repo.status();
                break;
            case "checkout":
                switch (args.length) {
                    case 2:
                        // handle 'checkout [branch name]'
                        repo.checkoutBranch(args[1]);
                        break;
                    case 3:
                        // handle 'checkout -- [file name]'
                        if (!args[1].equals("--")) {
                            System.out.println("Incorrect operands.");
                            break;
                        }
                        repo.checkoutForFilename(args[2]);
                        break;
                    case 4:
                        // handle 'checkout [commit id] -- [file name]'
                        if (!args[2].equals("--")) {
                            System.out.println("Incorrect operands.");
                            break;
                        }
                        repo.checkoutForSpecificFilename(args[1], args[3]);
                        break;
                    default:
                        break;
                }
                break;
            case "branch":
                //  handle 'branch [branch name]'
                if (isIncorrectOperands(args, 2)) {
                    return;
                }
                repo.branch(args[1]);
                break;
            case "rm-branch":
                // handle 'rm-branch [branch name]'
                if (isIncorrectOperands(args, 2)) {
                    return;
                }
                repo.removeBranch(args[1]);
                break;
            case "reset":
                // handle 'reset [commit id]'
                if (isIncorrectOperands(args, 2)) {
                    return;
                }
                repo.reset(args[1], true);
                break;
            case "merge":
                // handle 'merge [branch name]'
                if (isIncorrectOperands(args, 2)) {
                    return;
                }
                repo.merge(args[1]);
                break;
            case "add-remote":
                // handle 'add-remote [remote name] [name of remote directory]/.gitlet'
                if (isIncorrectOperands(args, 3)) {
                    return;
                }
                repo.addRemote(args[1], args[2]);
                break;
            case "rm-remote":
                // handle 'rm-remote [remote name]'
                if (isIncorrectOperands(args, 2)) {
                    return;
                }
                repo.rmRemote(args[1]);
                break;
            case "push":
                // handles 'push [remote name] [remote branch name]'
                if (isIncorrectOperands(args, 3)) {
                    return;
                }
                repo.push(args[1], args[2]);
                break;
            case "fetch":
                // handles 'fetch [remote name] [remote branch name]'
                if (isIncorrectOperands(args, 3)) {
                    return;
                }
                repo.fetch(args[1], args[2]);
                break;
            case "pull":
                // handles 'pull [remote name] [remote branch name]'
                if (isIncorrectOperands(args, 3)) {
                    return;
                }
                repo.pull(args[1], args[2]);
                break;
            default:
                System.out.println("No command with that name exists.");
        }
    }

    private static boolean isIncorrectOperands(String[] args, int num) {
        if (args.length != num) {
            System.out.println("Incorrect operands.");
            return true;
        }
        return false;
    }
}
