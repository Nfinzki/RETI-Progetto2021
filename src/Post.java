import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;
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

    public Post(String author, String postTitle, String postContent) {
        this.idPost = nextIdPost++;
        this.author = author;
        this.postTitle = postTitle;
        this.postContent = postContent;
        comments = ConcurrentHashMap.newKeySet();
        upvotes = ConcurrentHashMap.newKeySet();
        downvotes = ConcurrentHashMap.newKeySet();

    }

    public Post(int idPost, String author, String postTitle, String postContent, Set<Comment> comments, Set<String> upvotes, Set<String> downvotes) {
        this.idPost = idPost;
        this.author = author;
        this.postTitle = postTitle;
        this.postContent = postContent;
        this.comments = comments;
        this.upvotes = upvotes;
        this.downvotes = downvotes;

        if (idPost >= nextIdPost) nextIdPost = idPost + 1;
    }

    public int getIdPost() {
        return idPost;
    }

    public String getAuthor() {
        return author;
    }

    public boolean addUpvote(String user) {
        return upvotes.add(user);
    }

    public boolean addDownvote(String user) {
        return downvotes.add(user);
    }

    public void addComment(Comment comment) {
        this.comments.add(comment);
    }

    public boolean containsUpvote(String user) {
        return upvotes.contains(user);
    }

    public boolean containsDownvote(String user) {
        return downvotes.contains(user);
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
