/**
 * This class implements a task to handle the notification from a multicast group
 */

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
            //Gets the address
            InetAddress address = InetAddress.getByName(multicastIP);
            //Checks if the address is a multicast address
            if (!address.isMulticastAddress())
                throw new IllegalArgumentException("Not a multicast address: " + multicastIP);
            //Joins the multicast group
            multicastSocket.joinGroup(address);
        } catch (IOException e) {
            System.err.println("Error while joining the multicast group: (" + e.getMessage() + ")");
        }

        try {
            //Initializes the DatagramPacket to receive the message
            int bufferSize = 512;
            byte[] messageBuffer = new byte[bufferSize];
            DatagramPacket packet = new DatagramPacket(messageBuffer, messageBuffer.length);

            while (!Thread.currentThread().isInterrupted()) {
                //Waits for the message
                multicastSocket.receive(packet);
                //Extracts the message from the DatagramPacket
                String notification = new String(packet.getData(), 0, packet.getLength());

                //Prints the message received
                if (!Thread.currentThread().isInterrupted())
                    System.out.print("\n< " + notification + "\n> ");
            }
        } catch (IOException ignored) {}
    }
}
