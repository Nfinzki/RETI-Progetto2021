/**
 * This class implements a task that reads the request, elaborates it and
 * prepares the response
 */

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.rmi.RemoteException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class ReaderWorker implements Runnable {
    private final SelectionKey key;
    private final SocketChannel client;
    private final ByteBuffer byteBuffer;

    private final Map<String, User> users;
    private final Map<Integer, Post> posts;
    private final CallbackHandler callbackHandler;
    private final Map<String, Socket> loggedUsers;
    private final Set<Registable> readyToBeRegistered;

    private final Selector selector;

    private final AtomicBoolean stateChanged;

    private JsonElement jsonElement = null;

    public ReaderWorker(SelectionKey key, Map<String, User> users, Map<Integer, Post> posts, Map<String, Socket> loggedUsers, CallbackHandler callbackHandler, Set<Registable> readyToBeRegistered, Selector selector, AtomicBoolean stateChanged) {
        this.key = key;
        this.users = users;
        this.posts = posts;
        this.loggedUsers = loggedUsers;
        this.callbackHandler = callbackHandler;
        this.readyToBeRegistered = readyToBeRegistered;
        this.selector = selector;
        this.stateChanged = stateChanged;

        this.client = (SocketChannel) key.channel();
        this.byteBuffer = (ByteBuffer) key.attachment();
    }

    public void run() {
        //Reads the request from client
        String request = readRequest();
        if (request == null) return;

        //Gets the request arguments
        String[] args = request.split(" ");

        switch (args[0]) {
            case "login" -> {
                if (args.length != 3) { //Checks the correctness of the request
                    setResponse(-1);
                    break;
                }

                login(args[1], args[2]);
            }

            case "logout" -> {
                if (args.length != 2) { //Checks the correctness of the request
                    setResponse(-1);
                    break;
                }

                logout(args[1]);
            }

            case "list" -> {
                if (args.length != 3) { //Checks the correctness of the request
                    setResponse(-1);
                    break;
                }

                if (args[1].equals("users")) {
                    listUsers(args[2]);
                    break;
                }

                if (args[1].equals("following")) {
                    listFollowing(args[2]);
                    break;
                }

                setResponse(-1); //Invalid request
            }

            case "post" -> {
                String []postArgs = request.split("/");
                if (postArgs.length != 4) {
                    setResponse(-1);
                    break;
                }

                createPost(postArgs[3], postArgs[1], postArgs[2]);
            }

            case "delete" -> {
                if (args.length != 3) { //Checks the correctness of the request
                    setResponse(-1);
                    break;
                }

                try {
                    deletePost(args[2], Integer.parseInt(args[1]));
                } catch (NumberFormatException e) { setResponse(-1); }
            }

            case "follow" -> {
                if (args.length != 3) { //Checks the correctness of the request
                    setResponse(-1);
                    break;
                }

                followUser(args[2], args[1]);
            }

            case "unfollow" -> {
                if (args.length != 3) { //Checks the correctness of the request
                    setResponse(-1);
                    break;
                }

                unfollowUser(args[2], args[1]);
            }

            case "rate" -> {
                if (args.length != 4) { //Checks the correctness of the request
                    setResponse(-1);
                    break;
                }

                try {
                    ratePost(args[3], Integer.parseInt(args[1]), args[2]);
                } catch (NumberFormatException e) { setResponse(-1); }
            }

            case "blog" -> {
                if (args.length != 2) { //Checks the correctness of the request
                    setResponse(-1);
                    break;
                }

                viewBlog(args[1]);
            }

            case "show" -> {
                if (args[1].equals("post") && args.length == 4) {
                    try {
                        showPost(Integer.parseInt(args[2]), args[3]);
                    } catch (NumberFormatException e) { setResponse(-1); }
                    break;
                }

                if (args[1].equals("feed") && args.length == 3) {
                    showFeed(args[2]);
                    break;
                }

                setResponse(-1); //Invalid request
            }

            case "rewin" -> {
                if (args.length != 3) { //Checks the correctness of the request
                    setResponse(-1);
                    break;
                }

                try {
                    rewinPost(Integer.parseInt(args[1]), args[2]);
                } catch (NumberFormatException e) { setResponse(-1); }
            }

            case "comment" -> {
                String []commentArgs = request.split("/");
                if (commentArgs.length != 4) {
                    setResponse(-1);
                    break;
                }

                String comment = commentArgs[2];
                String username = commentArgs[3];

                if (comment.equals("") || username.equals("")) { //Checks the correctness of the request
                    setResponse(-1);
                    break;
                }

                try {
                    addComment(Integer.parseInt(commentArgs[1]), comment, username);
                } catch (NumberFormatException e) { setResponse(-1); }
            }

            case "wallet" -> {
                if (args.length == 2) {
                    getWallet(args[1]);
                    break;
                }

                if (args.length == 3) {
                    getWalletBTC(args[2]);
                    break;
                }

                setResponse(-1); //Invalid request
            }

            case "getFollowers" -> {
                if (args.length != 2) { //Checks the correctness of the request
                    setResponse(-1);
                    break;
                }

                sendFollowers(args[1]);
            }

            default -> setResponse(-2); //Invalid request
        }

        //Marks the client as ready
        readyToBeRegistered.add(new Registable(client, SelectionKey.OP_WRITE, byteBuffer, jsonElement));
        //Wakes up the selector to re-register the key
        selector.wakeup();
    }

    /**
     * Reads bytes from channel and writes it to the buffer
     * @return the number of bytes read
     * @throws IOException if some IO error occurs
     */
    private int readFromChannelToBuffer() throws IOException{
        //Prepares for writing to the buffer
        byteBuffer.clear();
        //Writes to the buffer
        int byteRead = client.read(byteBuffer);
        //Prepares the buffer to be read
        byteBuffer.flip();

        return byteRead;
    }

    /**
     * Reads a request from the SocketChannel
     * @return the request string or null if some error occur
     */
    private String readRequest() {
        try {
            //Reads bytes from the channel
            int byteRead = readFromChannelToBuffer();

            //Checks if the client sent the termination signal
            if (byteRead == -1) throw new IOException("Client disconnected");

            //Reads the request len
            int requestLen = byteBuffer.getInt();
            int totalRead = 0; //Bytes read
            StringBuilder request = new StringBuilder();

            //Reads all the request bytes
            while (totalRead < requestLen) {
                //Initializes the byte array to read the bytes
                byte[] requestByte = new byte[byteBuffer.limit() - byteBuffer.position()];
                //Puts all the bytes in the buffer
                byteBuffer.get(requestByte);
                //Adds the current bytes read to the request
                request.append(new String(requestByte));

                //Updates the bytes read
                totalRead += requestByte.length;

                //Checks if read all the request
                if (totalRead == requestLen) break;

                //Reads bytes from the channel
                byteRead = readFromChannelToBuffer();
                if (byteRead == -1) throw new IOException("Client disconnected");
            }

            return request.toString();

        } catch (IOException e) {
            try {client.close();} catch (Exception ignored) {}
        }

        return null;
    }

    /**
     * Sets the response code on the buffer
     * @param code Response code
     */
    private void setResponse(int code) {
        byteBuffer.clear(); //Prepares the buffer to be written
        byteBuffer.putInt(code); //Writes the response code
    }

    /**
     * Logins a user inside the social network
     * @param username the username of the user to log in
     * @param password the password of the user to log in
     */
    private void login(String username, String password) {
        try {
            //Gets the user with the specified username
            User user = users.get(username);

            if (user != null) {
                if (user.comparePassword(Hash.bytesToHex(Hash.sha256(username + password)))) { //The password is correct
                    synchronized (loggedUsers) {
                        if (loggedUsers.containsKey(username)) { //Checks if the user is already logged in
                            setResponse(2);
                            return;
                        }

                        loggedUsers.put(username, client.socket()); //Saves that the user is logged in
                    }

                    //Sets the references for the multicast group to send to the client
                    jsonElement = JsonParser.parseString("{\"multicastIP\": \"" + ServerMain.multicastIP + "\", \"multicastPort\": " + ServerMain.multicastPort + "}");
                    setResponse(0);
                } else //The password isn't correct
                    setResponse(1);
            } else { //The user is not registered
                setResponse(1);
            }
        } catch (NoSuchAlgorithmException e) {
            System.err.println("Error while hashing the password: " + e.getMessage());
            try {client.close();} catch (Exception ignored) {}
        }
    }

    /**
     * Logouts a user from the social network
     * @param username the username of the user to logout
     */
    private void logout(String username) {
        if (loggedUsers.remove(username) != null) //User removed correctly
            setResponse(0);
        else //User wasn't logged in
            setResponse(1);
    }

    /**
     * Sends a json array to the client that contains all the users who have at least one tag in common with him
     * @param username the username of the user that made the request
     */
    private void listUsers(String username) {
        if (!loggedUsers.containsKey(username)) { //Checks if the user is logged in
            setResponse(1);
            return;
        }

        Gson gson = new Gson();
        JsonArray jsonResponse = new JsonArray();

        //Gets the user information
        User user = users.get(username);

        //Populates the JsonArray with the users who have at least one tag in common
        for (User u : users.values()) {
            if (user != u) { //Checks if the user u is not who made the request
                //Gets the common tags
                String []commonTags = user.getCommonTags(u);

                //If there aren't common tags, skip this user u
                if (commonTags.length == 0) continue;

                //Creates and add the JsonElement to the JsonArray
                jsonResponse.add(JsonParser.parseString("{\"username\": \"" + u.getUsername() + "\", \"tags\": " + gson.toJson(commonTags) + "}"));
            }
        }

        //Sets the response
        setResponse(0);
        jsonElement = jsonResponse;
    }

    /**
     * Adds userToFollow to the followed list of the user with "username" username
     * and adds "username" to the userToFollow follower list
     * @param username username of the user who made the request
     * @param userToFollow username of the user to follow
     */
    private void followUser(String username, String userToFollow) {
        if (!loggedUsers.containsKey(username)) { //Checks if the user is logged in
            setResponse(1);
            return;
        }

        //Gets the information of the user to follow
        User userToFollowObj = users.get(userToFollow);
        if (userToFollowObj == null) { //User not registered
            setResponse(2);
            return;
        }

        try {
            //Performs the actions for following a user
            if (users.get(username).addFollowed(userToFollow) && userToFollowObj.addFollower(username)) {
                stateChanged.set(true); //State of the server has changed
                setResponse(0);

                //Notifies the user followed that has a new follower
                callbackHandler.notifyNewFollower(userToFollow, username);
            } else //User already followed that user
                setResponse(3);
        } catch (RemoteException e) {
            System.err.println("Error while notifying the new follower: " + e.getMessage());
            try {client.close();} catch (Exception ignored) {}
        }
    }

    /**
     * Removes userToFollow to the followed list of the user with "username" username
     * and removes "username" to the userToFollow follower list
     * @param username username of the user who made the request
     * @param userToUnfollow username of the user to unfollow
     */
    private void unfollowUser(String username, String userToUnfollow) {
        if (!loggedUsers.containsKey(username)) { //Checks if the user is logged in
            setResponse(1);
            return;
        }

        //Gets the information of the user to unfollow
        User userToUnfollowObj = users.get(userToUnfollow);
        if (userToUnfollowObj == null) { //User not registered
            setResponse(2);
            return;
        }

        try {
            //Performs the actions for unfollowing a user
            if (users.get(username).removeFollowed(userToUnfollow) && userToUnfollowObj.removeFollower(username)) {
                stateChanged.set(true); //State of the server has changed
                setResponse(0);

                //Notifies the user unfollowed that he has lost a follower
                callbackHandler.notifyLostFollower(userToUnfollow, username);
            } else //User wasn't following that user
                setResponse(3);
        } catch (RemoteException e) {
            System.err.println("Error while notifying the unfollow: " + e.getMessage());
            try {client.close();} catch (Exception ignored) {}
        }
    }

    /**
     * Add an upvote or downvote to a post
     * @param username the username of the user who made the request
     * @param idPost the id of the post to rate
     * @param vote +1 is an upvote, -1 is a downvote
     */
    private void ratePost(String username, int idPost, String vote) {
        if (!loggedUsers.containsKey(username)) { //Checks if the user is logged in
            setResponse(1);
            return;
        }

        //Checks if the vote isn't allowed
        if (!vote.equals("+1") && !vote.equals("-1")) {
            setResponse(2);
            return;
        }

        //Gets the information of the post
        Post post = posts.get(idPost);

        if (post == null) { //The post doesn't exist
            setResponse(3);
            return;
        }

        if (post.getAuthor().equals(username)) { //The user is the author of the post
            setResponse(4);
            return;
        }

        if (post.containsUpvote(username) || post.containsDownvote(username)) { //The user already rated the post
            setResponse(5);
            return;
        }

        //Adds the vote
        if (vote.equals("+1")) post.addUpvote(username);
        if (vote.equals("-1")) post.addDownvote(username);

        stateChanged.set(true); //States of the server has changed
        setResponse(0);
    }

    /**
     * Creates a new post
     * @param username the username of the user who made the request
     * @param title title of the new post
     * @param content content of the new post
     */
    private void createPost(String username, String title, String content) {
        if (!loggedUsers.containsKey(username)) { //Checks if the user is logged in
            setResponse(1);
            return;
        }

        //Checks if the title and content meet the constraints
        if (title.length() == 0 || title.length() > 20 || content.length() == 0 || content.length() > 500) {
            setResponse(2);
            return;
        }

        //Creates a new post
        Post newPost = new Post(username, title, content);
        //Adds the post to the server state
        posts.put(newPost.getIdPost(), newPost);
        //Gets the information of the user
        User user = users.get(username);
        //Adds the post in the user post list
        user.addPost(newPost.getIdPost());

        stateChanged.set(true); //States of the server has changed
        setResponse(0);
    }

    /**
     * Deletes the specified post
     * @param username username of the user who made the request
     * @param idPost id of the post to delete
     */
    private void deletePost(String username, int idPost) {
        if (!loggedUsers.containsKey(username)) { //Checks if the user is logged in
            setResponse(1);
            return;
        }

        //Gets the information of the post
        Post post = posts.get(idPost);

        if (post == null) { //The post doesn't exist
            setResponse(2);
            return;
        }

        //Gets the information of the user
        User user = users.get(username);

        if (!user.ownsPost(idPost)) { //The user is not the author of the post
            setResponse(3);
            return;
        }

        //Removes the post from the state of the server
        posts.remove(idPost);
        //Removes the post from the post list of the user
        user.removePost(idPost);

        //Removes the post from the post list of all the user who
        //rewinned the post
        for (String rewinner : post.getRewinner()) {
            User rewinnerUser = users.get(rewinner);
            rewinnerUser.removePost(idPost);
        }

        stateChanged.set(true); //States of the server has changed
        setResponse(0);
    }

    /**
     * Sends to the client the list of the user who is followed by "username"
     * @param username the username of the user who made the request
     */
    private void listFollowing(String username) {
        if (!loggedUsers.containsKey(username)) { //Checks if the user is logged in
            setResponse(1);
            return;
        }

        //Gets the information of the user
        User user = users.get(username);
        //Initializes the json array who will be sent to the client
        JsonArray jsonResponse = new JsonArray();
        Gson gson = new Gson();

        //Adds every user who is followed by "username" to the json array
        for (String u : user.getFollowing()) {
            jsonResponse.add(
                    //Parses the string to a JsonElement
                    JsonParser.parseString("{\"username\": \"" + u + "\", \"tags\": " + gson.toJson(user.getCommonTags(users.get(u))) + "}")
            );
        }

        //Sets the response
        setResponse(0);
        jsonElement = jsonResponse;
    }

    /**
     * Sends to the client the posts that the user has created
     * @param username username of the user who made the request
     */
    private void viewBlog(String username) {
        if (!loggedUsers.containsKey(username)) { //Checks if the user is logged in
            setResponse(1);
            return;
        }

        //Gets the information of the user
        User user = users.get(username);
        //Initializes the json array who will be sent to the client
        JsonArray jsonResponse = new JsonArray();

        //Adds every post created by user to the json array
        for (int postId : user.getBlog()) {
            //Gets the information of the post
            Post post = posts.get(postId);

            //Adds only the post that the user created and discards the rewinned post
            if (post.getAuthor().equals(username))
                jsonResponse.add(JsonParser.parseString(post.basicInfoToJson(username)));
        }

        //Sets the response
        setResponse(0);
        jsonElement = jsonResponse;
    }

    /**
     * Sends to the client the post information
     * @param idPost id of the post to send to the client
     * @param username username of the user who made the request
     */
    private void showPost(int idPost, String username) {
        if (!loggedUsers.containsKey(username)) { //Checks if the user is logged in
            setResponse(1);
            return;
        }

        //Gets the information of the post
        Post post = posts.get(idPost);

        if (post == null) { //The post doesn't exist
            setResponse(2);
            return;
        }

        //Sets the response
        setResponse(0);
        jsonElement = JsonParser.parseString(post.toJson());
    }

    /**
     * Sends to the client the feed of the user
     * @param username username of the user who made the request
     */
    private void showFeed(String username) {
        if (!loggedUsers.containsKey(username)) { //Checks if the user is logged in
            setResponse(1);
            return;
        }

        //Gets the information of the user
        User user = users.get(username);
        //Initializes the json array who will be sent to the client
        JsonArray jsonResponse = new JsonArray();

        //Adds every post of the users followed by "username"
        for (String usernameFollowed : user.getFollowed()) {
            //Gets information about the user
            User userFollowed = users.get(usernameFollowed);

            //Adds every post who's made by that user to the json array
            //even the rewinned one
            for (int postId : userFollowed.getBlog()) {
                //Gets information about the post
                Post post = posts.get(postId);
                jsonResponse.add(JsonParser.parseString(post.basicInfoToJson(usernameFollowed)));
            }
        }

        //Sets the response
        setResponse(0);
        jsonElement = jsonResponse;
    }

    /**
     * Adds a post of another user to the post list of "username"
     * @param idPost id of the post to rewin
     * @param username username of the user who made the request
     */
    private void rewinPost(int idPost, String username) {
        if (!loggedUsers.containsKey(username)) { //Checks if the user is logged in
            setResponse(1);
            return;
        }

        //Gets the information about the post
        Post post = posts.get(idPost);

        if (post == null) { //The post doesn't exist
            setResponse(2);
            return;
        }

        //Gets the information about the user
        User user = users.get(username);

        if (user.addPost(post.getIdPost())) { //Adds the post to the user post list
            post.addRewinner(username); //Adds the user to the rewinner list

            stateChanged.set(true); //State of the server changed
            setResponse(0);
        } else //Post already rewinned
            setResponse(3);
    }

    /**
     * Adds a comment to a post
     * @param idPost id of the post
     * @param comment comment to add to the post
     * @param username username of the user who made the request
     */
    private void addComment(int idPost, String comment, String username) {
        if (!loggedUsers.containsKey(username)) { //Checks if the user is connected
            setResponse(1);
            return;
        }

        if (comment.equals("")) { //Checks if the comment is empty
            setResponse(2);
            return;
        }

        //Gets information about the post
        Post post = posts.get(idPost);

        if (post == null) { //The post doesn't exits
            setResponse(3);
            return;
        }

        //Checks if the user doesn't follow the author of the post
        if (!users.get(username).follows(post.getAuthor())) {
            setResponse(4);
            return;
        }

        //The user who wants to add the comment is the author of the post
        if (post.getAuthor().equals(username)) {
            setResponse(5);
            return;
        }

        //Adds the comment to the post
        post.addComment(new Comment(username, comment));

        stateChanged.set(true); //State of the server changed
        setResponse(0);
    }

    /**
     * Sends to the client the user wallet in wincoin
     * @param username username of the user who made the request
     */
    private void getWallet(String username) {
        if (!loggedUsers.containsKey(username)) { //Checks if the user is logged in
            setResponse(1);
            return;
        }

        //Gets the information about the user
        User user = users.get(username);

        //Sets the wallet as JsonElement as response
        setResponse(0);
        jsonElement = JsonParser.parseString(user.getWalletAsJson());
    }

    /**
     * Sends to the client the user wallet in bitcoin
     * @param username username of the user who made the request
     */
    private void getWalletBTC(String username) {
        if (!loggedUsers.containsKey(username)) { //Checks if the user is logged in
            setResponse(1);
            return;
        }

        //Gets the information about the user
        User user = users.get(username);

        //Creates the json string
        StringBuilder response = new StringBuilder("{\"wincoinBTC\":" + user.getWallet().wincoinToBTC() + ", \"transactions\": [");

        boolean firstEntry = true;
        //Adds every transaction to the json string
        for (Transaction t : user.getWallet().getTransactions()) {
            //If it's not the first entry, adds a comma
            if (!firstEntry) response.append(",");

            response.append(t.toJson());

            firstEntry = false;
        }
        response.append("]}"); //Ends the json string

        //Sets the response
        setResponse(0);
        jsonElement = JsonParser.parseString(response.toString());
    }

    /**
     * Sends the followers of a user to the client
     * @param username username of the user who made the request
     */
    private void sendFollowers(String username) {
        //Gets the information about the user
        User user = users.get(username);

        //Sets the response
        setResponse(0);
        jsonElement = JsonParser.parseString(user.getFollowersAsJson());
    }
}
