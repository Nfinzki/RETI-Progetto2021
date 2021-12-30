import com.google.gson.Gson;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class User implements BufferedSerialization{
    private final String username;
    private final String password;
    private final String []tag;
    private final Set<String> follower;
    private final Set<String> followed;
    private final Set<Integer> blog;
    private final Wallet wallet;


    public User(String username, String password, String []tag) {
        if (tag.length > 5) throw new ArrayIndexOutOfBoundsException();

        this.username = username;
        this.password = password;
        this.tag = tag;
        this.follower = ConcurrentHashMap.newKeySet();
        this.followed = ConcurrentHashMap.newKeySet();
        this.blog = ConcurrentHashMap.newKeySet();
        this.wallet = new Wallet();
    }

    public User(String username, String password, String []tag, Set<String> follower, Set<String> followed, Set<Integer> blog, Wallet wallet) {
        this.username = username;
        this.password = password;
        this.tag = tag;
        this.follower = follower;
        this.followed = followed;
        this.blog = blog;
        this.wallet = wallet;
    }

    public boolean comparePassword(String password) {
        return this.password.equals(password);
    }

    public String getUsername() {
        return username;
    }

    public String[] getTag () {
        return tag;
    }

    public Set<String> getFollowed() {
        return followed;
    }

    public String[] getCommonTags(User u) {
        List<String> commonTags = new ArrayList<>(5);

        for (String t : tag) {
            for (String t2 : u.getTag())
                if (t.equals(t2)) commonTags.add(t);
        }

        return commonTags.toArray(new String[0]);
    }

    public boolean addPost(int postId) {
        return blog.add(postId);
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

    public boolean follows(String user) {
        return followed.contains(user);
    }

    public String getFollowersAsJson() {
        Gson gson = new Gson();
        return gson.toJson(follower);
    }

    public String getFollowingAsJson() {
        Gson gson = new Gson();
        return gson.toJson(followed);
    }

    public Set<Integer> getBlog() {
        return blog;
    }

    public Wallet getWallet() {
        return wallet;
    }

    public String getWalletAsJson() {
        Gson gson = new Gson();
        return gson.toJson(wallet);
    }

    public void prova() {
        Gson gson = new Gson();
        System.out.println(gson.toJson(tag));
    }

    public void toJsonFile(JsonWriter writer) throws IOException{
        writer.beginObject();
        writer.name("username").value(username);
        writer.name("password").value(password);

        writer.name("tag");
        writer.beginArray();
        for (String t : tag)
            writer.value(t);
        writer.endArray();

        stringCollectionToJson(writer, "follower", follower);

        stringCollectionToJson(writer, "followed", followed);

        integerCollectionToJson(writer, "blog", blog);

        writer.name("wallet");
        wallet.toJsonFile(writer);

        writer.endObject();
        writer.flush();
    }

    private void integerCollectionToJson(JsonWriter writer, String name, Collection<Integer> collection) throws IOException{
        writer.name(name);
        writer.beginArray();
        for (int integer : collection) {
            writer.value(integer);
        }
        writer.endArray();
    }

    private void stringCollectionToJson(JsonWriter writer, String name, Collection<String> collection) throws IOException{
        writer.name(name);
        writer.beginArray();
        for (String s : collection) {
            writer.value(s);
        }
        writer.endArray();
    }

    public String toString() {
        return username + " " + password + " " + Arrays.toString(tag) + " " + follower + " " + followed + " " + blog + " " + wallet;
    }
}
