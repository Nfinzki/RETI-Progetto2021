import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class ClientMain {
    private static String configurationFile = "clientConfig.txt";

    private static int registryPort = 11111;
    private static String registryHost = "localhost";
    private static String registerServiceName = "RMI-REGISTER";
    private static String callbackServiceName = "RMI-FOLLOWER-CALLBACK";

    private static String serverIP = "localhost";
    private static int tcpPort = 2222;

    private static int bufferSize = 16 * 1024;

    public static void main(String []args) {
        //Checks if is specified a different configuration file
        if (args.length == 1) configurationFile = args[0];
        if (args.length > 1) {
            System.err.println("Usage: ClientMain [config file]");
            System.exit(1);
        }

        //Parses the configuration file
        parseConfigFile();

        //Initializes the Winsome API
        Winsome winsome = null;
        try {
            winsome = new Winsome(serverIP, tcpPort, registryHost, registryPort, registerServiceName, callbackServiceName, bufferSize);
        } catch (RemoteException e) {
            System.err.println("< Error while getting the registry: (" + e.getMessage() + ")");
            System.exit(1);
        } catch (NotBoundException e) {
            System.err.println("< Error while recovering stubs: (" + e.getMessage() + ")");
            System.exit(1);
        }

        //Initializes the scanner to read input from CLI
        Scanner cliScanner = new Scanner(System.in);
        String command;
        boolean termination = false;

        while (!termination) {
            System.out.print("> ");
            //Reads the command
            command = cliScanner.nextLine();

            String []arguments = command.split(" ");

            switch (arguments[0]) {
                case "register" -> {
                    if (arguments.length > 8) {
                        System.out.println("< Usage: register username password tags (max 5 tag)");
                        break;
                    }

                    //Extract the tag list from the command
                    List<String> tags = new ArrayList<>(Arrays.asList(arguments).subList(3, arguments.length));
                    winsome.register(arguments[1], arguments[2], tags);
                }

                case "login" -> {
                    if (command.split(" ").length != 3) {
                        System.out.println("< Usage: login username password");
                        break;
                    }

                    winsome.login(arguments[1], arguments[2]);
                }

                case "logout" -> {
                    if (command.split(" ").length != 1) {
                        System.out.println("< Usage: logout");
                        break;
                    }

                    winsome.logout();
                }

                case "list" -> {
                    if (arguments.length != 2 || (!arguments[1].equals("users") && !arguments[1].equals("following") && !arguments[1].equals("followers"))) {
                        System.err.println("< Invalid command '" + command + "'. Use 'list users', 'list followers' or 'list following'");
                        break;
                    }

                    if (arguments[1].equals("users")) { //list users
                        winsome.listUsers();
                    } else if (arguments[1].equals("followers")) { //list followers
                        winsome.listFollowers();
                    } else { //list following
                        winsome.listFollowing();
                    }
                }

                case "follow" -> {
                    if (arguments.length != 2) {
                        System.err.println("< Usage: follow <idUser>");
                        break;
                    }

                    winsome.followUser(arguments[1]);
                }

                case "unfollow" -> {
                    if (arguments.length != 2) {
                        System.err.println("< Usage: unfollow <idUser>");
                        break;
                    }

                    winsome.unfollowUser(arguments[1]);
                }

                case "blog" -> {
                    if (arguments.length != 1) {
                        System.err.println("< Usage: blog");
                        break;
                    }

                    winsome.viewBlog();
                }

                case "post" -> {
                    //Gets the indexes of the first string between quotes
                    int openingQuoteIndex = command.indexOf("\"");
                    int closingQuoteIndex = command.indexOf("\"", openingQuoteIndex + 1);
                    //Gets the post title
                    String title = command.substring(openingQuoteIndex + 1, closingQuoteIndex);

                    //Gets the indexes of the second string between quotes
                    openingQuoteIndex = command.indexOf("\"", closingQuoteIndex + 1);
                    closingQuoteIndex = command.indexOf("\"", openingQuoteIndex + 1);
                    //Gets the post content
                    String content = command.substring(openingQuoteIndex + 1, closingQuoteIndex);

                    //Checks if the title or the post content are empty
                    if (title.equals("") || content.equals("")) {
                        System.err.println("< Usage: post \"Title\" \"Content\"");
                        break;
                    }

                    winsome.createPost(title, content);
                }

                case "show" -> {
                    if (arguments.length < 2 || (!arguments[1].equals("feed") && !arguments[1].equals("post"))) {
                        System.err.println("Invalid command '" + command + "'. Use 'show feed' or 'show post'");
                        break;
                    }

                    if (arguments[1].equals("feed")) { //show feed
                        winsome.showFeed();
                    } else { //show post
                        if (arguments.length != 3) {
                            System.err.println("< Usage: show post <idPost>");
                            break;
                        }

                        winsome.showPost(arguments[2]);
                    }
                }

                case "delete" -> {
                    if (arguments.length != 2) {
                        System.err.println("< Usage: delete <idPost>");
                        break;
                    }

                    winsome.deletePost(arguments[1]);
                }

                case "rewin" -> {
                    if (arguments.length != 2) {
                        System.err.println("< Usage: rewin <idPost>");
                        break;
                    }

                    winsome.rewinPost(arguments[1]);
                }

                case "rate" -> {
                    if (arguments.length != 3) {
                        System.err.println("< Usage: rate <idPost> <vote>");
                        break;
                    }

                    winsome.ratePost(arguments[1], arguments[2]);
                }

                case "comment" -> {
                    //Gets the post id
                    String idPost = command.split(" ")[1];
                    //Gets the comment
                    String comment = command.substring(command.indexOf("\"") + 1, command.lastIndexOf("\""));

                    //Checks if the comment is empty
                    if (comment.equals("")) {
                        System.err.println("< Usage: comment <idPost> <comment>");
                        break;
                    }

                    winsome.addComment(idPost, comment);
                }

                case "wallet" -> {
                    if (arguments.length > 2 || (arguments.length == 2 && !arguments[1].equals("btc"))) {
                        System.err.println("Invallid command '" + command + "'. Use 'wallet' or 'wallet btc'");
                        break;
                    }

                    if (arguments.length == 2) { //wallet btc
                        winsome.getWalletInBitcoin();
                    } else { //wallet
                        winsome.getWallet();
                    }
                }

                case "exit" -> {
                    //Sets the termination flag to true
                    termination = true;

                    winsome.close();
                }

                default -> System.err.println("Unknown command: " + command);
            }
        }
    }

    /**
     * Parses the configuration file
     */
    private static void parseConfigFile() {
        try {
            BufferedReader fileReader = new BufferedReader(new FileReader(configurationFile));
            String line;

            while ((line = fileReader.readLine()) != null) {
                switch (line.split("=")[0]) {
                    case "REGISTRY-PORT" -> registryPort = Integer.parseInt(line.split("=")[1]);

                    case "REGISTRY-HOST" -> registryHost = line.split("=")[1];

                    case "RMI-REGISTER" -> registerServiceName = line.split("=")[1];

                    case "SERVER-IP" -> serverIP = line.split("=")[1];

                    case "TCP-PORT" -> tcpPort = Integer.parseInt(line.split("=")[1]);

                    case "BUFFER-SIZE" -> bufferSize = Integer.parseInt(line.split("=")[1]);

                    case "RMI-CALLBACK" -> callbackServiceName = line.split("=")[1];

                    default -> {
                        //Checks if the line is not empty and doesn't start with '#'
                        if (!line.equals("") && !line.startsWith("#")) {
                            System.err.println("Invalid option: " + line);
                            System.exit(1);
                        }
                    }
                }
            }

        } catch (IOException e) {
            System.err.println("Error while parsing the config file: " + e.getMessage());
            System.exit(1);
        }
    }
}
