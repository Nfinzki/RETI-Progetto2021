import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Set;

public class WriterWorker implements Runnable {
    private final SelectionKey key;
    private final SocketChannel client;
    private final ByteBuffer byteBuffer;

    private final Set<Registable> readyToBeRegistered;

    private final Selector selector;

    public WriterWorker(SelectionKey key, Set<Registable> readyToBeRegistered, Selector selector) {
        this.key = key;
        this.readyToBeRegistered = readyToBeRegistered;
        this.selector = selector;

        this.client = (SocketChannel) key.channel();
        this.byteBuffer = (ByteBuffer) key.attachment();
    }

    public void run() {
        sendResponse();
        readyToBeRegistered.add(new Registable(client, SelectionKey.OP_READ, byteBuffer));
        selector.wakeup();
    }

    private void sendResponse() {
        try {
            byteBuffer.flip();
            while(byteBuffer.hasRemaining()) client.write(byteBuffer);
        } catch (IOException e) {
            System.err.println("Error sending response to the client: " + e.getMessage());
            try {key.channel().close();} catch (Exception ignored) {} //TODO Deve inviare comunque la risposta al client?
        }
    }
}
