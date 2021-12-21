import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.NonReadableChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Set;

public class ThreadWorker implements Runnable{
    private final SelectionKey key;
    private final SocketChannel client;
    private final ByteBuffer byteBuffer;

    private final Map<String, User> users;
    private final Map<Integer, Post> posts;
    private final Set<Registable> readyToBeRegistered;

    private final int operation;
    private final Selector selector;

    public ThreadWorker(SelectionKey key, Map<String, User> users, Map<Integer, Post> posts, Set<Registable> readyToBeRegistered, int operation, Selector selector) {
        this.key = key;
        this.users = users;
        this.posts = posts;
        this.readyToBeRegistered = readyToBeRegistered;
        this.operation = operation;
        this.selector = selector;

        this.client = (SocketChannel) key.channel();
        this.byteBuffer = (ByteBuffer) key.attachment();
    }

    public void run() {
        if (operation == SelectionKey.OP_READ) {
            String request = readRequest();
            if (request == null) return;

            String[] args = request.split(" ");

            switch (args[0]) {
                case "login" -> login(args[1], args[2]);
            }

            readyToBeRegistered.add(new Registable(client, SelectionKey.OP_WRITE, byteBuffer));
            selector.wakeup();
        } else {
            sendResponse();
            readyToBeRegistered.add(new Registable(client, SelectionKey.OP_READ, byteBuffer));
            selector.wakeup();
        }
    }

    private void setResponse(int code) {
        byteBuffer.clear();
        byteBuffer.putInt(code);
    }

    private void sendResponse() {
        try {
            byteBuffer.flip();
            client.write(byteBuffer);
        } catch (IOException e) {
            System.err.println("Error sending response to the client: " + e.getMessage());
            try {key.channel().close();} catch (Exception ignored) {} //TODO Deve inviare comunque la risposta al client?
        }
    }

    private String readRequest() {
        try {
            byteBuffer.clear(); //Predispone la scrittura sul buffer
            client.read(byteBuffer); //Scrive dati sul buffer
            byteBuffer.flip();

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
            User user = users.get(username);
            if (user != null) {
                if (user.comparePassword(Hash.bytesToHex(Hash.sha256(password))))
                    setResponse(0);
                else
                    setResponse(1);
            } else {
                setResponse(1);
            }
        } catch (NoSuchAlgorithmException e) {
            System.err.println("Error while hashing the password: " + e.getMessage());
            try {client.close();} catch (Exception ignored) {} //TODO Deve inviare comunque la risposta al client?
        }
    }
}
