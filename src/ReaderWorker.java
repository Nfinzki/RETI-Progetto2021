import com.google.gson.Gson;

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
        String request = readRequest();
        if (request == null) return;

        String[] args = request.split(" ");

        switch (args[0]) {
            case "login" -> login(args[1], args[2]);
            case "logout" -> logout(args[1]);
            case "list" -> {
                if (args.length != 3) {
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

                setResponse(-1);
            }
            case "post" -> createPost(request);
            case "delete" -> deletePost(args[2], Integer.parseInt(args[1]));
            case "follow" -> followUser(args[2], args[1]);
            case "unfollow" -> unfollowUser(args[2], args[1]);
            case "rate" -> ratePost(args[3], Integer.parseInt(args[1]), args[2]);
            case "blog" -> viewBlog(args[1]);
            case "show" -> {
                if (args[1].equals("post") && args.length == 4) {
                    showPost(Integer.parseInt(args[2]), args[3]);
                    break;
                }

                if (args[1].equals("feed") && args.length == 3) {
                    showFeed(args[2]);
                    break;
                }

                setResponse(-1);
            }
            case "rewin" -> rewinPost(Integer.parseInt(args[1]), args[2]);
            case "comment" -> {
                String comment = request.substring(request.indexOf("\"") + 1, request.lastIndexOf("\""));
                String username = request.substring(request.lastIndexOf("\"") + 2);
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

                setResponse(-1);
            }
            case "getFollowers" -> sendFollowers(args[1]);
        }

        readyToBeRegistered.add(new Registable(client, SelectionKey.OP_WRITE, byteBuffer)); //TODO Cosa succede poi nel main se c'è stata la client.close() ?
        selector.wakeup();
    }

    private void setResponse(int code) {
        byteBuffer.clear();
        byteBuffer.putInt(code);
    }

    private void setResponse(int code, String response) {
        byteBuffer.clear();
        byteBuffer.putInt(code);
        byteBuffer.putInt(response.getBytes().length);
        byteBuffer.put(response.getBytes());
    }

    private String readRequest() {
        try {
            byteBuffer.clear(); //Predispone la scrittura sul buffer
            int byteRead = client.read(byteBuffer); //Scrive dati sul buffer
            byteBuffer.flip();

            //Checks if the client sent the termination signal
            if (byteRead == -1) throw new IOException("CLient disconnected");

            int requestLen = byteBuffer.getInt();
            byte[] requestBytes = new byte[requestLen];
            byteBuffer.get(requestBytes);
            return new String(requestBytes);
        } catch (IOException e) {
            try {client.close();} catch (Exception ignored) {} //TODO Deve inviare comunque la risposta al client?
        }

        return null;
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
                    String multicastReferences = "{\"multicastIP\": \"" + ServerMain.multicastIP + "\", \"multicastPort\": " + ServerMain.multicastPort + "}";
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
        StringBuilder response = new StringBuilder("["); //TODO Probabilmente andrebbero inviati uno alla volta
        User user = users.get(username);
        for (User u : users.values()) {
            if (user != u) {
                String []commonTags = user.getCommonTags(u);
                if (commonTags.length == 0) continue;

                if (!firstEntry) response.append(", ");
                response.append("{\"username\": \"" + u.getUsername() + "\", \"tags\": " + gson.toJson(commonTags) + "}");
                firstEntry = false;
            }
        }
        response.append("]");

        setResponse(0, response.toString());
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
        String response = "[";
        boolean firstEntry = true;
        Gson gson = new Gson();
        for (String u : user.getFollowing()) {
            if (!firstEntry) response += ",";

            response += "{\"username\": \"" + u + "\", \"tags\": " + gson.toJson(user.getCommonTags(users.get(u))) + "}";
            firstEntry = false;
        }
        response += "]";

        setResponse(0, response);
    }

    private void viewBlog(String username) {
        if (!loggedUsers.containsKey(username)) {
            setResponse(1);
            return;
        }

        User user = users.get(username);
        boolean firstEntry = true;
        String response = "[";

        for (int postId : user.getBlog()) {
            if (!firstEntry) response += ", ";
            Post post = posts.get(postId);

            if (post.getAuthor().equals(username))
                response += post.basicInfoToJson();

            firstEntry = false;
        }
        response += "]";

        setResponse(0, response);
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

        setResponse(0, post.toJson());
    }

    private void showFeed(String username) {
        if (!loggedUsers.containsKey(username)) {
            setResponse(1);
            return;
        }

        User user = users.get(username);
        boolean firstEntry = true;
        String response = "[";
        for (String usernameFollowed : user.getFollowed()) {
            User userFollowed = users.get(usernameFollowed);
            for (int postId : userFollowed.getBlog()) {
                if (!firstEntry) response += ", ";
                Post post = posts.get(postId);
                response += post.basicInfoToJson();
                firstEntry = false;
            }
        }
        response += "]";

        setResponse(0, response);
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
        setResponse(0, user.getWalletAsJson());
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

        setResponse(0, response);
    }

    private void sendFollowers(String username) {
        User user = users.get(username);

        setResponse(0, user.getFollowersAsJson());
    }
}
