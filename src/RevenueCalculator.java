import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class RevenueCalculator implements Runnable{
    private final Map<String, User> users;
    private final Map<Integer, Post> posts;
    private final int calculationTime;
    private final int authorPercentage;
    private final String multicastIP;
    private final int multicastPort;
    private final AtomicBoolean stateChanged;

    public RevenueCalculator(Map<String, User> users, Map<Integer, Post> posts, int calculationTime, int authorPercentage, String multicastIP, int multicastPort, AtomicBoolean stateChanged) {
        if (authorPercentage < 0 || authorPercentage > 100) throw new IllegalArgumentException("authorPercentage is not a percentage");
        this.users = users;
        this.posts = posts;
        this.calculationTime = calculationTime;
        this.authorPercentage = authorPercentage;
        this.multicastIP = multicastIP;
        this.multicastPort = multicastPort;
        this.stateChanged = stateChanged;
    }

    public void run() {
        try (MulticastSocket multicastGroup = new MulticastSocket()) {
            InetAddress address = InetAddress.getByName(multicastIP);

            while (!Thread.currentThread().isInterrupted()) {
                System.out.println("Calculating revenue");
                for (Post post : posts.values()) {
                    double gain = 0;
                    Set<String> commenterUsernames = post.getRecentCommenters();
                    synchronized (post) {
                        int iterations = post.getRevenueIteration();

                        int upvotes = post.getRecentUpvotesAndReset();
                        int downvotes = post.getRecentDownvotesAndReset();
                        gain = (Math.log(Math.max(upvotes - downvotes, 0) + 1) + Math.log(getSecondLogArg(post, commenterUsernames) + 1)) / iterations;

                        post.incrementRevenueIteration();
                        stateChanged.set(true);
                    }

                    double authorGain = (gain * authorPercentage) / 100;
                    double commentersGain = (gain - authorGain);
                    double singleCommenterGain = commentersGain / commenterUsernames.size();

                    if (authorGain == 0.0 && commentersGain == 0.0) continue;

                    users.get(post.getAuthor()).getWallet().addWincoin(authorGain);
                    System.out.println("Author " + post.getAuthor() + " got " + authorGain);
                    for (String user : commenterUsernames) {
                        users.get(user).getWallet().addWincoin(singleCommenterGain);
                        System.out.println("Commenter " + user + " got " + singleCommenterGain);
                    }
                    stateChanged.set(true);
                }

                byte []notifyRevenueCalculation = "Reward calculated".getBytes();
                DatagramPacket packet = new DatagramPacket(notifyRevenueCalculation, notifyRevenueCalculation.length, address, multicastPort);
                multicastGroup.send(packet);

                Thread.sleep(calculationTime);
            }
        } catch (IOException e) {
            System.err.println("Error while notifying that the revenue is calculated (" + e.getMessage() + ")");
        } catch (InterruptedException ignored) {}
    }

    private double getSecondLogArg(Post post, Set<String> commenterUsernames) {
        double result = 0;
        for (String user : commenterUsernames) {
            result += 2 / (1 + Math.exp(-(post.getNumberOfComments(user)) - 1));
        }

        return result;
    }
}
