import java.util.ArrayList;
import java.util.List;

public class User {
    public static int nextId = 0;
    private final int id;
    private final String username;
    private final String password;
    private final String []tag;
    private final List<Integer> follower;
    private final List<Integer> followed;
    private final List<Post> blog;
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
}
