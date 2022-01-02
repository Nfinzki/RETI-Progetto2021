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

            case "post" -> createPost(request);

            case "delete" -> {
                if (args.length != 3) { //Checks the correctness of the request
                    setResponse(-1);
                    break;
                }

                deletePost(args[2], Integer.parseInt(args[1]));
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

                ratePost(args[3], Integer.parseInt(args[1]), args[2]);
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
                    showPost(Integer.parseInt(args[2]), args[3]);
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

                rewinPost(Integer.parseInt(args[1]), args[2]);
            }

            case "comment" -> {
                String comment = request.substring(request.indexOf("\"") + 1, request.lastIndexOf("\""));
                String username = request.substring(request.lastIndexOf("\"") + 2);

                if (comment.equals("") || username.equals("")) { //Checks the correctness of the request
                    setResponse(-1);
                    break;
                }

                addComment(Integer.parseInt(args[1]), comment, username);
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
        }

        //TODO Cosa succede poi nel main se c'è stata la client.close() ?
        readyToBeRegistered.add(new Registable(client, SelectionKey.OP_WRITE, byteBuffer)); //Marks the client as ready
        selector.wakeup(); //Wakes up the selector to re-register the key
    }

    /**
     * Reads a request from the SocketChannel
     * @return the request string or null otherwise
     */
    private String readRequest() {
        try {
            byteBuffer.clear(); //Prepares for writing to the buffer
            int byteRead = client.read(byteBuffer); //Writes to the buffer
            byteBuffer.flip(); //Prepares the buffer to be read

            //Checks if the client sent the termination signal
            if (byteRead == -1) throw new IOException("Client disconnected");

            int requestLen = byteBuffer.getInt(); //Reads the request length
            byte[] requestBytes = new byte[requestLen];
            byteBuffer.get(requestBytes); //Reads the request in bytes
            return new String(requestBytes); //Creates the request string
        } catch (IOException e) {
            try {client.close();} catch (Exception ignored) {} //TODO Deve inviare comunque la risposta al client?
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
     * Sets the response code on the buffer and the json array as result
     * @param code Response code
     * @param response response to send to the client
     */
    private void setResponse(int code, JsonArray response) {
        byteBuffer.clear();
        byteBuffer.putInt(code);
        byteBuffer.putInt(response.toString().length());
        byteBuffer.put(response.toString().getBytes());
    }

    /**
     * Sets the response code on the buffer and the json element as result
     * @param code Response code
     * @param response response to send to the client
     */
    private void setResponse(int code, JsonElement response) {
        byteBuffer.clear();
        byteBuffer.putInt(code);
        byteBuffer.putInt(response.toString().length());
        byteBuffer.put(response.toString().getBytes());
    }

    private void login(String username, String password) {
        try {
            if (loggedUsers.containsKey(username)) {
                setResponse(2);
                return;
            }
            User user = users.get(username);
            if (user != null) {
                if (user.comparePassword(Hash.bytesToHex(Hash.sha256(username + password)))) {
                    loggedUsers.put(username, client.socket());
                    JsonElement multicastReferences = JsonParser.parseString("{\"multicastIP\": \"" + ServerMain.multicastIP + "\", \"multicastPort\": " + ServerMain.multicastPort + "}");
                    setResponse(0, multicastReferences);
                } else
                    setResponse(1);
            } else {
                setResponse(1);
            }
        } catch (NoSuchAlgorithmException e) {
            System.err.println("Error while hashing the password: " + e.getMessage());
            try {client.close();} catch (Exception ignored) {} //TODO Deve inviare comunque la risposta al client?
        }
    }

    private void logout(String username) {
        if (loggedUsers.remove(username) != null)
            setResponse(0);
        else
            setResponse(1);
    }

    private void listUsers(String username) {
        if (!loggedUsers.containsKey(username)) {
            setResponse(1);
            return;
        }

        boolean firstEntry = true;
        Gson gson = new Gson();
        JsonArray jsonResponse = new JsonArray(); //TODO Probabilmente andrebbero inviati uno alla volta
        User user = users.get(username);
        for (User u : users.values()) {
            if (user != u) {
                String []commonTags = user.getCommonTags(u);
                if (commonTags.length == 0) continue;

                jsonResponse.add(JsonParser.parseString("{\"username\": \"" + u.getUsername() + "\", \"tags\": " + gson.toJson(commonTags) + "}"));
            }
        }

        setResponse(0, jsonResponse);
    }

    private void followUser(String username, String userToFollow) {
        if (!loggedUsers.containsKey(username)) {
            setResponse(1);
            return;
        }

        User userToFollowObj = users.get(userToFollow);
        if (userToFollowObj == null) {
            setResponse(2);
            return;
        }

        try {
            if (users.get(username).addFollowed(userToFollow) && userToFollowObj.addFollower(username)) {
                stateChanged.set(true);
                setResponse(0);
                callbackHandler.notifyNewFollower(userToFollow, username);
            } else
                setResponse(3);
        } catch (RemoteException e) {
            System.err.println("Error while notifying the new follower: " + e.getMessage());
            try {client.close();} catch (Exception ignored) {} //TODO Deve inviare comunque la risposta al client?
        }
    }

    private void unfollowUser(String username, String userToUnfollow) {
        if (!loggedUsers.containsKey(username)) {
            setResponse(1);
            return;
        }

        User userToUnfollowObj = users.get(userToUnfollow);
        if (userToUnfollowObj == null) {
            setResponse(2);
            return;
        }

        try {
            if (users.get(username).removeFollowed(userToUnfollow) && userToUnfollowObj.removeFollower(username)) {
                stateChanged.set(true);
                setResponse(0);
                callbackHandler.notifyLostFollower(userToUnfollow, username);
            } else
                setResponse(3);
        } catch (RemoteException e) {
            System.err.println("Error while notifying the new follower: " + e.getMessage());
            try {client.close();} catch (Exception ignored) {} //TODO Deve inviare comunque la risposta al client?
        }
    }

    private void ratePost(String username, int idPost, String vote) {
        if (!loggedUsers.containsKey(username)) {
            setResponse(1);
            return;
        }
        if (!vote.equals("+1") && !vote.equals("-1")) {
            setResponse(2);
            return;
        }

        Post post = posts.get(idPost);
        if (post == null) {
            setResponse(3);
            return;
        }

        if (post.getAuthor().equals(username)) {
            setResponse(4);
            return;
        }

        if (post.containsUpvote(username) || post.containsDownvote(username)) {
            setResponse(5);
            return;
        }

        if (vote.equals("+1")) post.addUpvote(username);
        if (vote.equals("-1")) post.addDownvote(username);
        stateChanged.set(true);
        setResponse(0);
    }

    private void createPost(String request) {
        String username = request.substring(request.lastIndexOf(" ") + 1);
        int openingQuoteIndex = request.indexOf("\"");
        int closingQuoteIndex = request.indexOf("\"", openingQuoteIndex + 1);

        String title = request.substring(openingQuoteIndex + 1, closingQuoteIndex);
        openingQuoteIndex = request.indexOf("\"", closingQuoteIndex + 1);
        closingQuoteIndex = request.indexOf("\"", openingQuoteIndex + 1);

        String content = request.substring(openingQuoteIndex + 1, closingQuoteIndex);

        if (!loggedUsers.containsKey(username)) {
            setResponse(1);
            return;
        }

        if (title.length() == 0 || title.length() > 20 || content.length() == 0 || content.length() > 500) {
            setResponse(2);
            return;
        }

        Post newPost = new Post(username, title, content);
        posts.put(newPost.getIdPost(), newPost);
        User user = users.get(username);
        user.addPost(newPost.getIdPost());

        stateChanged.set(true);
        setResponse(0);
    }

    private void deletePost(String username, int idPost) {
        if (!loggedUsers.containsKey(username)) {
            setResponse(1);
            return;
        }

        Post post = posts.get(idPost);
        if (post == null) {
            setResponse(2);
            return;
        }

        User user = users.get(username);
        if (!user.ownsPost(idPost)) {
            setResponse(3);
            return;
        }

        posts.remove(idPost);
        user.removePost(idPost);
        for (String rewinner : post.getRewinner()) {
            User rewinnerUser = users.get(rewinner);
            rewinnerUser.removePost(idPost);
        }
        stateChanged.set(true);
        setResponse(0);
    }

    private void listFollowing(String username) {
        if (!loggedUsers.containsKey(username)) {
            setResponse(1);
            return;
        }

        User user = users.get(username);
        JsonArray jsonResponse = new JsonArray();
        Gson gson = new Gson();
        for (String u : user.getFollowing()) {
            jsonResponse.add(JsonParser.parseString("{\"username\": \"" + u + "\", \"tags\": " + gson.toJson(user.getCommonTags(users.get(u))) + "}"));
        }

        setResponse(0, jsonResponse);
    }

    private void viewBlog(String username) {
        if (!loggedUsers.containsKey(username)) {
            setResponse(1);
            return;
        }

        User user = users.get(username);
        JsonArray jsonResponse = new JsonArray();
        for (int postId : user.getBlog()) {
            Post post = posts.get(postId);

            if (post.getAuthor().equals(username))
                jsonResponse.add(JsonParser.parseString(post.basicInfoToJson()));
        }

        setResponse(0, jsonResponse);
    }

    private void showPost(int idPost, String username) {
        if (!loggedUsers.containsKey(username)) {
            setResponse(1);
            return;
        }

        Post post = posts.get(idPost);
        if (post == null) {
            setResponse(2);
            return;
        }

        setResponse(0, JsonParser.parseString(post.toJson()));
    }

    private void showFeed(String username) {
        if (!loggedUsers.containsKey(username)) {
            setResponse(1);
            return;
        }

        User user = users.get(username);
        JsonArray jsonResponse = new JsonArray();
        for (String usernameFollowed : user.getFollowed()) {
            User userFollowed = users.get(usernameFollowed);
            for (int postId : userFollowed.getBlog()) {
                Post post = posts.get(postId);
                jsonResponse.add(JsonParser.parseString(post.basicInfoToJson()));
            }
        }

        setResponse(0, jsonResponse);
    }

    private void rewinPost(int idPost, String username) {
        if (!loggedUsers.containsKey(username)) {
            setResponse(1);
            return;
        }

        Post post = posts.get(idPost);
        if (post == null) {
            setResponse(2);
            return;
        }

        User user = users.get(username);
        if (user.addPost(post.getIdPost())) {
            post.addRewinner(username);
            stateChanged.set(true);
            setResponse(0);
        } else
            setResponse(3);
    }

    private void addComment(int idPost, String comment, String username) {
        if (!loggedUsers.containsKey(username)) {
            setResponse(1);
            return;
        }

        if (comment.equals("")) {
            setResponse(2);
            return;
        }

        Post post = posts.get(idPost);
        if (post == null) {
            setResponse(3);
            return;
        }

        if (!users.get(username).follows(post.getAuthor())) {
            setResponse(4);
            return;
        }

        if (post.getAuthor().equals(username)) {
            setResponse(5);
            return;
        }

        post.addComment(new Comment(username, comment));
        stateChanged.set(true);
        setResponse(0);
    }

    private void getWallet(String username) {
        if (!loggedUsers.containsKey(username)) {
            setResponse(1);
            return;
        }

        User user = users.get(username);
        setResponse(0, JsonParser.parseString(user.getWalletAsJson()));
    }

    private void getWalletBTC(String username) {
        if (!loggedUsers.containsKey(username)) {
            setResponse(1);
            return;
        }

        User user = users.get(username);
        String response = "{\"wincoinBTC\":" + user.getWallet().wincoinToBTC() + ", \"transactions\": [";
        boolean firstEntry = true;

        for (Transaction t : user.getWallet().getTransactions()) {
            if (!firstEntry) response += ",";
            response += t.toJson();

            firstEntry = false;
        }
        response += "]}";

        setResponse(0, JsonParser.parseString(response));
    }

    private void sendFollowers(String username) {
        User user = users.get(username);

        setResponse(0, JsonParser.parseString(user.getFollowersAsJson()));
    }
}
