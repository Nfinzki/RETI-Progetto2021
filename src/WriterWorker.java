/**
 * This class implements a task that writes the responses to the client
 */

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
        //Needs to send only a response code which was
        //previously set in the ReaderWorker
        if (jsonElement == null) sendResponse();

        //Needs to also send a json response
        if (jsonElement != null) {
            //Gets the json element as byte array
            byte []jsonElementBytes = jsonElement.toString().getBytes();
            //Writes the byte array length to the buffer
            byteBuffer.putInt(jsonElementBytes.length);

            //Calculates the remaining buffer capacity
            int bufferCapacity = byteBuffer.capacity() - byteBuffer.position();

            if (jsonElementBytes.length <= bufferCapacity) { //The json response fits all in the buffer
                //Writes the bytes to the buffer
                byteBuffer.put(jsonElementBytes);
                //Sends the response
                sendResponse();
            } else { //The json response needs to be sent fragmented
                int startingIndex = 0;

                while (startingIndex < jsonElementBytes.length) {
                    //Writes the maximum bytes that fits in the buffer
                    byteBuffer.put(jsonElementBytes, startingIndex, bufferCapacity);
                    //Sends bytes to the client
                    sendResponse();
                    //Clears the buffer
                    byteBuffer.clear();

                    //Calculates the next start index to write
                    startingIndex += bufferCapacity;

                    //Calculates how many bytes have to write
                    if (bufferCapacity < jsonElementBytes.length - startingIndex)
                        //Needs to fill the whole buffer
                        bufferCapacity = byteBuffer.capacity();
                    else //Needs to write only a part of the buffer
                        bufferCapacity = jsonElementBytes.length - startingIndex;
                }

            }
        }

        //Marks the client as ready
        readyToBeRegistered.add(new Registable(client, SelectionKey.OP_READ, byteBuffer, null));
        //Wakes up the selector to re-register the key
        selector.wakeup();
    }

    /**
     * Writes the bytes from the buffer to the channel
     */
    private void sendResponse() {
        try {
            //Prepares the buffer to be read
            byteBuffer.flip();
            //Writes all the bytes in the buffer to the channel
            while(byteBuffer.hasRemaining()) client.write(byteBuffer);
        } catch (IOException e) {
            System.err.println("Error sending response to the client: " + e.getMessage());
            try {key.channel().close();} catch (Exception ignored) {}
        }
    }
}
