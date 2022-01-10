/**
 * This class implements a user
 */

import com.google.gson.Gson;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class User implements BufferedSerialization {
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

    /**
     * Creates a new user. This constructor should be used only while deserializing
     * @param username username of the user
     * @param password password of the user
     * @param tag tag list
     * @param follower list of follower users
     * @param followed list of followed users
     * @param blog list of user's post
     * @param wallet user's wallet
     */
    public User(String username, String password, String []tag, Set<String> follower, Set<String> followed, Set<Integer> blog, Wallet wallet) {
        this.username = username;
        this.password = password;
        this.tag = tag;
        this.follower = follower;
        this.followed = followed;
        this.blog = blog;
        this.wallet = wallet;
    }

    /**
     * Compares the password with the parameter
     * @param password password to authenticate
     * @return true iff the passwords matches, false otherwise
     */
    public boolean comparePassword(String password) {
        return this.password.equals(password);
    }

    /**
     * @return username
     */
    public String getUsername() {
        return username;
    }

    /**
     * @return tag list
     */
    public String[] getTag () {
        return tag;
    }

    /**
     * @return list of followed user
     */
    public Set<String> getFollowed() {
        synchronized (followed) {
            return new HashSet<>(followed);
        }
    }

    /**
     * Returns common tag between two user
     * @param u user
     * @return array of common tag
     */
    public String[] getCommonTags(User u) {
        List<String> commonTags = new ArrayList<>(5);

        for (String t : tag) {
            for (String t2 : u.getTag())
                if (t.equals(t2)) commonTags.add(t);
        }

        return commonTags.toArray(new String[0]);
    }

    /**
     * Adds a new post to the user's blog
     * @param postId post id
     * @return true iff the post wasn't already added, false otherwise
     */
    public boolean addPost(int postId) {
        return blog.add(postId);
    }

    /**
     * @param idPost post id
     * @return true if this user owns the post identified with idPost, false otherwise
     */
    public boolean ownsPost(int idPost) {
        return blog.contains(idPost);
    }

    /**
     * Removes a post from the user's blog
     * @param idPost post id
     */
    public void removePost(int idPost) {
        blog.remove(idPost);
    }

    /**
     * Adds a user to the followed list
     * @param followed user to follow
     * @return true iff this user didn't follow that user, false otherwise
     */
    public boolean addFollowed(String followed) {
        return this.followed.add(followed);
    }

    /**
     * Removes a user from the followed list
     * @param followed user to remove
     * @return true iff this user followed that user, false otherwise
     */
    public boolean removeFollowed(String followed) {
        return this.followed.remove(followed);
    }

    /**
     * Adds a user to the follower list
     * @param follower new follower
     * @return true iff this user wasn't already followed by the new follower, false otherwise
     */
    public boolean addFollower(String follower) {
        return this.follower.add(follower);
    }

    /**
     * Removes a user to the follower list
     * @param follower follower to remove
     * @return true iff this user was followed by the follower to remove, false otherwise
     */
    public boolean removeFollower(String follower) {
        return this.follower.remove(follower);
    }

    /**
     * @param user user to verify
     * @return true iff this user follows the user to verify, false otherwise
     */
    public boolean follows(String user) {
        return followed.contains(user);
    }

    /**
     * @return a string which contains the follower list as json format
     */
    public String getFollowersAsJson() {
        Gson gson = new Gson();
        synchronized (follower) {
            return gson.toJson(follower);
        }
    }

    /**
     * @return a set which contains the following list
     */
    public Set<String> getFollowing() {
        synchronized (followed) {
            return new HashSet<>(followed);
        }
    }

    /**
     * @return a set which contains the blog
     */
    public Set<Integer> getBlog() {
        synchronized (blog) {
            return new HashSet<>(blog);
        }
    }

    /**
     * @return the wallet of the user
     */
    public Wallet getWallet() {
        return wallet;
    }

    /**
     * @return a string which contains the wallet as json format
     */
    public String getWalletAsJson() {
        Gson gson = new Gson();
        synchronized (wallet) {
            return gson.toJson(wallet);
        }
    }

    /**
     * Writes the user in a file in json format
     * @param writer writer used to write the object as json object
     */
    public synchronized void toJsonFile(JsonWriter writer) throws IOException{
        //Writes '{'
        writer.beginObject();

        writer.name("username").value(username);
        writer.name("password").value(password);

        writer.name("tag");
        //Writes '['
        writer.beginArray();
        for (String t : tag)
            writer.value(t);
        //Writes ']'
        writer.endArray();

        //Writes the follower list as a json array
        stringCollectionToJson(writer, "follower", follower);

        //Writes the followed list as json array
        stringCollectionToJson(writer, "followed", followed);

        //Writes the blog list as json array
        integerCollectionToJson(writer, "blog", blog);

        writer.name("wallet");
        wallet.toJsonFile(writer);

        //Writes '}'
        writer.endObject();
        writer.flush();
    }

    /**
     * Writes an integer collection to a file in json format
     * @param writer writer used to write the object as json object
     * @param name name of the field
     * @param collection collection to serialize
     */
    private void integerCollectionToJson(JsonWriter writer, String name, Collection<Integer> collection) throws IOException {
        //Writes the name of the field
        writer.name(name);
        //Writes '['
        writer.beginArray();
        for (int integer : collection) {
            writer.value(integer);
        }
        //Writes ']'
        writer.endArray();
        writer.flush();
    }

    /**
     * Writes a string collection to a file in json format
     * @param writer writer used to write the object as json object
     * @param name name of the field
     * @param collection collection to serialize
     */
    private void stringCollectionToJson(JsonWriter writer, String name, Collection<String> collection) throws IOException {
        //Writes the name of the field
        writer.name(name);
        //Writes '['
        writer.beginArray();
        for (String s : collection) {
            writer.value(s);
        }
        //Writes ']'
        writer.endArray();
        writer.flush();
    }
}
