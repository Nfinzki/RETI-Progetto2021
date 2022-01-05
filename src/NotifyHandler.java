import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

public class NotifyHandler implements Runnable {
    private final String multicastIP;
    private final int multicastPort;

    public NotifyHandler(String multicastIP, int multicastPort) {
        this.multicastIP = multicastIP;
        this.multicastPort = multicastPort;
    }

    public void run() {
        try (MulticastSocket multicastSocket = new MulticastSocket(multicastPort)) {
            InetAddress address = InetAddress.getByName(multicastIP);
            if (!address.isMulticastAddress()) throw new IllegalArgumentException("Not a multicast address: " + multicastIP);

            multicastSocket.joinGroup(address);

            int bufferSize = 512;
            byte []messaggeBuffer = new byte[bufferSize];
            DatagramPacket packet = new DatagramPacket(messaggeBuffer, messaggeBuffer.length);

            while (!Thread.currentThread().isInterrupted()) {
                multicastSocket.receive(packet);
                String notification = new String(packet.getData(), 0, packet.getLength());

                if (!Thread.currentThread().isInterrupted())
                    System.out.print("\n< " + notification + "\n> ");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
