/**
 * This class implements a post
 */

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
    private final Map<String, Integer> commentsStats; //Number of comments on this post for each user
    private final Set<String> rewinner; //Users who rewinned this post
    private int revenueIteration; //Number of time this post was evaluated

    //These attributes are used to calculate the revenue
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

    /**
     * Creates a new post. This constructor should be used only while deserializing
     * @param idPost post id
     * @param author author
     * @param postTitle post title
     * @param postContent post content
     * @param comments comments under the post
     * @param upvotes users who upvoted the post
     * @param downvotes users who downvoted the post
     * @param commentsStats how many comment made each user under this post
     * @param rewinner users who rewinned this post
     * @param revenueIteration number of time this post was evaluated
     */
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

    /**
     * Returns the post id
     * @return post id
     */
    public int getIdPost() {
        return idPost;
    }

    /**
     * Returns the author of the post
     * @return author
     */
    public String getAuthor() {
        return author;
    }

    /**
     * Add the username of the user who wants to upvote this post
     * @param user username of the user who wants to upvote this post
     * @return true iff the user didn't previously upvoted the post, false otherwise
     */
    public boolean addUpvote(String user) {
        synchronized (recentUpvotes) {
            if (upvotes.add(user)) {
                recentUpvotes.add(user);
                return true;
            } else
                return false;
        }
    }

    /**
     * Add the username of the user who wants to downvote this post
     * @param user username of the user who wants to downvote this post
     * @return true iff the user didn't previously downvoted the post, false otherwise
     */
    public boolean addDownvote(String user) {
        synchronized (recentDownvotes) {
            if (downvotes.add(user)) {
                recentDownvotes.add(user);
                return true;
            } else
                return false;
        }
    }

    /**
     * Add the comment to the comment section of the post
     * @param comment comment to add to the comment section
     */
    public void addComment(Comment comment) {
        synchronized (recentCommenters) {
            comments.add(comment);
            recentCommenters.add(comment.getAuthor());

            //Increments the number of comment that the author of the comment has done
            commentsStats.merge(comment.getAuthor(), 1, Integer::sum);
        }
    }

    /**
     * Add the username to the rewinner list
     * @param username username of the user that rewinned the post
     * @return true iff the user didn't already rewinned the post, false otherwise
     */
    public boolean addRewinner(String username) {
        return rewinner.add(username);
    }

    /**
     * Checks if the user upvoted this post
     * @param user username of the user to search
     * @return true iff user upvoted this post, false otherwise
     */
    public boolean containsUpvote(String user) {
        return upvotes.contains(user);
    }

    /**
     * Checks if the user downvoted this post
     * @param user username of the user to search
     * @return true iff user downvoted this post, false otherwise
     */
    public boolean containsDownvote(String user) {
        return downvotes.contains(user);
    }

    /**
     * Return the users who upvoted this post recently and
     * resets the list
     *
     * @return the users who upvoted this post recently
     */
    public Set<String> getRecentUpvotesAndReset() {
        synchronized (recentUpvotes) {
            Set<String> recentUpvotesCopy = new HashSet<>(recentUpvotes);
            recentUpvotes.clear();
            return recentUpvotesCopy;
        }
    }

    /**
     * Return the number of users who downvoted this post recently and
     * resets the list
     *
     * @return the number of users who downvoted this post recently
     */
    public int getRecentDownvotesAndReset() {
        synchronized (recentDownvotes){
            int downvotes = recentDownvotes.size();
            recentDownvotes.clear();
            return downvotes;
        }
    }

    /**
     * Return the recent comments and resets the list
     *
     * @return the recent comments
     */
    public Set<String> getRecentCommenters() {
        synchronized (recentCommenters) {
            Set<String> copy = new HashSet<>(recentCommenters);
            recentCommenters.clear();

            return copy;
        }
    }

    /**
     * Returns the number of comments that this user made
     * on this post
     * @param username the username of the user to check
     * @return the number of comments
     */
    public int getNumberOfComments(String username) {
        return commentsStats.get(username);
    }

    /**
     * @return the times this post was evaluated
     */
    public synchronized int getRevenueIteration() {
        return revenueIteration;
    }

    /**
     * Increments the number of times this post was evaluated
     */
    public synchronized void incrementRevenueIteration() {
        revenueIteration++;
    }

    /**
     * Returns title, content, number of upvotes, number of downvotes and comments in json format
     * @return useful information about the post in json format
     */
    public synchronized String toJson() {
        //Initializes the string with the post basic information
        StringBuilder serializedPost = new StringBuilder("{\"postTitle\":\"" + postTitle + "\", \"postContent\":\"" + postContent + "\", \"upvotes\":" + upvotes.size() + ", \"downvotes\":" + downvotes.size() + ", \"comments\": [");
        boolean firstEntry = true;

        //Serializes all the comments
        for (Comment comment : comments) {
            //If this isn't the first comment, adds a ','
            if (!firstEntry) serializedPost.append(", ");

            serializedPost.append(comment.toJson());

            firstEntry = false;
        }
        serializedPost.append("]}");

        return serializedPost.toString();
    }

    /**
     * @return the list of the users who rewinned this post
     */
    public Set<String> getRewinner() {
        synchronized (rewinner) {
            return new HashSet<>(rewinner);
        }
    }

    /**
     * Returns post id, author and the title in json format
     * @return basics information about the post in json format
     */
    public String basicInfoToJson(String username) {
        String response = "{\"idPost\": " + idPost + ", \"author\": \"" + author + "\", \"postTitle\": \"" + postTitle;
        if (rewinner.contains(username)) response += " [rewinned by " + username + "]";
        response += "\"}";
        return response;
    }

    /**
     * Writes the post in a file in json format
     * @param writer writer used to write the object as json object
     */
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

    /**
     * Writes a collection of comments to a file in a json format
     * @param writer writer used to write the object as json array
     * @param name name of the json entry
     * @param collection collection to serialize
     */
    private void commentsCollectionToJson(JsonWriter writer, String name, Collection<Comment> collection) throws IOException {
        Gson gson = new Gson();

        writer.name(name);
        writer.beginArray();
        for (Comment comment : collection)
            gson.toJson(comment, Comment.class, writer);
        writer.endArray();
        writer.flush();
    }

    /**
     * Writes a collection of string to a file in a json format
     * @param writer writer used to write the object as json array
     * @param name name of the json entry
     * @param collection collection to serialize
     */
    private void stringCollectionToJson(JsonWriter writer, String name, Collection<String> collection) throws IOException {
        writer.name(name);
        writer.beginArray();
        for (String s : collection) {
            writer.value(s);
        }
        writer.endArray();
        writer.flush();
    }

    /**
     * Writes a map to a file in a json format
     * @param writer writer used to write the object as json array
     * @param name name of the json entry
     * @param map map to serialize
     */
    private void mapToJson(JsonWriter writer, String name, Map<String, Integer> map) throws IOException {
        writer.name(name);
        writer.beginObject();

        for (String key : map.keySet())
            writer.name(key).value(map.get(key));

        writer.endObject();
        writer.flush();
    }
}
