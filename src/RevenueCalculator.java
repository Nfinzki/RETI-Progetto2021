import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class RevenueCalculator implements Runnable{ //TODO Aggiungere multicast
    private final Map<String, User> users;
    private final Map<Integer, Post> posts;
    private final int calculationTime;
    private final int authorPercentage;
    private final Map<Integer, Integer> postIteration;

    public RevenueCalculator(Map<String, User> users, Map<Integer, Post> posts, int calculationTime, int authorPercentage) {
        if (authorPercentage < 0 || authorPercentage > 100) throw new IllegalArgumentException("authorPercentage is not a percentage");
        this.users = users;
        this.posts = posts;
        this.calculationTime = calculationTime;
        this.authorPercentage = authorPercentage;

        this.postIteration = new HashMap<>();
    }

    public void run() {
        while (true) {
            System.out.println("Calculating revenue");
            for (Post post : posts.values()) {
                double gain = 0;
                Set<String> commenterUsernames = post.getRecentCommenters();
                synchronized (post) {
                    Integer iterations = postIteration.get(post.getIdPost());
                    if (iterations == null) iterations = 1;

                    int upvotes = post.getRecentUpvotesAndReset();
                    int downvotes = post.getRecentDownvotesAndReset();
                    gain = (Math.log(Math.max(upvotes - downvotes, 0) + 1) + Math.log(getSecondLogArg(post, commenterUsernames) + 1)) / iterations;

                    postIteration.put(post.getIdPost(), iterations + 1);
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
            }

            try {
                Thread.sleep(calculationTime);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private double getSecondLogArg(Post post, Set<String> commenterUsernames) {
        double result = 0;
        for (String user : commenterUsernames) {
            result += 2 / (1 + Math.exp(-(post.getNumberOfComments(user)) - 1));
        }

        return result;
    }
}
