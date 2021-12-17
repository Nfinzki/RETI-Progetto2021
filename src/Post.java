import java.util.ArrayList;
import java.util.List;

public class Post {
    private static int nextIdPost = 0;
    private final int idPost;
    private final String postTitle;
    private final String postContent;
    private final List<String> comments;
    private final List<Integer> upvotes;
    private final List<Integer> downvotes;

    public Post(String postTitle, String postContent) {
        this.idPost = nextIdPost++;
        this.postTitle = postTitle;
        this.postContent = postContent;
        comments = new ArrayList<>();
        upvotes = new ArrayList<>();
        downvotes = new ArrayList<>();

    }

    public int getIdPost() {
        return idPost;
    }
}
