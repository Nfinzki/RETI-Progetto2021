/**
 * This class implements a task that calculates the revenue for each post
 */

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class RevenueCalculator implements Runnable{
    private final Map<String, User> users;
    private final Map<Integer, Post> posts;
    private final int calculationTime;
    private final double authorPercentage;
    private final String multicastIP;
    private final int multicastPort;
    private final AtomicBoolean stateChanged;

    public RevenueCalculator(Map<String, User> users, Map<Integer, Post> posts, int calculationTime, double authorPercentage, String multicastIP, int multicastPort, AtomicBoolean stateChanged) {
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
        try (MulticastSocket multicastGroup = new MulticastSocket()) { //Opens the multicast socket
            //Gets the address
            InetAddress address = InetAddress.getByName(multicastIP);

            while (!Thread.currentThread().isInterrupted()) {
                System.out.println("Calculating revenue");
                for (Post post : posts.values()) {
                    double gain = 0;
                    //Gets recent commenters
                    Set<String> commenterUsernames = post.getRecentCommenters();

                    //Gets the current post iteration
                    int iterations = post.getRevenueIteration();

                    //Gets recent upvotes and recent downvotes
                    int upvotes = post.getRecentUpvotesAndReset();
                    int downvotes = post.getRecentDownvotesAndReset();

                    //Calculates the revenue of the post
                    gain = (Math.log(Math.max(upvotes - downvotes, 0) + 1) + Math.log(getSecondLogArg(post, commenterUsernames) + 1)) / iterations;

                    //Increments the post iteration
                    post.incrementRevenueIteration();
                    //State of the server changed
                    stateChanged.set(true);

                    //Calculates the revenue of the author
                    double authorGain = (gain * authorPercentage) / 100;
                    //Calculates the revenue of the commenters
                    double commentersGain = (gain - authorGain);
                    //Calculates the revenue of the commenter
                    double singleCommenterGain = commentersGain / commenterUsernames.size();

                    //If no one gained wincoin, skip to the next post
                    if (authorGain == 0.0 && commentersGain == 0.0) continue;

                    //Adds the gain to the author
                    users.get(post.getAuthor()).getWallet().addWincoin(authorGain);
                    //Adds the gain to each commenter
                    for (String user : commenterUsernames) {
                        users.get(user).getWallet().addWincoin(singleCommenterGain);
                    }
                    stateChanged.set(true); //State of the server changed
                }

                //Creates the message to send to the multicast group
                byte []notifyRevenueCalculation = "Reward calculated".getBytes();
                DatagramPacket packet = new DatagramPacket(notifyRevenueCalculation, notifyRevenueCalculation.length, address, multicastPort);
                //Sends the message
                multicastGroup.send(packet);

                //Waits until next iteration
                Thread.sleep(calculationTime);
            }
        } catch (IOException e) {
            System.err.println("Error while notifying that the revenue is calculated (" + e.getMessage() + ")");
        } catch (InterruptedException ignored) {}
    }

    /**
     * Calculates the second argument of the second log
     * @param post the post that is being analyzed
     * @param commenterUsernames list of users that commented the post
     * @return result of the argument of the second log
     */
    private double getSecondLogArg(Post post, Set<String> commenterUsernames) {
        double result = 0;
        for (String user : commenterUsernames) {
            result += 2 / (1 + Math.exp(-(post.getNumberOfComments(user)) - 1));
        }

        return result;
    }
}
