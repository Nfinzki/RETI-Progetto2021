/**
 * This is a utility class to mark client as ready
 * to be re-registered
 */

import com.google.gson.JsonElement;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class Registrable {
    private final SocketChannel clientChannel;
    private final int operation;
    private final ByteBuffer byteBuffer;
    private final JsonElement jsonElement;

    public Registrable(SocketChannel clientChannel, int operation, ByteBuffer byteBuffer, JsonElement jsonElement) {
        this.clientChannel = clientChannel;
        this.operation = operation;
        this.byteBuffer = byteBuffer;
        this.jsonElement = jsonElement;
    }

    /**
     * @return socketchannel
     */
    public SocketChannel getClientChannel() {
        return clientChannel;
    }

    /**
     * @return operation
     */
    public int getOperation() {
        return operation;
    }

    /**
     * @return buffer
     */
    public ByteBuffer getByteBuffer() {
        return byteBuffer;
    }

    /**
     * @return JsonElement
     */
    public JsonElement getJsonElement() {
        return jsonElement;
    }
}
