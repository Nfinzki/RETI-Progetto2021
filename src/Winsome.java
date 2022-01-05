import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.rmi.NoSuchObjectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Winsome {
    private final String serverIP;
    private final int tcpPort;

    private SocketChannel socketChannel = null;
    private String currentLoggedUser = null;
    private final ByteBuffer buffer;

    private List<String> followers;

    private MulticastSocket multicastSocket;
    private Thread multicastThread;

    private final CallbackHandlerInterface serverCallbackHandler;
    private NotifyNewFollower callbackStub = null;
    private NotifyNewFollower followerCallback = null;
    private final RegisterInterface register;

    public Winsome(String serverIP, int tcpPort, String registryHost, int registryPort, String registerServiceName, String callbackServiceName, int bufferSize) throws RemoteException, NotBoundException {
        this.serverIP = serverIP;
        this.tcpPort = tcpPort;
        buffer = ByteBuffer.allocate(bufferSize);

        Registry registry = LocateRegistry.getRegistry(registryHost, registryPort);
        register = (RegisterInterface) registry.lookup(registerServiceName);
        serverCallbackHandler = (CallbackHandlerInterface) registry.lookup(callbackServiceName);
    }

    public void ratePost(String idPost, String vote) {
        if (socketChannel == null || currentLoggedUser == null) {
            System.err.println("< There is no user logged in");
            return;
        }

        String request = "rate " + idPost + " " + vote + " " + currentLoggedUser;
        try {
            sendRequest(request);
            readResponse();
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

    public void readResponse() throws IOException {
        buffer.clear();
        //Reads the response
        socketChannel.read(buffer);
        //Sets the buffer ready to be read
        buffer.flip();
    }

    public void followUser(String idUser) {
        if (socketChannel == null || currentLoggedUser == null) {
            System.err.println("< There is no user logged in");
            return;
        }

        String request = "follow " + idUser + " " + currentLoggedUser;
        try {
            sendRequest(request);
            readResponse();
            int responseId = buffer.getInt();

            if (responseId == 0) System.out.println("< " + idUser + " followed");
            if (responseId == 1) System.err.println("< There is no user logged in");
            if (responseId == 2) System.err.println("< User " + idUser + " doesn't exists");
            if (responseId == 3) System.err.println("< User " + idUser + " already followed");

        } catch (IOException e) {
            System.err.println("Error while following user (" + e.getMessage() + ")");
        }
    }

    public void unfollowUser(String idUser) {
        if (socketChannel == null || currentLoggedUser == null) {
            System.err.println("< There is no user logged in");
            return;
        }

        String request = "unfollow " + idUser + " " + currentLoggedUser;
        try {
            sendRequest(request);
            readResponse();
            int responseId = buffer.getInt();

            if (responseId == 0) System.out.println("< " + idUser + " unfollowed");
            if (responseId == 1) System.err.println("< There is no user logged in");
            if (responseId == 2) System.err.println("< User " + idUser + " doesn't exists");
            if (responseId == 3) System.err.println("< User " + idUser + " already not followed");

        } catch (IOException e) {
            System.err.println("Error while unfollowing user (" + e.getMessage() + ")");
        }
    }

    public void listFollowers() {
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

    public void sendRequest(String command) throws IOException{
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

    public void logout() {
        if (currentLoggedUser == null) {
            System.err.println("< There are no user logged");
            return;
        }

        String request = "logout " + currentLoggedUser;
        try {
            sendRequest(request);
            readResponse();
            int responseId = buffer.getInt();

            if (responseId == 0) {
                System.out.println("< " + currentLoggedUser + " logged out");
                socketChannel.close();
                socketChannel = null;

                multicastThread.interrupt();
                multicastSocket.close();

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

    public void login(String username, String password) {
        //Checks if a user is already logged in
        if (socketChannel != null) {
            System.err.println("< There's a user already logged. He needs to logout");
        }

        try {
            //Connects to the server
            socketChannel = SocketChannel.open(new InetSocketAddress(serverIP, tcpPort));

            //Sends request to the server
            sendRequest("login " + username + " " + password);
            readResponse();
            int responseId = buffer.getInt();

            if (responseId == 0) {
                System.out.println("< " + username + " logged in");
                currentLoggedUser = username;

                int strLen = buffer.getInt();
                byte []strByte = new byte[strLen];
                buffer.get(strByte);
                String multicastReferences = new String(strByte);

                JsonObject jsonObject = JsonParser.parseString(multicastReferences).getAsJsonObject();
                String multicastIP = jsonObject.get("multicastIP").getAsString();
                int multicastPort = jsonObject.get("multicastPort").getAsInt();

                multicastSocket = new MulticastSocket(multicastPort);
                multicastThread = new Thread(new NotifyHandler(multicastIP, multicastSocket));
                multicastThread.start();

                followers = new ArrayList<>();
                followerCallback = new NotifyNewFollowerService(followers);
                callbackStub = (NotifyNewFollower) UnicastRemoteObject.exportObject(followerCallback, 0);
                serverCallbackHandler.registerForCallback(currentLoggedUser, callbackStub);
                getFollowers(username);
            }
            if (responseId == 1) System.err.println("< Username or password not correct");
            if (responseId == 2) System.err.println("< Already logged on another terminal");
            if (responseId == 1 || responseId == 2) {
                socketChannel.close();
                socketChannel = null;
            }
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("Error during login, please try again (" + e.getMessage() + ")");
        }
    }

    public void getFollowers(String username) {
        try {
            sendRequest("getFollowers " + username);
            readResponse();

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

    public void getUserFromJson(List<String> list, String jsonString) {
        JsonArray jsonArray = JsonParser.parseString(jsonString).getAsJsonArray();
        for (JsonElement jsonElement : jsonArray)
            list.add(jsonElement.getAsString());
    }

    public void listUsers() {
        if (socketChannel == null || currentLoggedUser == null) {
            System.err.println("< There is no user logged");
            return;
        }

        try {
            sendRequest( "list users " + currentLoggedUser);
            readResponse();

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

    public void createPost(String title, String content) {
        if (socketChannel == null || currentLoggedUser == null) {
            System.err.println("< There is no user logged");
            return;
        }

        String request = "post /" + title + "/" + content + "/" + currentLoggedUser;
        System.out.println(request);
        try {
            sendRequest(request);
            readResponse();

            int responseId = buffer.getInt();
            System.out.println(responseId);
            if (responseId == 0) System.out.println("< Post created correctly");
            if (responseId == 1) System.err.println("< There is no user logged in");
            if (responseId == 2) System.err.println("< Invalid post arguments. Title must have less then 20 characters, the content mush have less then 500 characters");

        } catch (IOException e) {
            System.err.println("< Error during comunication with server (" + e.getMessage() + ")");
        }
    }

    public void deletePost(String idPost) {
        if (socketChannel == null || currentLoggedUser == null) {
            System.err.println("< There is no user logged");
            return;
        }

        try {
            String request = "delete " + idPost + " " + currentLoggedUser;
            sendRequest(request);
            readResponse();

            int responseId = buffer.getInt();
            if (responseId == 0) System.out.println("< Post deleted correctly");
            if (responseId == 1) System.err.println("< There is no user logged");
            if (responseId == 2) System.err.println("< This post doesn't exists");
            if (responseId == 3) System.err.println("< You don't own this post");
        } catch (IOException e) {
            System.err.println("< Error during comunication with server (" + e.getMessage() + ")");
        }
    }

    public void listFollowing() {
        if (socketChannel == null || currentLoggedUser == null) {
            System.err.println("< There is no user logged");
            return;
        }

        try {
            String request = "list following " + currentLoggedUser;
            sendRequest(request);
            readResponse();

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

    public void viewBlog() {
        if (socketChannel == null || currentLoggedUser == null) {
            System.err.println("< There is no user logged");
            return;
        }

        try {
            String request = "blog " + currentLoggedUser;
            sendRequest(request);
            readResponse();

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

    public void showPost(String idPost) {
        if (socketChannel == null || currentLoggedUser == null) {
            System.err.println("< There is no user logged");
            return;
        }

        try {
            String request = "show post " + idPost + " " + currentLoggedUser;
            System.out.println(request);
            sendRequest(request);
            readResponse();

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

    public void showFeed() {
        if (socketChannel == null || currentLoggedUser == null) {
            System.err.println("< There is no user logged");
            return;
        }

        try {
            String request = "show feed " + currentLoggedUser;
            sendRequest(request);
            readResponse();

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

    public void rewinPost(String idPost) {
        if (socketChannel == null || currentLoggedUser == null) {
            System.err.println("< There is no user logged");
            return;
        }

        try {
            String request = "rewin " + idPost + " " + currentLoggedUser;
            sendRequest(request);
            readResponse();

            int responseId = buffer.getInt();
            if (responseId == 0) System.out.println("< Post rewinned correctly");
            if (responseId == 1) System.err.println("< There is no user logged");
            if (responseId == 2) System.err.println("< This post doesn't exists");
            if (responseId == 3) System.err.println("< You've already rewinned this post");
        } catch (IOException e) {
            System.err.println("< Error during comunication with server (" + e.getMessage() + ")");
        }
    }

    public void addComment(String idPost, String comment) {
        if (socketChannel == null || currentLoggedUser == null) {
            System.err.println("< There is no user logged");
            return;
        }

        try {
            String request = "comment /" + idPost + "/" + comment + "/" + currentLoggedUser;
            sendRequest(request);
            readResponse();
            System.out.println(request);

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

    public void getWallet() {
        if (socketChannel == null || currentLoggedUser == null) {
            System.err.println("< There is no user logged");
            return;
        }

        try {
            String request = "wallet " + currentLoggedUser;
            sendRequest(request);
            readResponse();

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

    public void getWalletInBitcoin() {
        if (socketChannel == null || currentLoggedUser == null) {
            System.err.println("< There is no user logged");
            return;
        }

        try {
            String request = "wallet btc " + currentLoggedUser;
            sendRequest(request);
            readResponse();

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
    public void register(String username, String password, List<String> tags) {
        if (socketChannel != null || currentLoggedUser != null) {
            System.err.println("< Please logout before registering another user");
            return;
        }

        try {
            int result;
            if ((result = register.register(username, password, tags)) != 0) {
                if (result == -1) System.err.println("< Error server side");
                if (result == 1) System.err.println("< Password field is empty");
                if (result == 2 || result == 3) System.err.println("< Registration requires minimum 1 tag and maximum 5");
                if (result == 4) System.err.println("< User '" + username + "' already registered");
                return;
            }
            System.out.println("< " + username + " registered correctly");
        } catch (RemoteException e) {
            System.err.println("Error while registering new user: " + e.getMessage());
        }
    }

    public void close() {
        multicastThread.interrupt();
        multicastSocket.close();
        try {
            UnicastRemoteObject.unexportObject(followerCallback, true);
        } catch (NoSuchObjectException ignored) {}
    }
}
