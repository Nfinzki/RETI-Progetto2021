/**
 * This class implements a comment
 */

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

    /**
     * Returns the comment id
     * @return comment id
     */
    public int getId() {
        return idComment;
    }

    /**
     * Returns the author
     * @return author
     */
    public String getAuthor() {
        return author;
    }

    /**
     * Sets the next id that will be assigned to the comments.
     * This method should be used only while deserializing
     * @param id next id
     */
    public static void setNextId(int id) {
        nextId = id;
    }

    /**
     * Returns the comment in the json format
     * @return a string that contains the comment in json format
     */
    public String toJson() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(this);
    }
}
