import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class User {
    public static int nextId = 0;
    private final int id;
    private final String username;
    private final String password;
    private final String []tag;
    private final List<Integer> follower;
    private final List<Integer> followed;
    private final List<Integer> blog;
    private final Wallet wallet;


    public User(String username, String password, String []tag) {
        if (tag.length > 5) throw new ArrayIndexOutOfBoundsException();

        this.id = nextId++;
        this.username = username;
        this.password = password;
        this.tag = tag;
        this.follower = new ArrayList<>();
        this.followed = new ArrayList<>();
        this.blog = new ArrayList<>();
        this.wallet = new Wallet();
    }

    public User(int id, String username, String password, String []tag, List<Integer> follower, List<Integer> followed, List<Integer> blog, Wallet wallet) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.tag = tag;
        this.follower = follower;
        this.followed = followed;
        this.blog = blog;
        this.wallet = wallet;

        if (id >= nextId) nextId = id + 1;
    }

    public boolean comparePassword(String password) {
        return this.password.equals(password);
    }

    public String toString() {
        return id + " " + username + " " + password + " " + Arrays.toString(tag) + " " + follower + " " + followed + " " + blog + " " + wallet;
    }
}
