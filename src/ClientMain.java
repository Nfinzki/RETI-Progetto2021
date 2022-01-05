import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class ClientMain {
    private static String configurationFile = "clientConfig.txt";

    private static int registryPort = 11111; //TODO Mettere una porta di default più significativa
    private static String registryHost = "localhost";
    private static String registerServiceName = "RMI-REGISTER";
    private static String callbackServiceName = "RMI-FOLLOWER-CALLBACK";
    private static String serverIP = "localhost";
    private static int tcpPort = 2222;
    private static int bufferSize = 16 * 1024;

    public static void main(String []args) {
        if (args.length == 1) configurationFile = args[0];
        if (args.length > 1) {
            System.err.println("Usage: ClientMain [config file]");
            System.exit(1);
        }

        parseConfigFile();

        Winsome winsome = null;
        try {
            winsome = new Winsome(serverIP, tcpPort, registryHost, registryPort, registerServiceName, callbackServiceName, bufferSize);
        } catch (Exception ignored){} //TODO Migliorare qui

        Scanner cliScanner = new Scanner(System.in);
        String command;
        boolean termination = false;

        while (!termination) {
            System.out.print("> ");
            command = cliScanner.nextLine();

            String []arguments = command.split(" ");
            switch (arguments[0]) {
                case "register" -> {
                    if (arguments.length > 8) {
                        System.err.println("< Usage: register username password tags (max 5 tag)");
                        return;
                    }

                    List<String> tags = new ArrayList<>(Arrays.asList(arguments).subList(3, arguments.length));
                    assert winsome != null;
                    winsome.register(arguments[1], arguments[2], tags);
                }

                case "login" -> {
                    if (command.split(" ").length != 3) {
                        System.err.println("< Usage: login username password");
                    }

                    assert winsome != null;
                    winsome.login(arguments[1], arguments[2]);
                }

                case "logout" -> {
                    assert winsome != null;
                    winsome.logout();
                }

                case "list" -> {
                    if (arguments.length != 2 || (!arguments[1].equals("users") && !arguments[1].equals("following") && !arguments[1].equals("followers"))) {
                        System.err.println("< Invalid command '" + command + "'. Use 'list users', 'list followers' or 'list following'");
                        break;
                    }

                    if (arguments[1].equals("users")) { //list users
                        assert winsome != null;
                        winsome.listUsers();
                    } else if (arguments[1].equals("followers")) { //list followers
                        assert winsome != null;
                        winsome.listFollowers();
                    } else { //list following
                        assert winsome != null;
                        winsome.listFollowing();
                    }
                }

                case "follow" -> {
                    if (arguments.length != 2) {
                        System.err.println("< Usage: follow <idUser>");
                        break;
                    }

                    assert winsome != null;
                    winsome.followUser(arguments[1]);
                }

                case "unfollow" -> {
                    if (arguments.length != 2) {
                        System.err.println("< Usage: unfollow <idUser>");
                        break;
                    }

                    assert winsome != null;
                    winsome.unfollowUser(arguments[1]);
                }

                case "blog" -> {
                    if (arguments.length != 1) {
                        System.err.println("< Usage: blog");
                        break;
                    }

                    assert winsome != null;
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

                    if (title.equals("") || content.equals("")) {
                        System.err.println("< Usage: post \"Title\" \"Content\"");
                        break;
                    }

                    assert winsome != null;
                    winsome.createPost(title, content);
                }

                case "show" -> {
                    if (arguments.length < 2 || (!arguments[1].equals("feed") && !arguments[1].equals("post"))) {
                        System.err.println("Invalid command '" + command + "'. Use 'show feed' or 'show post'");
                        break;
                    }

                    if (arguments[1].equals("feed")) { //show feed
                        assert winsome != null;
                        winsome.showFeed();
                    } else { //show post
                        if (arguments.length != 3) {
                            System.err.println("< Usage: show post <idPost>");
                            break;
                        }

                        assert winsome != null;
                        winsome.showPost(arguments[2]);
                    }
                }

                case "delete" -> {
                    if (arguments.length != 2) {
                        System.err.println("< Usage: delete <idPost>");
                        break;
                    }

                    assert winsome != null;
                    winsome.deletePost(arguments[1]);
                }

                case "rewin" -> {
                    if (arguments.length != 2) {
                        System.err.println("< Usage: rewin <idPost>");
                        break;
                    }

                    assert winsome != null;
                    winsome.rewinPost(arguments[1]);
                }

                case "rate" -> {
                    if (arguments.length != 3) {
                        System.err.println("< Usage: rate <idPost> <vote>");
                        break;
                    }

                    assert winsome != null;
                    winsome.ratePost(arguments[1], arguments[2]);
                }

                case "comment" -> {
                    String idPost = command.split(" ")[1];
                    String comment = command.substring(command.indexOf("\"") + 1, command.lastIndexOf("\""));

                    if (comment.equals("")) {
                        System.err.println("< Usage: comment <idPost> <comment>");
                        break;
                    }

                    assert winsome != null;
                    winsome.addComment(idPost, comment);
                }

                case "wallet" -> {
                    if (arguments.length > 2 || (arguments.length == 2 && !arguments[1].equals("btc"))) {
                        System.err.println("Invallid command '" + command + "'. Use 'wallet' or 'wallet btc'");
                        break;
                    }

                    assert winsome != null;
                    if (arguments.length == 2) { //wallet btc
                        winsome.getWalletInBitcoin();
                    } else { //wallet
                        winsome.getWallet();
                    }
                }

                case "exit" -> { //TODO Scrivere nella relazione che si è voluto inserire questo comando per terminare il client
                    termination = true;

                    assert winsome != null;
                    winsome.close();
                }

                default -> System.err.println("Unknown command: " + command);
            }
        }
    }

    //Parsing del file di configurazione del client
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
