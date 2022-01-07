import com.google.gson.JsonElement;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class Registable {
    private final SocketChannel clientChannel;
    private final int operation;
    private final ByteBuffer byteBuffer;
    private final JsonElement jsonElement;

    public Registable(SocketChannel clientChannel, int operation, ByteBuffer byteBuffer, JsonElement jsonElement) {
        this.clientChannel = clientChannel;
        this.operation = operation;
        this.byteBuffer = byteBuffer;
        this.jsonElement = jsonElement;
    }

    public SocketChannel getClientChannel() {
        return clientChannel;
    }

    public int getOperation() {
        return operation;
    }

    public ByteBuffer getByteBuffer() {
        return byteBuffer;
    }

    public JsonElement getJsonElement() {
        return jsonElement;
    }
}
