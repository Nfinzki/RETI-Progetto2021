import com.google.gson.Gson;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Post implements BufferedSerialization {
    private static int nextIdPost = 0;
    private final int idPost;
    private final String author;
    private final String postTitle;
    private final String postContent;
    private final Set<Comment> comments;
    private final Set<String> upvotes;
    private final Set<String> downvotes;
    private final Map<String, Integer> commentsStats;
    private final Set<String> rewinner;
    private int revenueIteration;

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
        rewinner = ConcurrentHashMap.newKeySet();
        revenueIteration = 1;

        recentUpvotes = ConcurrentHashMap.newKeySet();
        recentDownvotes = ConcurrentHashMap.newKeySet();
        recentCommenters = ConcurrentHashMap.newKeySet();
    }

    public Post(int idPost, String author, String postTitle, String postContent, Set<Comment> comments, Set<String> upvotes, Set<String> downvotes, Map<String, Integer> commentsStats, Set<String> rewinner, int revenueIteration) {
        this.idPost = idPost;
        this.author = author;
        this.postTitle = postTitle;
        this.postContent = postContent;
        this.comments = comments;
        this.upvotes = upvotes;
        this.downvotes = downvotes;
        this.commentsStats = commentsStats;
        this.rewinner = rewinner;
        this.revenueIteration = revenueIteration;

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

    public synchronized boolean addUpvote(String user) {
        if (upvotes.add(user)) {
            recentUpvotes.add(user);
            return true;
        } else
            return false;
    }

    public synchronized boolean addDownvote(String user) {
        if (downvotes.add(user)) {
            recentDownvotes.add(user);
            return true;
        } else
            return false;
    }

    public synchronized void addComment(Comment comment) {
        comments.add(comment);
        recentCommenters.add(comment.getAuthor());

        commentsStats.merge(comment.getAuthor(), 1, Integer::sum);
    }

    public boolean addRewinner(String username) {
        return  rewinner.add(username);
    }

    public boolean containsUpvote(String user) {
        return upvotes.contains(user);
    }

    public boolean containsDownvote(String user) {
        return downvotes.contains(user);
    }

    public synchronized int getRecentUpvotesAndReset() {
        int upvotes = recentUpvotes.size();
        recentUpvotes.clear();
        return upvotes;
    }

    public synchronized int getRecentDownvotesAndReset() {
        int downvotes = recentDownvotes.size();
        recentDownvotes.clear();
        return downvotes;
    }

    public synchronized Set<String> getRecentCommenters() {
        Set<String> copy = new HashSet<>(recentCommenters);
        recentCommenters.clear();

        return copy;
    }

    public int getNumberOfComments(String username) {
        return commentsStats.get(username);
    }

    public synchronized int getRevenueIteration() {
        return revenueIteration;
    }

    public synchronized void incrementRevenueIteration() {
        revenueIteration++;
    }

    public synchronized String toJson() {
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

    public synchronized Set<String> getRewinner() {
        return new HashSet<>(rewinner);
    }

    public String basicInfoToJson() {
        return "{\"idPost\": " + idPost + ", \"author\": \"" + author + "\", \"postTitle\": \"" + postTitle + "\" }";
    }

    public synchronized void toJsonFile(JsonWriter writer) throws IOException {
        writer.beginObject();
        writer.name("idPost").value(idPost);
        writer.name("author").value(author);
        writer.name("postTitle").value(postTitle);
        writer.name("postContent").value(postContent);
        commentsCollectionToJson(writer, "comments", comments);
        stringCollectionToJson(writer, "upvotes", upvotes);
        stringCollectionToJson(writer, "downvotes", downvotes);
        mapToJson(writer, "commentsStats", commentsStats);
        stringCollectionToJson(writer, "rewinner", rewinner);
        writer.name("revenueIteration").value(revenueIteration);
        writer.endObject();
    }

    private void commentsCollectionToJson(JsonWriter writer, String name, Collection<Comment> collection) throws IOException {
        Gson gson = new Gson();

        writer.name(name);
        writer.beginArray();
        for (Comment comment : collection)
            gson.toJson(comment, Comment.class, writer);
        writer.endArray();
        writer.flush();
    }

    private void stringCollectionToJson(JsonWriter writer, String name, Collection<String> collection) throws IOException {
        writer.name(name);
        writer.beginArray();
        for (String s : collection) {
            writer.value(s);
        }
        writer.endArray();
        writer.flush();
    }

    private void mapToJson(JsonWriter writer, String name, Map<String, Integer> map) throws IOException {
        writer.name(name);
        writer.beginObject();

        for (String key : map.keySet())
            writer.name(key).value(map.get(key));

        writer.endObject();
        writer.flush();
    }
}
