import com.google.gson.JsonElement;

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

    private final JsonElement jsonElement;

    public WriterWorker(SelectionKey key, Set<Registable> readyToBeRegistered, Selector selector, JsonElement jsonElement) {
        this.key = key;
        this.readyToBeRegistered = readyToBeRegistered;
        this.selector = selector;
        this.jsonElement = jsonElement;

        this.client = (SocketChannel) key.channel();
        this.byteBuffer = (ByteBuffer) key.attachment();
    }

    public void run() {
        if (jsonElement == null) sendResponse();

        if (jsonElement != null) {
            byte []jsonElementBytes = jsonElement.toString().getBytes();
            byteBuffer.putInt(jsonElementBytes.length);

            int bufferCapacity = byteBuffer.capacity() - byteBuffer.position();

            if (jsonElementBytes.length <= bufferCapacity) {
                byteBuffer.put(jsonElementBytes);
                sendResponse();
            } else {
                int startingIndex = 0;

                while (startingIndex < jsonElementBytes.length) {
                    byteBuffer.put(jsonElementBytes, startingIndex, bufferCapacity);
                    sendResponse();
                    byteBuffer.clear();

                    startingIndex += bufferCapacity;
                    if (bufferCapacity < jsonElementBytes.length - startingIndex)
                        bufferCapacity = byteBuffer.capacity();
                    else
                        bufferCapacity = jsonElementBytes.length - startingIndex;
                }

            }
        }

        readyToBeRegistered.add(new Registable(client, SelectionKey.OP_READ, byteBuffer, null));
        selector.wakeup();
    }

    private void sendResponse() {
        try {
            byteBuffer.flip();
            while(byteBuffer.hasRemaining()) client.write(byteBuffer);
        } catch (IOException e) {
            System.err.println("Error sending response to the client: " + e.getMessage());
            try {key.channel().close();} catch (Exception ignored) {}
        }
    }
}
