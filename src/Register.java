import com.google.gson.stream.JsonReader;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.rmi.RemoteException;
import java.util.*;

public class Register implements RegisterInterface{
    private final Map<String, User> users;
    private final Map<Integer, Post> posts;
    private final String usersFile;
    private final String postsFile;

    public Register(String usersFile, String postsFile) {
        this.users = new HashMap<>();
        this.posts = new HashMap<>();
        this.usersFile = usersFile;
        this.postsFile = postsFile;
        readUsers();
        readPosts();
    }

    public int register(String username, String password, List<String> tag) throws RemoteException {
        if (username == null || password == null || tag == null) throw new NullPointerException();

        if (!users.containsKey(username) && !password.equals("") && tag.size() > 0 && tag.size() < 6) {
            String []tags = new String[tag.size()];
            for (int i = 0; i < tag.size(); i++) {
                tags[i] = tag.get(i).toLowerCase();
            }

            users.put(username, new User(username, password, tags));
            return 0;
        } else {
            if (password.equals("")) return 1; //Password field is empty
            if (tag.size() == 0) return 2; //No tag inserted
            if (tag.size() >= 6) return 3; //Too many tag

            return 4; //User already registered
        }
    }

    private Map<String, User> readUsers() {
        Map<String, User> users = new HashMap<>();

        try (FileInputStream fileInputStream = new FileInputStream(postsFile);
             InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream);
            JsonReader jsonReader = new JsonReader(inputStreamReader)) {
                jsonReader.beginArray();

                while (jsonReader.hasNext()) {
                    jsonReader.beginObject();

                    jsonReader.nextName();
                    int id = jsonReader.nextInt();

                    jsonReader.nextName();
                    String postTitle = jsonReader.nextString();

                    jsonReader.nextName();
                    String postContent = jsonReader.nextString();

                    jsonReader.nextName();
                    jsonReader.beginArray();
                    List<String> comments = new ArrayList<>();
                    while (jsonReader.hasNext())
                        comments.add(jsonReader.nextString());
                    jsonReader.endArray();

                    jsonReader.nextName();
                    jsonReader.beginArray();
                    List<Integer> upvotes = new ArrayList<>();
                    while (jsonReader.hasNext())
                        upvotes.add(jsonReader.nextInt());
                    jsonReader.endArray();

                    jsonReader.nextName();
                    jsonReader.beginArray();
                    List<Integer> downvotes = new ArrayList<>();
                    while (jsonReader.hasNext())
                        downvotes.add(jsonReader.nextInt());
                    jsonReader.endArray();

                    jsonReader.endObject();

                    posts.put(
                            id,
                            new Post(
                                    id,
                                    postTitle,
                                    postContent,
                                    comments,
                                    upvotes,
                                    downvotes
                            )
                    );
                }
                jsonReader.endArray();

            System.out.println(posts);
        } catch (FileNotFoundException e) {
            System.err.println("Error while reading json file: " + e.getMessage());
            return null;
        } catch (IOException e) {
            System.err.println("I/O Error: " + e.getMessage());
            return null;
        }

        return users;
    }

    private Map<Integer, Post> readPosts() {
        Map<String, User> users = new HashMap<>();

        try (FileInputStream fileInputStream = new FileInputStream(usersFile);
             InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream);
             JsonReader jsonReader = new JsonReader(inputStreamReader)) {
            jsonReader.beginArray();

            while (jsonReader.hasNext()) {
                jsonReader.beginObject();

                jsonReader.nextName();
                int id = jsonReader.nextInt();

                jsonReader.nextName();
                String username = jsonReader.nextString();

                jsonReader.nextName();
                String password = jsonReader.nextString();

                jsonReader.nextName();
                jsonReader.beginArray();
                List<String> tagList = new ArrayList<>();
                while (jsonReader.hasNext())
                    tagList.add(jsonReader.nextString());
                String []tags = tagList.toArray(new String[0]);
                jsonReader.endArray();

                jsonReader.nextName();
                jsonReader.beginArray();
                List<Integer> follower = new ArrayList<>();
                while (jsonReader.hasNext())
                    follower.add(jsonReader.nextInt());
                jsonReader.endArray();

                jsonReader.nextName();
                jsonReader.beginArray();
                List<Integer> followed = new ArrayList<>();
                while (jsonReader.hasNext())
                    followed.add(jsonReader.nextInt());
                jsonReader.endArray();

                jsonReader.nextName();
                jsonReader.beginArray();
                List<Integer> blog = new ArrayList<>();
                while (jsonReader.hasNext())
                    blog.add(jsonReader.nextInt());
                jsonReader.endArray();

                jsonReader.nextName();
                jsonReader.beginObject();
                jsonReader.nextName();
                double wincoin = jsonReader.nextDouble();
                jsonReader.endObject();

                jsonReader.endObject();

                users.put(
                        username,
                        new User(
                                id,
                                username,
                                password,
                                tags,
                                follower,
                                followed,
                                blog,
                                new Wallet(wincoin)
                        )
                );
            }
            jsonReader.endArray();

            System.out.println(users);
        } catch (FileNotFoundException e) {
            System.err.println("Error while reading json file: " + e.getMessage());
            return null;
        } catch (IOException e) {
            System.err.println("I/O Error: " + e.getMessage());
            return null;
        }

        return posts;
    }
}
