import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.security.NoSuchAlgorithmException;
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
        }

        readyToBeRegistered.add(new Registable(client, SelectionKey.OP_WRITE, byteBuffer));
        selector.wakeup();
    }

    private void setResponse(int code) {
        byteBuffer.clear();
        byteBuffer.putInt(code);
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
}
