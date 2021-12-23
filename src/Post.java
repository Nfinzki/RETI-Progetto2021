import java.util.ArrayList;
import java.util.List;

public class Post {
    private static int nextIdPost = 0;
    private final int idPost;
    private final int authorId;
    private final String postTitle;
    private final String postContent;
    private final List<String> comments;
    private final List<Integer> upvotes;
    private final List<Integer> downvotes;

    public Post(int authorId, String postTitle, String postContent) {
        this.idPost = nextIdPost++;
        this.authorId = authorId;
        this.postTitle = postTitle;
        this.postContent = postContent;
        comments = new ArrayList<>();
        upvotes = new ArrayList<>();
        downvotes = new ArrayList<>();

    }

    public Post(int idPost, int authorId, String postTitle, String postContent, List<String> comments, List<Integer> upvotes, List<Integer> downvotes) {
        this.idPost = idPost;
        this.authorId = authorId;
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

    public String toString() {
        return idPost + " " + postTitle + " " + postContent + " " + comments + " " + upvotes + " " + downvotes;
    }
}
