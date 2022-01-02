import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class ClientMain {
    private static String configurationFile = "clientConfig.txt";

    private static int registryPort = 11111; //TODO Mettere una porta di default più significativa
    private static String registryHost = "localhost";
    private static String registerServiceName = "RMI-REGISTER";
    private static String callbackServiceName = "RMI-FOLLOWER-CALLBACK"; //TODO Aggiungere il parsing del file di config
    private static String serverIP = "localhost";
    private static int tcpPort = 2222;
    private static int bufferSize = 16 * 1024;
    private static SocketChannel socketChannel = null;
    private static ByteBuffer buffer;

    private static String currentLoggedUser = null;

    private static Thread multicastThread;

    private static List<String> followers;
    private static CallbackHandlerInterface serverCallbackHandler = null;
    private static NotifyNewFollower callbackStub = null;
    private static NotifyNewFollower followerCallback = null;

    public static void main(String []args) {
        if (args.length == 1) configurationFile = args[0];
        if (args.length > 1) {
            System.err.println("Usage: ClientMain [config file]");
            System.exit(1);
        }

        parseConfigFile();
        buffer = ByteBuffer.allocate(bufferSize);

        //RegisterInterface register = getRemoteRegisterObject();
        RegisterInterface register = null;
        try {
            Registry registry = LocateRegistry.getRegistry(registryHost, registryPort);
            register = (RegisterInterface) registry.lookup(registerServiceName);
            serverCallbackHandler = (CallbackHandlerInterface) registry.lookup(callbackServiceName);
        } catch (RemoteException e) {
            System.err.println("Error with the remote object: " + e.getMessage());
            System.exit(1);
        } catch (NotBoundException e) {
            System.err.println("Error while retrieving the remote object: " + e.getMessage());
            System.exit(1);
        }

        Scanner cliScanner = new Scanner(System.in);
        String command;
        boolean termination = false;

        while (!termination) {
            System.out.print("> ");
            command = cliScanner.nextLine();

            String []arguments = command.split(" ");
            switch (arguments[0]) {
                case "register" : {
                    if (socketChannel != null) {
                        System.err.println("There's a user already logged");
                        break;
                    }
                    registerUser(register, arguments);
                    break;
                }
                case "login" : {
                    socketChannel = loginUser(command);
                    break;
                }
                case "logout" : {
                    logout();
                    break;
                }
                case "list" : {
                    if (arguments.length != 2 || (!arguments[1].equals("users") && !arguments[1].equals("following") && !arguments[1].equals("followers"))) {
                        System.err.println("Invalid command '" + command + "'. Use 'list users', 'list followers' or 'list following'");
                        break;
                    }

                    if (arguments[1].equals("users")) { //list users
                        listUsers(command);
                        break;
                    } else if (arguments[1].equals("followers")) { //list followers
                        listFollowers();
                    } else { //list following
                        listFollowing(command);
                    }
                    break;
                }
                case "follow" : {
                    followUser(command);
                    break;
                }
                case "unfollow" : {
                    unfollowUser(command);
                    break;
                }
                case "blog" : {
                    viewBlog(command);
                    break;
                }
                case "post" : {
                    createPost(command);
                    break;
                }
                case "show" : {
                    if (arguments.length < 2 || (!arguments[1].equals("feed") && !arguments[1].equals("post"))) {
                        System.err.println("Invalid command '" + command + "'. Use 'show feed' or 'show post'");
                        break;
                    }

                    if (arguments[1].equals("feed")) { //show feed
                        showFeed(command);
                    } else { //show post
                        showPost(command);
                    }
                    break;
                }
                case "delete" : {
                    deletePost(command);
                    break;
                }
                case "rewin" : {
                    rewinPost(command);
                    break;
                }
                case "rate" : {
                    ratePost(command);
                    break;
                }
                case "comment" : {
                    addComment(command);
                    break;
                }
                case "wallet" : {
                    if (arguments.length > 2 || (arguments.length == 2 && !arguments[1].equals("btc"))) {
                        System.err.println("Invallid command '" + command + "'. Use 'wallet' or 'wallet btc'");
                        break;
                    }

                    if (arguments.length == 2) { //wallet btc
                        getWalletInBitcoin(command);
                    } else { //wallet
                        getWallet(command);
                    }
                    break;
                }
                case "exit" : { //TODO Scrivere nella relazione che si è voluto inserire questo comando per terminare il client
                    termination = true;
                    break;
                }
                default: System.err.println("Unknown command: " + command);
            }
        }
    }

    private static void ratePost(String command) {
        if (socketChannel == null || currentLoggedUser == null) {
            System.err.println("< There is no user logged in");
            return;
        }

        String request = command + " " + currentLoggedUser;
        try {
            sendRequest(request);

            buffer.clear();
            //Reads the response
            socketChannel.read(buffer);
            //Sets the buffer ready to be read
            buffer.flip();
            int responseId = buffer.getInt();

            if (responseId == 0) System.out.println("< Post rated correctly");
            if (responseId == 1) System.err.println("< There is no user logged in");
            if (responseId == 2) System.err.println("< Unknown command. Usage: rate idPost rate (rate must be '+1' or '-1'");
            if (responseId == 3) System.err.println("< Post doesn't exists");
            if (responseId == 4) System.err.println("< You can't rate your own post");
            if (responseId == 5) System.err.println("< You've already rated this post");

        } catch (IOException e) {
            System.err.println("Error while rating the post (" + e.getMessage() + ")");
        }
    }

    private static void followUser(String command) {
        if (socketChannel == null || currentLoggedUser == null) {
            System.err.println("< There is no user logged in");
            return;
        }

        String request = command + " " + currentLoggedUser;
        try {
            sendRequest(request);

            buffer.clear();
            //Reads the response
            socketChannel.read(buffer);
            //Sets the buffer ready to be read
            buffer.flip();
            int responseId = buffer.getInt();

            String userToFollow = command.split(" ")[1];
            if (responseId == 0) System.out.println("< " + userToFollow + " followed");
            if (responseId == 1) System.err.println("< There is no user logged in");
            if (responseId == 2) System.err.println("< User " + userToFollow + " doesn't exists");
            if (responseId == 3) System.err.println("< User " + userToFollow + " already followed");

        } catch (IOException e) {
            System.err.println("Error while following user (" + e.getMessage() + ")");
        }
    }

    private static void unfollowUser(String command) {
        if (socketChannel == null || currentLoggedUser == null) {
            System.err.println("< There is no user logged in");
            return;
        }

        String request = command + " " + currentLoggedUser;
        try {
            sendRequest(request);

            buffer.clear();
            //Reads the response
            socketChannel.read(buffer);
            //Sets the buffer ready to be read
            buffer.flip();
            int responseId = buffer.getInt();

            String userToUnfollow = command.split(" ")[1];
            if (responseId == 0) System.out.println("< " + userToUnfollow + " unfollowed");
            if (responseId == 1) System.err.println("< There is no user logged in");
            if (responseId == 2) System.err.println("< User " + userToUnfollow + " doesn't exists");
            if (responseId == 3) System.err.println("< User " + userToUnfollow + " already not followed");

        } catch (IOException e) {
            System.err.println("Error while unfollowing user (" + e.getMessage() + ")");
        }
    }

    private static void listFollowers() {
        if (socketChannel == null || currentLoggedUser == null) {
            System.err.println("< There is no user logged in");
            return;
        }

        System.out.println("< Followers:");
        synchronized (followers) {
            if (followers.isEmpty()) System.out.println("< There are no followers");

            for (String user : followers)
                System.out.println("< " + user);
        }
    }

    private static void sendRequest(String command) throws IOException{
        if (socketChannel == null) {
            System.err.println("No user is logged in");
            return;
        }

        buffer.clear();

        //Puts the command length
        buffer.putInt(command.getBytes().length);
        //Puts the command
        buffer.put(command.getBytes());

        //Sets the buffer ready for writing to the channel
        buffer.flip();
        //Writes to the channel
        socketChannel.write(buffer);
    }

    private static void logout() {
        if (currentLoggedUser == null) {
            System.err.println("< There are no user logged");
            return;
        }

        String request = "logout " + currentLoggedUser;
        try {
            sendRequest(request);

            buffer.clear();
            //Reads the response
            socketChannel.read(buffer);
            //Sets the buffer ready to be read
            buffer.flip();
            int responseId = buffer.getInt();

            if (responseId == 0) {
                System.out.println("< " + currentLoggedUser + " logged out");
                socketChannel.close();
                socketChannel = null;

                multicastThread.interrupt();

                followers = null;
                serverCallbackHandler.unregisterForCallback(currentLoggedUser, callbackStub);
                currentLoggedUser = null;
                UnicastRemoteObject.unexportObject(followerCallback, true);
                followerCallback = null;
            } else {
                System.err.println("< " + currentLoggedUser + " is not logged in");
            }

        } catch (IOException e) {
            System.err.println("Error during logout (" + e.getMessage() + ")");
        }
    }

    private static SocketChannel loginUser(String command) {
        if (command.split(" ").length != 3) {
            System.err.println("< Usage: login username password");
            return null;
        }

        //Checks if a user is already logged in
        if (socketChannel != null) {
            System.err.println("< There's a user already logged. He needs to logout");
            return socketChannel;
        }

        try {
            //Connects to the server
            socketChannel = SocketChannel.open(new InetSocketAddress(serverIP, tcpPort));

            //Sends request to the server
            sendRequest(command);

            buffer.clear();
            //Reads the response
            socketChannel.read(buffer);
            //Sets the buffer ready to be read
            buffer.flip();
            int responseId = buffer.getInt();

            if (responseId == 0) {
                System.out.println("< " + command.split(" ")[1] + " logged in");
                currentLoggedUser = command.split(" ")[1];

                int strLen = buffer.getInt();
                byte []strByte = new byte[strLen];
                buffer.get(strByte);
                String multicastReferences = new String(strByte);

                JsonObject jsonObject = JsonParser.parseString(multicastReferences).getAsJsonObject();
                String multicastIP = jsonObject.get("multicastIP").getAsString();
                int multicastPort = jsonObject.get("multicastPort").getAsInt();
                multicastThread = new Thread(new NotifyHandler(multicastIP, multicastPort));
                multicastThread.start();

                followers = new ArrayList<>();
                followerCallback = new NotifyNewFollowerService(followers);
                callbackStub = (NotifyNewFollower) UnicastRemoteObject.exportObject(followerCallback, 0);
                serverCallbackHandler.registerForCallback(currentLoggedUser, callbackStub);
                getFollowers(command.split(" ")[1]);
            }
            if (responseId == 1) System.err.println("< Username or password not correct");
            if (responseId == 2) System.err.println("< Already logged on another terminal");
            if (responseId == 1 || responseId == 2) {
                socketChannel.close();
                socketChannel = null;
            }

            //Returns the socketChannel
            return socketChannel;
        } catch (IOException e) {
            System.err.println("Error during login, please try again (" + e.getMessage() + ")");
            return null;
        }
    }

    private static void getFollowers(String username) {
        try {
            sendRequest("getFollowers " + username);

            buffer.clear();
            socketChannel.read(buffer);
            buffer.flip();

            buffer.getInt();
            int strLen = buffer.getInt();
            byte []strByte = new byte[strLen];
            buffer.get(strByte);
            String userFollowers = new String(strByte);

            if (userFollowers.equals("[]")) return;

            getUserFromJson(followers, userFollowers);

        } catch (IOException e) {
            System.err.println("Error while recovering followers (" + e.getMessage() + ")");
        }
    }

    private static void getUserFromJson(List<String> list, String jsonString) {
        JsonArray jsonArray = JsonParser.parseString(jsonString).getAsJsonArray();
        for (JsonElement jsonElement : jsonArray)
            list.add(jsonElement.getAsString());
    }

    private static void listUsers(String command) {
        if (socketChannel == null || currentLoggedUser == null) {
            System.err.println("< There is no user logged");
            return;
        }

        try {
            sendRequest(command + " " + currentLoggedUser);

            buffer.clear();
            socketChannel.read(buffer);
            buffer.flip();

            int responseId = buffer.getInt();
            if (responseId == -1) {
                System.err.println("< Invalid command. Usage: list users");
                return;
            }

            if (responseId == 0) {
                int resultLen = buffer.getInt();
                byte []resultByte = new byte[resultLen];
                buffer.get(resultByte);
                String result = new String(resultByte);

                System.out.printf("< %10s%10s%10s\n", "Users", "|", "Tags");
                System.out.println("< ----------------------------------------");

                JsonArray jsonArray = JsonParser.parseString(result).getAsJsonArray();
                for (JsonElement jsonElement : jsonArray) {
                    JsonObject jsonEntry = JsonParser.parseString(jsonElement.toString()).getAsJsonObject();
                    String username = jsonEntry.get("username").getAsString();

                    JsonArray jsonTags = jsonEntry.get("tags").getAsJsonArray();
                    String []tags = new String[jsonTags.size()];
                    for (int i = 0; i < tags.length; i++) {
                        tags[i] = jsonTags.get(i).getAsString();
                    }

                    System.out.printf("< %10s%10s%22s\n", username, "|", Arrays.toString(tags));
                }

            } else {
                System.err.println("There is no user logged in");
            }
        } catch (IOException e) {
            System.err.println("Error during communication with server (" + e.getMessage() + ")");
        }
    }

    private static void createPost(String command) {
        if (socketChannel == null || currentLoggedUser == null) {
            System.err.println("< There is no user logged");
            return;
        }

        String request = command + " " + currentLoggedUser;
        try {
            sendRequest(request);

            buffer.clear();
            socketChannel.read(buffer);
            buffer.flip();

            int responseId = buffer.getInt();
            if (responseId == 0) System.out.println("< Post created correctly");
            if (responseId == 1) System.err.println("< There is no user logged in");
            if (responseId == 2) System.err.println("< Invalid post arguments. Title must have less then 20 characters, the content mush have less then 500 characters");

        } catch (IOException e) {
            System.err.println("< Error during comunication with server (" + e.getMessage() + ")");
        }
    }

    private static void deletePost(String command) {
        if (socketChannel == null || currentLoggedUser == null) {
            System.err.println("< There is no user logged");
            return;
        }

        try {
            String request = command + " " + currentLoggedUser;
            sendRequest(request);

            buffer.clear();
            socketChannel.read(buffer);
            buffer.flip();

            int responseId = buffer.getInt();
            if (responseId == 0) System.out.println("< Post deleted correctly");
            if (responseId == 1) System.err.println("< There is no user logged");
            if (responseId == 2) System.err.println("< This post doesn't exists");
            if (responseId == 3) System.err.println("< You don't own this post");
        } catch (IOException e) {
            System.err.println("< Error during comunication with server (" + e.getMessage() + ")");
        }
    }

    private static void listFollowing(String command) {
        if (socketChannel == null || currentLoggedUser == null) {
            System.err.println("< There is no user logged");
            return;
        }

        try {
            String request = command + " " + currentLoggedUser;
            sendRequest(request);

            buffer.clear();
            socketChannel.read(buffer);
            buffer.flip();

            int responseId = buffer.getInt();
            if (responseId == 0) {
                int strLen = buffer.getInt();
                byte []strByte = new byte[strLen];
                buffer.get(strByte);
                String userFollowing = new String(strByte);

                System.out.printf("< %10s%10s%10s\n", "Users", "|", "Tags");
                System.out.println("< ----------------------------------------");

                JsonArray jsonArray = JsonParser.parseString(userFollowing).getAsJsonArray();
                for (JsonElement jsonElement : jsonArray) {
                    JsonObject jsonEntry = JsonParser.parseString(jsonElement.toString()).getAsJsonObject();
                    String username = jsonEntry.get("username").getAsString();

                    JsonArray jsonTags = jsonEntry.get("tags").getAsJsonArray();
                    String []tags = new String[jsonTags.size()];
                    for (int i = 0; i < tags.length; i++) {
                        tags[i] = jsonTags.get(i).getAsString();
                    }

                    System.out.printf("< %10s%10s%22s\n", username, "|", Arrays.toString(tags));
                }
            }
            if (responseId == 1) System.err.println("< There is no user logged");
        } catch (IOException e) {
            System.err.println("< Error during comunication with server (" + e.getMessage() + ")");
        }
    }

    private static void viewBlog(String command) {
        if (socketChannel == null || currentLoggedUser == null) {
            System.err.println("< There is no user logged");
            return;
        }

        try {
            String request = command + " " + currentLoggedUser;
            sendRequest(request);

            buffer.clear();
            socketChannel.read(buffer);
            buffer.flip();

            int responseId = buffer.getInt();
            if (responseId == 0) {
                int strLen = buffer.getInt();
                byte []strByte = new byte[strLen];
                buffer.get(strByte);
                String blogPosts = new String(strByte);

                System.out.printf("< %-4s %s %-14s %s %10s\n", "Id", "|", "Author", "|", "Title");
                System.out.println("< ---------------------------------------------------------");

                JsonArray jsonArray = JsonParser.parseString(blogPosts).getAsJsonArray();
                for (JsonElement jsonElement : jsonArray) {
                    JsonObject jsonEntry = JsonParser.parseString(jsonElement.toString()).getAsJsonObject();
                    int idPost = jsonEntry.get("idPost").getAsInt();
                    String author = jsonEntry.get("author").getAsString();
                    String postTitle = jsonEntry.get("postTitle").getAsString();

                    System.out.printf("< %-4d %s %-14s %s %10s\n", idPost, "|", author, "|", postTitle);
                }
            }
            if (responseId == 1) System.err.println("< There is no user logged");
        } catch (IOException e) {
            System.err.println("< Error during comunication with server (" + e.getMessage() + ")");
        }
    }

    private static void showPost(String command) {
        if (socketChannel == null || currentLoggedUser == null) {
            System.err.println("< There is no user logged");
            return;
        }

        try {
            String request = command + " " + currentLoggedUser;
            sendRequest(request);

            buffer.clear();
            socketChannel.read(buffer);
            buffer.flip();

            int responseId = buffer.getInt();
            if (responseId == -1) System.err.println("< Invalid command");
            if (responseId == 0) {
                int strLen = buffer.getInt();
                byte []strByte = new byte[strLen];
                buffer.get(strByte);
                String post = new String(strByte);

                JsonObject jsonObject = JsonParser.parseString(post).getAsJsonObject();
                String postTitle = jsonObject.get("postTitle").getAsString();
                String postContent = jsonObject.get("postContent").getAsString();
                int upvotes = jsonObject.get("upvotes").getAsInt();
                int downvotes = jsonObject.get("downvotes").getAsInt();
                JsonArray comments = jsonObject.get("comments").getAsJsonArray();

                System.out.println("< Title: " + postTitle);
                System.out.println("< Content: " + postContent);
                System.out.println("< Votes: " + upvotes + " upovotes, " + downvotes + " downvotes");
                System.out.print("< Comments: ");
                if (comments.size() == 0)
                    System.out.println(comments.size());
                else {
                    System.out.println();
                    for (JsonElement comment : comments) {
                        JsonObject jsonEntry = JsonParser.parseString(comment.toString()).getAsJsonObject();
                        String author = jsonEntry.get("author").getAsString();
                        String commentContent = jsonEntry.get("content").getAsString();
                        System.out.println("<\t" + author + ": \"" + commentContent + "\"");
                    }
                }
            }
            if (responseId == 1) System.err.println("< There is no user logged");
            if (responseId == 2) System.err.println("< This post doesn't exists");
        } catch (IOException e) {
            System.err.println("< Error during comunication with server (" + e.getMessage() + ")");
        }
    }

    private static void showFeed(String command) {
        if (socketChannel == null || currentLoggedUser == null) {
            System.err.println("< There is no user logged");
            return;
        }

        try {
            String request = command + " " + currentLoggedUser;
            sendRequest(request);

            buffer.clear();
            socketChannel.read(buffer);
            buffer.flip();

            int responseId = buffer.getInt();
            if (responseId == 0) {
                int strLen = buffer.getInt();
                byte []strByte = new byte[strLen];
                buffer.get(strByte);
                String blogPosts = new String(strByte);

                System.out.printf("< %-4s %s %-14s %s %10s\n", "Id", "|", "Author", "|", "Title");
                System.out.println("< ---------------------------------------------------------");

                JsonArray jsonArray = JsonParser.parseString(blogPosts).getAsJsonArray();
                for (JsonElement jsonElement : jsonArray) {
                    JsonObject jsonEntry = JsonParser.parseString(jsonElement.toString()).getAsJsonObject();
                    int idPost = jsonEntry.get("idPost").getAsInt();
                    String author = jsonEntry.get("author").getAsString();
                    String postTitle = jsonEntry.get("postTitle").getAsString();

                    System.out.printf("< %-4d %s %-14s %s %10s\n", idPost, "|", author, "|", postTitle);
                }
            }
            if (responseId == 1) System.err.println("< There is no user logged");
        } catch (IOException e) {
            System.err.println("< Error during comunication with server (" + e.getMessage() + ")");
        }
    }

    private static void rewinPost(String command) {
        if (socketChannel == null || currentLoggedUser == null) {
            System.err.println("< There is no user logged");
            return;
        }

        try {
            String request = command + " " + currentLoggedUser;
            sendRequest(request);

            buffer.clear();
            socketChannel.read(buffer);
            buffer.flip();

            int responseId = buffer.getInt();
            if (responseId == 0) System.out.println("< Post rewinned correctly");
            if (responseId == 1) System.err.println("< There is no user logged");
            if (responseId == 2) System.err.println("< This post doesn't exists");
            if (responseId == 3) System.err.println("< You've already rewinned this post");
        } catch (IOException e) {
            System.err.println("< Error during comunication with server (" + e.getMessage() + ")");
        }
    }

    private static void addComment(String command) {
        if (socketChannel == null || currentLoggedUser == null) {
            System.err.println("< There is no user logged");
            return;
        }

        try {
            String request = command + " " + currentLoggedUser;
            sendRequest(request);

            buffer.clear();
            socketChannel.read(buffer);
            buffer.flip();

            int responseId = buffer.getInt();
            if (responseId == 0) System.out.println("< Comment added correctly");
            if (responseId == 1) System.err.println("< There is no user logged");
            if (responseId == 2) System.err.println("< Comment can't be empty");
            if (responseId == 3) System.err.println("< This post doesn't exists");
            if (responseId == 4) System.err.println("< This post isn't in your feed");
            if (responseId == 5) System.err.println("< You can't comment your own post");
        } catch (IOException e) {
            System.err.println("< Error during comunication with server (" + e.getMessage() + ")");
        }
    }

    private static void getWallet(String command) {
        if (socketChannel == null || currentLoggedUser == null) {
            System.err.println("< There is no user logged");
            return;
        }

        try {
            String request = command + " " + currentLoggedUser;
            sendRequest(request);

            buffer.clear();
            socketChannel.read(buffer);
            buffer.flip();

            int responseId = buffer.getInt();
            if (responseId == -1) System.err.println("< Invalid command. Use 'wallet' or 'wallet btc'");
            if (responseId == 0) {
                int strLen = buffer.getInt();
                byte []strByte = new byte[strLen];
                buffer.get(strByte);
                String wallet = new String(strByte);

                JsonObject jsonObject = JsonParser.parseString(wallet).getAsJsonObject();
                double wincoin = jsonObject.get("wincoin").getAsDouble();
                JsonArray transactions = jsonObject.get("transactions").getAsJsonArray();

                System.out.println("< Wincoin: " + wincoin);
                System.out.print("< Transactions: ");
                if (transactions.size() == 0)
                    System.out.println(transactions.size());
                else {
                    System.out.println();
                    for (JsonElement transaction : transactions) {
                        JsonObject jsonEntry = JsonParser.parseString(transaction.toString()).getAsJsonObject();
                        String action = jsonEntry.get("action").getAsString();
                        String timestamp = jsonEntry.get("timestamp").getAsString();
                        System.out.println("<\t" + action + " " + timestamp);
                    }
                }
            }
            if (responseId == 1) System.err.println("< There is no user logged");

        } catch (IOException e) {
            System.err.println("< Error during comunication with server (" + e.getMessage() + ")");
        }
    }

    private static void getWalletInBitcoin(String command) {
        if (socketChannel == null || currentLoggedUser == null) {
            System.err.println("< There is no user logged");
            return;
        }

        try {
            String request = command + " " + currentLoggedUser;
            sendRequest(request);

            buffer.clear();
            socketChannel.read(buffer);
            buffer.flip();

            int responseId = buffer.getInt();
            if (responseId == -1) System.err.println("< Invalid command. Use 'wallet' or 'wallet btc'");
            if (responseId == 0) {
                int strLen = buffer.getInt();
                byte []strByte = new byte[strLen];
                buffer.get(strByte);
                String wallet = new String(strByte);

                JsonObject jsonObject = JsonParser.parseString(wallet).getAsJsonObject();
                double btc = jsonObject.get("wincoinBTC").getAsDouble();
                JsonArray transactions = jsonObject.get("transactions").getAsJsonArray();

                System.out.println("< BTC: " + btc);
                System.out.print("< Transactions: ");
                if (transactions.size() == 0)
                    System.out.println(transactions.size());
                else {
                    System.out.println();
                    for (JsonElement transaction : transactions) {
                        JsonObject jsonEntry = JsonParser.parseString(transaction.toString()).getAsJsonObject();
                        String action = jsonEntry.get("action").getAsString();
                        String timestamp = jsonEntry.get("timestamp").getAsString();
                        System.out.println("<\t" + action + " " + timestamp);
                    }
                }
            }
            if (responseId == 1) System.err.println("< There is no user logged");

        } catch (IOException e) {
            System.err.println("< Error during comunication with server (" + e.getMessage() + ")");
        }
    }

    //Registers a new user to WINSOME
    private static void registerUser(RegisterInterface register, String[] arguments) {
        if (arguments.length > 8) {
            System.err.println("Usage: register username password tags (max 5 tag)");
            return;
        }

        List<String> tag = new ArrayList<>(Arrays.asList(arguments).subList(3, arguments.length));

        try {
            int result;
            if ((result = register.register(arguments[1], arguments[2], tag)) != 0) {
                if (result == -1) System.err.println("< Error server side");
                if (result == 1) System.err.println("< Password field is empty");
                if (result == 2 || result == 3) System.err.println("< Registration requires minimum 1 tag and maximum 5");
                if (result == 4) System.err.println("< User '" + arguments[1] + "' already registered");
                return;
            }
            System.out.println("< " + arguments[1] + " registered correctly");
        } catch (RemoteException e) {
            System.err.println("Error while registering new user: " + e.getMessage());
        }
    }

    //Retrieving the remote object for the registration process
    /*private static RegisterInterface getRemoteRegisterObject() {
        try {
            Registry registry = LocateRegistry.getRegistry(registryHost, registryPort);
            return (RegisterInterface) registry.lookup(registerServiceName);
        } catch (RemoteException e) {
            System.err.println("Error with the remote object: " + e.getMessage());
            System.exit(1);
        } catch (NotBoundException e) {
            System.err.println("Error while retrieving the remote object: " + e.getMessage());
            System.exit(1);
        }

        return null;
    }*/

    //Parsing del file di configurazione del client
    private static void parseConfigFile() {
        try {
            BufferedReader fileReader = new BufferedReader(new FileReader(configurationFile));
            String line;

            while ((line = fileReader.readLine()) != null) {
                switch (line.split("=")[0]) {
                    case "REGISTRY-PORT" -> registryPort = Integer.parseInt(line.split("=")[1]);

                    case "REGISTRY-HOST" -> registryHost = line.split("=")[1];

                    case "RMI-SERVICE" -> registerServiceName = line.split("=")[1];

                    case "SERVER-IP" -> serverIP = line.split("=")[1];

                    case "TCP-PORT" -> tcpPort = Integer.parseInt(line.split("=")[1]);
                    //TODO Aggiungere ulteriori case
                }
            }

        } catch (IOException e) {
            System.err.println("Error while parsing the config file: " + e.getMessage());
            System.exit(1);
        }
    }
}
