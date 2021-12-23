import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class User {
    //public static int nextId = 0;
    //private final int id;
    private final String username;
    private final String password;
    private final String []tag;
    private final Set<String> follower;
    private final Set<String> followed;
    private final Set<Integer> blog;
    private final Wallet wallet;


    public User(String username, String password, String []tag) {
        if (tag.length > 5) throw new ArrayIndexOutOfBoundsException();

        //this.id = nextId++;
        this.username = username;
        this.password = password;
        this.tag = tag;
        //this.follower = new ArrayList<>();
        //this.followed = new ArrayList<>();
        this.follower = ConcurrentHashMap.newKeySet();
        this.followed = ConcurrentHashMap.newKeySet();
        this.blog = ConcurrentHashMap.newKeySet();
        this.wallet = new Wallet();
    }

    public User(String username, String password, String []tag, Set<String> follower, Set<String> followed, Set<Integer> blog, Wallet wallet) {
        //this.id = id;
        this.username = username;
        this.password = password;
        this.tag = tag;
        this.follower = follower;
        this.followed = followed;
        this.blog = blog;
        this.wallet = wallet;

        //if (id >= nextId) nextId = id + 1;
    }

    public boolean comparePassword(String password) {
        return this.password.equals(password);
    }

    /*public int getId() {
        return id;
    }*/

    public String getUsername() {
        return username;
    }

    public String[] getTag () {
        return tag;
    }

    public Set<Integer> getPosts() {
        return blog;
    }

    public String[] getCommonTags(User u) {
        List<String> commonTags = new ArrayList<>(5);

        for (String t : tag) {
            for (String t2 : u.getTag())
                if (t.equals(t2)) commonTags.add(t);
        }

        return commonTags.toArray(new String[0]);
    }

    public void addPost(int postId) {
        blog.add(postId);
    }

    public boolean ownsPost(int idPost) {
        return blog.contains(idPost);
    }

    public void removePost(int idPost) {
        blog.remove(idPost);
    }

    public boolean addFollowed(String follower) {
        return this.followed.add(follower);
    }

    public boolean removeFollowed(String follower) {
        return this.followed.remove(follower);
    }

    public boolean addFollower(String follower) {
        return this.follower.add(follower);
    }

    public boolean removeFollower(String follower) {
        return this.follower.remove(follower);
    }

    public String toString() {
        return username + " " + password + " " + Arrays.toString(tag) + " " + follower + " " + followed + " " + blog + " " + wallet;
    }
}
