import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

public class ReaderWorker implements Runnable {
    private final SelectionKey key;
    private final SocketChannel client;
    private final ByteBuffer byteBuffer;

    private final Map<String, User> users;
    private final Map<Integer, Post> posts;
    private final Map<String, Socket> loggedUsers;
    private final Set<Registable> readyToBeRegistered;

    private final Selector selector;

    public ReaderWorker(SelectionKey key, Map<String, User> users, Map<Integer, Post> posts, Map<String, Socket> loggedUsers, Set<Registable> readyToBeRegistered, Selector selector) {
        this.key = key;
        this.users = users;
        this.posts = posts;
        this.loggedUsers = loggedUsers;
        this.readyToBeRegistered = readyToBeRegistered;
        this.selector = selector;

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

                if (args[1].equals("users")) listUsers(args[2]);
            }
            case "post" -> createPost(request);
        }

        readyToBeRegistered.add(new Registable(client, SelectionKey.OP_WRITE, byteBuffer));
        selector.wakeup();
    }

    private void setResponse(int code) {
        byteBuffer.clear();
        byteBuffer.putInt(code);
    }

    private void setResponse(int code, String response) {
        byteBuffer.clear();
        byteBuffer.putInt(code);
        byteBuffer.putInt(response.length());
        byteBuffer.put(response.getBytes());
    }

    private String readRequest() {
        try {
            byteBuffer.clear(); //Predispone la scrittura sul buffer
            client.read(byteBuffer); //Scrive dati sul buffer
            byteBuffer.flip();

            if (byteBuffer.limit() == 0) return null; //TODO Rivedere questa cosa

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
                if (user.comparePassword(Hash.bytesToHex(Hash.sha256(password)))) {
                    loggedUsers.put(username, client.socket());
                    setResponse(0);
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

        StringBuilder response = new StringBuilder(); //TODO Da qui
        User user = users.get(username);
        for (User u : users.values()) {
            if (user != u) {
                String []commonTags = user.getCommonTags(u);
                if (commonTags.length == 0) continue;

                response.append(u.getUsername() + " " + Arrays.toString(commonTags) + " \n");
            }
        }

        setResponse(0, response.toString());
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

        User user = users.get(username);
        Post newPost = new Post(user.getId(), title, content);
        posts.put(newPost.getIdPost(), newPost);
        user.addPost(newPost.getIdPost());

        setResponse(0);
    }
}
