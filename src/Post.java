import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Post {
    private static int nextIdPost = 0;
    private final int idPost;
    private final String author;
    private final String postTitle;
    private final String postContent;
    private final Set<Comment> comments;
    private final Set<String> upvotes;
    private final Set<String> downvotes;
    private final Map<String, Integer> commentsStats;
    private transient final Set<String> recentUpvotes;
    private transient final Set<String> recentDownvotes;
    private transient final Set<String> recentCommenters;

    public Post(String author, String postTitle, String postContent) {
        this.idPost = nextIdPost++;
        this.author = author;
        this.postTitle = postTitle;
        this.postContent = postContent;
        comments = ConcurrentHashMap.newKeySet();
        upvotes = ConcurrentHashMap.newKeySet();
        downvotes = ConcurrentHashMap.newKeySet();
        commentsStats = new ConcurrentHashMap<>();

        recentUpvotes = ConcurrentHashMap.newKeySet();
        recentDownvotes = ConcurrentHashMap.newKeySet();
        recentCommenters = ConcurrentHashMap.newKeySet();
    }

    public Post(int idPost, String author, String postTitle, String postContent, Set<Comment> comments, Set<String> upvotes, Set<String> downvotes) {
        this.idPost = idPost;
        this.author = author;
        this.postTitle = postTitle;
        this.postContent = postContent;
        this.comments = comments;
        this.upvotes = upvotes;
        this.downvotes = downvotes;
        commentsStats = new ConcurrentHashMap<>();

        recentUpvotes = ConcurrentHashMap.newKeySet();
        recentDownvotes = ConcurrentHashMap.newKeySet();
        recentCommenters = ConcurrentHashMap.newKeySet();

        if (idPost >= nextIdPost) nextIdPost = idPost + 1;
    }

    public int getIdPost() {
        return idPost;
    }

    public String getAuthor() {
        return author;
    }

    public boolean addUpvote(String user) {
        if (upvotes.add(user)) {
            recentUpvotes.add(user);
            return true;
        } else
            return false;
    }

    public boolean addDownvote(String user) {
        if (downvotes.add(user)) {
            recentDownvotes.add(user);
            return true;
        } else
            return false;
    }

    public void addComment(Comment comment) {
        comments.add(comment);
        recentCommenters.add(comment.getAuthor());

        commentsStats.merge(comment.getAuthor(), 1, Integer::sum);
    }

    public boolean containsUpvote(String user) {
        return upvotes.contains(user);
    }

    public boolean containsDownvote(String user) {
        return downvotes.contains(user);
    }

    public int getRecentUpvotesAndReset() {
        int upvotes = recentUpvotes.size();
        recentUpvotes.clear();
        return upvotes;
    }

    public int getRecentDownvotesAndReset() {
        int downvotes = recentDownvotes.size();
        recentDownvotes.clear();
        return downvotes;
    }

    public Set<String> getRecentCommenters() {
        Set<String> copy = new HashSet<String>(recentCommenters);
        recentCommenters.clear();

        return copy;
    }

    public int getNumberOfComments(String username) {
        return commentsStats.get(username);
    }

    public String toJson() {
        String serializedPost = "{\"postTitle\":\"" + postTitle + "\", \"postContent\":\"" + postContent + "\", \"upvotes\":" + upvotes.size() + ", \"downvotes\":" + downvotes.size() + ", \"comments\": [";
        boolean firstEntry = true;
        for (Comment comment : comments) {
            if (!firstEntry) serializedPost += ", ";
            serializedPost += comment.toJson();

            firstEntry = false;
        }
        serializedPost += "]}";

        return serializedPost;
    }

    public String basicInfoToJson() {
        return "{\"idPost\": " + idPost + ", \"author\": \"" + author + "\", \"postTitle\": \"" + postTitle + "\" }";
    }

    public String toString() {
        return idPost + " " + postTitle + " " + postContent + " " + comments + " " + upvotes + " " + downvotes;
    }
}
