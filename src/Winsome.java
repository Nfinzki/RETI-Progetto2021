/**
 * This class implements the Winsome API
 */

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

    /**
     * Writes the bytes in the buffer to the channel
     */
    private void writeToChannel() throws IOException {
        //Sets the buffer ready for writing to the channel
        buffer.flip();

        //Writes to the channel
        while(buffer.hasRemaining()) socketChannel.write(buffer);
    }

    /**
     * Sends the request to the server
     * @param command request to send to the server
     */
    private void sendRequest(String command) throws IOException{
        if (socketChannel == null) {
            System.out.println("< No user is logged in");
            return;
        }

        //Clears the buffer
        buffer.clear();

        //Converts the request to bytes
        byte []requestBytes = command.getBytes();

        //Puts the command length in the buffer
        buffer.putInt(requestBytes.length);

        //Calculates the buffer remaining capacity
        int bufferCapacity = buffer.capacity() - buffer.position();

        if (requestBytes.length <= bufferCapacity) { //The request fits all in the buffer
            //Puts the command
            buffer.put(requestBytes);

            writeToChannel();
        } else { //The request needs to be sent fragmented
            int startingIndex = 0;

            while (startingIndex < requestBytes.length) {
                //Writes the maximum bytes that fits in the buffer
                buffer.put(requestBytes, startingIndex, bufferCapacity);

                writeToChannel();
                //Clears the buffer
                buffer.clear();

                //Calculates the next start index to write
                startingIndex += bufferCapacity;

                //Calculates how many bytes have to write
                if (bufferCapacity < requestBytes.length - startingIndex)
                    //Needs to fill the whole buffer
                    bufferCapacity = buffer.capacity();
                else //Needs to write only a part of the buffer
                    bufferCapacity = requestBytes.length - startingIndex;
            }

        }
    }

    /**
     * Reads the response from the server
     */
    private void readResponse() throws IOException {
        buffer.clear();
        //Reads the response
        socketChannel.read(buffer);
        //Sets the buffer ready to be read
        buffer.flip();
    }

    /**
     * Reads the complete response from the server
     * @return the string containing the response
     */
    private String extractResponse() throws IOException {
        //Reads the response length
        int strLen = buffer.getInt();
        int totalRead = 0;
        StringBuilder response = new StringBuilder();

        //Until the response is entirely read
        while (totalRead < strLen) {
            byte[] strByte = new byte[buffer.limit() - buffer.position()];
            //Puts the bytes from the buffer to the array
            buffer.get(strByte);
            //Appends the response
            response.append(new String(strByte));

            //Updates the number of bytes read
            totalRead += strByte.length;

            if (totalRead == strLen) break;

            //Refills the buffer reading from the channel
            readResponse();
        }
        return response.toString();
    }

    /**
     * Adds a vote to a post
     * @param idPost post id of the post to rate
     * @param vote vote (must be "+1" or "-1")
     */
    public void ratePost(String idPost, String vote) {
        if (socketChannel == null || currentLoggedUser == null) {
            System.err.println("< There is no user logged in");
            return;
        }

        //Builds the request
        String request = "rate " + idPost + " " + vote + " " + currentLoggedUser;
        try {
            sendRequest(request);
            readResponse();
            //Gets the response code
            int responseId = buffer.getInt();

            if (responseId == 0) System.out.println("< Post rated correctly");
            if (responseId == 1) System.err.println("< There is no user logged in");
            if (responseId == -1 || responseId == 2) System.err.println("< Unknown command. Usage: rate idPost rate (rate must be '+1' or '-1')");
            if (responseId == 3) System.err.println("< Post doesn't exists");
            if (responseId == 4) System.err.println("< You can't rate your own post");
            if (responseId == 5) System.err.println("< You've already rated this post");

        } catch (IOException e) {
            System.err.println("Error while rating the post (" + e.getMessage() + ")");
        }
    }

    /**
     * Follows a user
     * @param idUser username of the user to follow
     */
    public void followUser(String idUser) {
        if (socketChannel == null || currentLoggedUser == null) {
            System.err.println("< There is no user logged in");
            return;
        }

        //Builds the request
        String request = "follow " + idUser + " " + currentLoggedUser;
        try {
            sendRequest(request);
            readResponse();
            //Gets the response code
            int responseId = buffer.getInt();

            if (responseId == 0) System.out.println("< " + idUser + " followed");
            if (responseId == 1) System.err.println("< There is no user logged in");
            if (responseId == 2) System.out.println("< You can't follow yourself");
            if (responseId == 3) System.err.println("< User " + idUser + " doesn't exists");
            if (responseId == 4) System.err.println("< User " + idUser + " already followed");

        } catch (IOException e) {
            System.err.println("Error while following user (" + e.getMessage() + ")");
        }
    }

    /**
     * Unfollows a user
     * @param idUser username of the user to follow
     */
    public void unfollowUser(String idUser) {
        if (socketChannel == null || currentLoggedUser == null) {
            System.err.println("< There is no user logged in");
            return;
        }

        //Builds the request
        String request = "unfollow " + idUser + " " + currentLoggedUser;
        try {
            sendRequest(request);
            readResponse();
            //Gets the response code
            int responseId = buffer.getInt();

            if (responseId == 0) System.out.println("< " + idUser + " unfollowed");
            if (responseId == 1) System.err.println("< There is no user logged in");
            if (responseId == 2) System.out.println("< You can't unfollow yourself");
            if (responseId == 3) System.err.println("< User " + idUser + " doesn't exists");
            if (responseId == 4) System.err.println("< User " + idUser + " already not followed");

        } catch (IOException e) {
            System.err.println("Error while unfollowing user (" + e.getMessage() + ")");
        }
    }

    /**
     * Prints on the standard output the list of user who are following the user logged
     */
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

    /**
     * Logout a user from winsome
     */
    public void logout() {
        if (currentLoggedUser == null) {
            System.err.println("< There are no user logged");
            return;
        }

        //Builds the request
        String request = "logout " + currentLoggedUser;
        try {
            sendRequest(request);
            readResponse();
            //Gets the response code
            int responseId = buffer.getInt();

            if (responseId == 0) {
                System.out.println("< " + currentLoggedUser + " logged out");
                //Closes the connection
                socketChannel.close();
                socketChannel = null;

                //Interrupts the thread which receive the multicast notification
                multicastThread.interrupt();
                multicastSocket.close();

                //Resets the followers list
                followers = null;
                //Unregisters from the callback
                serverCallbackHandler.unregisterForCallback(currentLoggedUser);

                //Resets the user logged
                currentLoggedUser = null;

                //Disables the RMI callback to receive followers notification
                UnicastRemoteObject.unexportObject(followerCallback, true);
                followerCallback = null;
            } else {
                System.err.println("< " + currentLoggedUser + " is not logged in");
            }

        } catch (IOException e) {
            System.err.println("Error during logout (" + e.getMessage() + ")");
        }
    }

    /**
     * Let a user login in winsome
     * @param username username of the user to login
     * @param password password of the user to login
     */
    public void login(String username, String password) {
        //Checks if a user is already logged in
        if (socketChannel != null) {
            System.err.println("< There's a user already logged. He needs to logout");
        }

        try {
            //Connects to the server
            socketChannel = SocketChannel.open(new InetSocketAddress(serverIP, tcpPort));

            //Builds and sends request to the server
            sendRequest("login " + username + " " + password);
            readResponse();
            //Reads the response code
            int responseId = buffer.getInt();

            if (responseId == 0) {
                System.out.println("< " + username + " logged in");
                //Sets the user logged
                currentLoggedUser = username;

                //Gets the response
                String multicastReferences = extractResponse();

                //Gets the multicast ip and multicast port
                JsonObject jsonObject = JsonParser.parseString(multicastReferences).getAsJsonObject();
                String multicastIP = jsonObject.get("multicastIP").getAsString();
                int multicastPort = jsonObject.get("multicastPort").getAsInt();

                //Creates and starts the thread which receive the multicast notifications
                multicastSocket = new MulticastSocket(multicastPort);
                multicastThread = new Thread(new NotifyHandler(multicastIP, multicastSocket));
                multicastThread.start();

                followers = new ArrayList<>();
                followerCallback = new NotifyNewFollowerService(followers);
                //Exports the object and gets the stub
                callbackStub = (NotifyNewFollower) UnicastRemoteObject.exportObject(followerCallback, 0);
                //Registers for callback
                serverCallbackHandler.registerForCallback(currentLoggedUser, callbackStub);

                //Retrieves the followers list from the server
                getFollowers(username);
            }
            if (responseId == 1) System.err.println("< Username or password not correct");
            if (responseId == 2) System.err.println("< Already logged on another terminal");

            if (responseId == 1 || responseId == 2) {
                //Close the connection with the server
                socketChannel.close();
                socketChannel = null;
            }
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("Error during login, please try again (" + e.getMessage() + ")");
        }
    }

    /**
     * Retreives the followers list from the server
     * @param username the user who wants his own follower list
     */
    private void getFollowers(String username) {
        try {
            //Builds and sends the request
            sendRequest("getFollowers " + username);
            readResponse();

            //Reads the response code
            buffer.getInt();
            //Reads the response
            String userFollowers = extractResponse();

            //Checks if the user doesn't have followers
            if (userFollowers.equals("[]")) return;

            //Gets followers from json string
            getUserFromJson(followers, userFollowers);

        } catch (IOException e) {
            System.err.println("Error while recovering followers (" + e.getMessage() + ")");
        }
    }

    /**
     * Puts the json entry in the list
     * @param list list where the users will be added
     * @param jsonString json string which contains the users
     */
    public void getUserFromJson(List<String> list, String jsonString) {
        //Parses the string to a json array
        JsonArray jsonArray = JsonParser.parseString(jsonString).getAsJsonArray();

        //Inserts every json element, as a string, in the list
        for (JsonElement jsonElement : jsonArray)
            list.add(jsonElement.getAsString());
    }

    /**
     * Prints to the standard output the list of users which have at least one common tag
     * with the user
     */
    public void listUsers() {
        if (socketChannel == null || currentLoggedUser == null) {
            System.err.println("< There is no user logged");
            return;
        }

        try {
            //Builds and sends the request
            sendRequest( "list users " + currentLoggedUser);
            readResponse();

            //Reads the response code
            int responseId = buffer.getInt();
            if (responseId == -1) {
                System.err.println("< Invalid command. Usage: list users");
                return;
            }

            if (responseId == 0) {
                //Gets the response as a string
                String result = extractResponse();

                System.out.printf("< %10s%10s%10s\n", "Users", "|", "Tags");
                System.out.println("< ----------------------------------------");

                //Parses the string to a json array
                JsonArray jsonArray = JsonParser.parseString(result).getAsJsonArray();

                //Prints every user
                for (JsonElement jsonElement : jsonArray) {
                    JsonObject jsonEntry = JsonParser.parseString(jsonElement.toString()).getAsJsonObject();

                    String username = jsonEntry.get("username").getAsString();

                    JsonArray jsonTags = jsonEntry.get("tags").getAsJsonArray();
                    String []tags = new String[jsonTags.size()];
                    //Puts the tags in an array
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

    /**
     * Creates a new post in winsome
     * @param title post title
     * @param content post content
     */
    public void createPost(String title, String content) {
        if (socketChannel == null || currentLoggedUser == null) {
            System.err.println("< There is no user logged");
            return;
        }

        //Builds the request
        String request = "post /" + title + "/" + content + "/" + currentLoggedUser;
        try {
            sendRequest(request);
            readResponse();

            //Reads the response code
            int responseId = buffer.getInt();
            if (responseId == 0) System.out.println("< Post created correctly");
            if (responseId == 1) System.err.println("< There is no user logged in");
            if (responseId == 2) System.err.println("< Invalid post arguments. Title must have less then 20 characters, the content mush have less then 500 characters");

        } catch (IOException e) {
            System.err.println("< Error during comunication with server (" + e.getMessage() + ")");
        }
    }

    /**
     * Deletes a post from winsome
     * @param idPost post id
     */
    public void deletePost(String idPost) {
        if (socketChannel == null || currentLoggedUser == null) {
            System.err.println("< There is no user logged");
            return;
        }

        try {
            //Builds the request
            String request = "delete " + idPost + " " + currentLoggedUser;
            sendRequest(request);
            readResponse();

            //Reads the response code
            int responseId = buffer.getInt();
            if (responseId == -1) System.out.println("< Invalid request. Usage: delete <idPost>");
            if (responseId == 0) System.out.println("< Post deleted correctly");
            if (responseId == 1) System.err.println("< There is no user logged");
            if (responseId == 2) System.err.println("< This post doesn't exists");
            if (responseId == 3) System.err.println("< You don't own this post");
        } catch (IOException e) {
            System.err.println("< Error during comunication with server (" + e.getMessage() + ")");
        }
    }

    /**
     * Prints to the standard output the list of users who are followed by the user logged
     */
    public void listFollowing() {
        if (socketChannel == null || currentLoggedUser == null) {
            System.err.println("< There is no user logged");
            return;
        }

        try {
            //Builds the request
            String request = "list following " + currentLoggedUser;
            sendRequest(request);
            readResponse();

            //Reads the response code
            int responseId = buffer.getInt();
            if (responseId == 0) {
                //Gets the response as a string
                String userFollowing = extractResponse();

                System.out.printf("< %10s%10s%10s\n", "Users", "|", "Tags");
                System.out.println("< ----------------------------------------");

                //Parses the string as a json array
                JsonArray jsonArray = JsonParser.parseString(userFollowing).getAsJsonArray();
                //Prints all the users
                for (JsonElement jsonElement : jsonArray) {
                    JsonObject jsonEntry = JsonParser.parseString(jsonElement.toString()).getAsJsonObject();
                    String username = jsonEntry.get("username").getAsString();

                    JsonArray jsonTags = jsonEntry.get("tags").getAsJsonArray();
                    String []tags = new String[jsonTags.size()];
                    //Puts the tags in the array
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

    /**
     * Prints to the standard output the list of posts the user has created
     */
    public void viewBlog() {
        if (socketChannel == null || currentLoggedUser == null) {
            System.err.println("< There is no user logged");
            return;
        }

        try {
            //Builds the request
            String request = "blog " + currentLoggedUser;
            sendRequest(request);
            readResponse();

            //Reads the response code
            int responseId = buffer.getInt();
            if (responseId == 0) {
                //Gets the response as a string
                String blogPosts = extractResponse();

                System.out.printf("< %-4s %s %-14s %s %10s\n", "Id", "|", "Author", "|", "Title");
                System.out.println("< ---------------------------------------------------------");

                //Parses the string as a json array
                JsonArray jsonArray = JsonParser.parseString(blogPosts).getAsJsonArray();
                //Prints all the post
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

    /**
     * Prints the standard output the specified post
     * @param idPost id of the post to print
     */
    public void showPost(String idPost) {
        if (socketChannel == null || currentLoggedUser == null) {
            System.err.println("< There is no user logged");
            return;
        }

        try {
            //Builds the request
            String request = "show post " + idPost + " " + currentLoggedUser;
            sendRequest(request);
            readResponse();

            //Reads the response code
            int responseId = buffer.getInt();
            if (responseId == -1) System.err.println("< Invalid request. Usage: show post <idPost>");
            if (responseId == 0) {
                //Gets the response as a string
                String post = extractResponse();

                //Parses the string as a json object
                JsonObject jsonObject = JsonParser.parseString(post).getAsJsonObject();
                String postTitle = jsonObject.get("postTitle").getAsString();
                String postContent = jsonObject.get("postContent").getAsString();
                int upvotes = jsonObject.get("upvotes").getAsInt();
                int downvotes = jsonObject.get("downvotes").getAsInt();
                //Parses the comments as a json array
                JsonArray comments = jsonObject.get("comments").getAsJsonArray();

                //Prints the post
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

    /**
     * Prints to the standard output the post list created by the users
     * followed by the user currently logged in
     */
    public void showFeed() {
        if (socketChannel == null || currentLoggedUser == null) {
            System.err.println("< There is no user logged");
            return;
        }

        try {
            //Builds the request
            String request = "show feed " + currentLoggedUser;
            sendRequest(request);
            readResponse();

            //Reads the response code
            int responseId = buffer.getInt();
            if (responseId == 0) {
                String blogPosts = extractResponse();

                System.out.printf("< %-4s %s %-14s %s %10s\n", "Id", "|", "Author", "|", "Title");
                System.out.println("< ---------------------------------------------------------");

                //Parses the string as a json array
                JsonArray jsonArray = JsonParser.parseString(blogPosts).getAsJsonArray();
                //Prints all the posts
                for (JsonElement jsonElement : jsonArray) {
                    //Parses the json element as an entry
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

    /**
     * Rewins the post specified
     * @param idPost post id of the post to rewin
     */
    public void rewinPost(String idPost) {
        if (socketChannel == null || currentLoggedUser == null) {
            System.err.println("< There is no user logged");
            return;
        }

        try {
            //Builds the request
            String request = "rewin " + idPost + " " + currentLoggedUser;
            sendRequest(request);
            readResponse();

            //Reads the response code
            int responseId = buffer.getInt();
            if (responseId == -1) System.out.println("< Invalid request. Usage: rewin <idPost>");
            if (responseId == 0) System.out.println("< Post rewinned correctly");
            if (responseId == 1) System.err.println("< There is no user logged");
            if (responseId == 2) System.err.println("< This post doesn't exists");
            if (responseId == 3) System.err.println("< You've already rewinned this post");
        } catch (IOException e) {
            System.err.println("< Error during comunication with server (" + e.getMessage() + ")");
        }
    }

    /**
     * Adds a comment to the specified post
     * @param idPost post id of the post to comment
     * @param comment comment to add
     */
    public void addComment(String idPost, String comment) {
        if (socketChannel == null || currentLoggedUser == null) {
            System.err.println("< There is no user logged");
            return;
        }

        try {
            //Builds the request
            String request = "comment /" + idPost + "/" + comment + "/" + currentLoggedUser;
            sendRequest(request);
            readResponse();

            //Reads the response code
            int responseId = buffer.getInt();
            if (responseId == -1) System.out.println("< Invalid request. Usage: comment <idPost> \"<comment>\"");
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

    /**
     * Prints to the standard output the wallet of the current user
     */
    public void getWallet() {
        if (socketChannel == null || currentLoggedUser == null) {
            System.err.println("< There is no user logged");
            return;
        }

        try {
            //Builds the request
            String request = "wallet " + currentLoggedUser;
            sendRequest(request);
            readResponse();

            //Reads the response code
            int responseId = buffer.getInt();
            if (responseId == -1) System.err.println("< Invalid command. Use 'wallet' or 'wallet btc'");
            if (responseId == 0) {
                //Gets the response as a stirng
                String wallet = extractResponse();

                //Parses the string as a json object
                JsonObject jsonObject = JsonParser.parseString(wallet).getAsJsonObject();

                double wincoin = jsonObject.get("wincoin").getAsDouble();
                JsonArray transactions = jsonObject.get("transactions").getAsJsonArray();

                //Prints the wallet
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

    /**
     * Prints to the standard output the wallet converted in BitCoin
     * of the current user
     */
    public void getWalletInBitcoin() {
        if (socketChannel == null || currentLoggedUser == null) {
            System.err.println("< There is no user logged");
            return;
        }

        try {
            //Builds the request
            String request = "wallet btc " + currentLoggedUser;
            sendRequest(request);
            readResponse();

            //Reads the response code
            int responseId = buffer.getInt();
            if (responseId == -1) System.err.println("< Invalid command. Use 'wallet' or 'wallet btc'");
            if (responseId == 0) {
                String wallet = extractResponse();

                //Parses the string as a json object
                JsonObject jsonObject = JsonParser.parseString(wallet).getAsJsonObject();

                double btc = jsonObject.get("wincoinBTC").getAsDouble();
                JsonArray transactions = jsonObject.get("transactions").getAsJsonArray();

                //Prints the wallet converted
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

    /**
     * Registers a new user to winsome
     * @param username username of the user to register
     * @param password password of the user to register
     * @param tags list of tags of the user to register
     */
    public void register(String username, String password, List<String> tags) {
        if (socketChannel != null || currentLoggedUser != null) {
            System.err.println("< Please logout before registering another user");
            return;
        }

        try {
            int result;
            //Tries to register the user
            if ((result = register.register(username, password, tags)) != 0) {
                if (result == -1) System.out.println("< Error server side");
                if (result == 1) System.out.println("< Password field is empty");
                if (result == 2 || result == 3) System.out.println("< Registration requires minimum 1 tag and maximum 5");
                if (result == 4) System.out.println("< User '" + username + "' already registered");
                return;
            }
            System.out.println("< " + username + " registered correctly");
        } catch (RemoteException e) {
            System.out.println("< Error while registering new user: " + e.getMessage());
        }
    }

    /**
     * Closes the winsome API
     */
    public void close() {
        //Interrupts the thread who is waiting for multicast notifications
        if (multicastSocket != null) {
            multicastThread.interrupt();
            multicastSocket.close();
        }

        try {
            //Closes the SocketChannel
            if (socketChannel != null) {
                socketChannel.close();
                socketChannel = null;
            }

            if (currentLoggedUser != null) {
                //Unregisters from the callback
                serverCallbackHandler.unregisterForCallback(currentLoggedUser);
            }

            //Resets the user logged
            currentLoggedUser = null;

            //Resets the followers list
            followers = null;

            //Unexports the object to receive the callbacks for followers updates
            UnicastRemoteObject.unexportObject(followerCallback, true);
        } catch (IOException e) {
            System.err.println("Error while closing the API: (" + e.getMessage() + ")");
        }
    }
}
