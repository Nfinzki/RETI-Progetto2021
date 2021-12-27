import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class Comment {
    private static int nextId = 0;
    private final int idComment;
    private final String author;
    private final String content;

    public Comment(String author, String content) {
        idComment = nextId++;
        this.author = author;
        this.content = content;
    }

    public int getId() {
        return idComment;
    }

    public String getComment() {
        return author + ": " + content;
    }

    public void setNextId(int id) {
        nextId = id;
    }

    public String toJson() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(this);
    }
}
