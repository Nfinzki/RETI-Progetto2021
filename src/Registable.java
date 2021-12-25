import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class Registable {
    private final SocketChannel clientChannel;
    private final int operation;
    private final ByteBuffer byteBuffer;

    public Registable(SocketChannel clientChannel, int operation, ByteBuffer byteBuffer) {
        this.clientChannel = clientChannel;
        this.operation = operation;
        this.byteBuffer = byteBuffer;
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
}
