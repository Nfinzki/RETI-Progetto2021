import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

public class NotifyHandler implements Runnable {
    private final String multicastIP;
    private final MulticastSocket multicastSocket;

    public NotifyHandler(String multicastIP, MulticastSocket multicastSocket) {
        this.multicastIP = multicastIP;
        this.multicastSocket = multicastSocket;
    }

    public void run() {
        try {
            InetAddress address = InetAddress.getByName(multicastIP);
            if (!address.isMulticastAddress())
                throw new IllegalArgumentException("Not a multicast address: " + multicastIP);
            multicastSocket.joinGroup(address);
        } catch (IOException e) {
            System.err.println("Error while joining the multicast group: (" + e.getMessage() + ")");
        }

        try {
            int bufferSize = 512;
            byte[] messageBuffer = new byte[bufferSize];
            DatagramPacket packet = new DatagramPacket(messageBuffer, messageBuffer.length);
            while (!Thread.currentThread().isInterrupted()) {
                multicastSocket.receive(packet);
                String notification = new String(packet.getData(), 0, packet.getLength());

                if (!Thread.currentThread().isInterrupted())
                    System.out.print("\n< " + notification + "\n> ");
            }
        } catch (IOException ignored) {}
    }
}
