import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RecoverState {
    public static void readPosts(Map<Integer, Post> posts, String postsFile) {
        File file = new File(postsFile);
        if (!file.isFile()) return;

        try (FileInputStream fileInputStream = new FileInputStream(postsFile);
             InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream);
             JsonReader jsonReader = new JsonReader(inputStreamReader)) {
            //Removes the '['
            jsonReader.beginArray();

            //Reads all the posts
            while (jsonReader.hasNext()) {
                //Start post
                jsonReader.beginObject();

                //Reads idPost
                int id = getIntegerFromJson(jsonReader);

                //Reads authorId
                String authorId = getStringFromJson(jsonReader);

                //Reads postTitle
                String postTitle = getStringFromJson(jsonReader);

                //Reads postContent
                String postContent = getStringFromJson(jsonReader);

                //Reads comment
                Set<Comment> comments = getCommentSetFromJson(jsonReader);

                //Reads upvote
                Set<String> upvotes = getStringSetFromJson(jsonReader);

                //Reads downvote
                Set<String> downvotes = getStringSetFromJson(jsonReader);

                //Reads commentStats
                Map<String, Integer> commentStats = getCommentStatFromJson(jsonReader);

                //Reads users that rewinned this post
                Set<String> rewinner = getStringSetFromJson(jsonReader);

                //End post
                jsonReader.endObject();

                //Adds post to the server
                posts.put(
                        id,
                        new Post( //Create the post
                                id,
                                authorId,
                                postTitle,
                                postContent,
                                comments,
                                upvotes,
                                downvotes,
                                commentStats,
                                rewinner
                        )
                );
            }
            //Removes the ']'
            jsonReader.endArray();
        } catch (FileNotFoundException e) {
            System.err.println("Error while reading json file: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("I/O Error: " + e.getMessage());
        }
    }

    public static void readUsers(Map<String, User> users, String usersFile) {
        File file = new File(usersFile);
        if (!file.isFile()) return;

        try (FileInputStream fileInputStream = new FileInputStream(usersFile);
             InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream);
             JsonReader jsonReader = new JsonReader(inputStreamReader)) {
            //Removes the first '['
            jsonReader.beginArray();

            //Reads all the users
            while (jsonReader.hasNext()) {
                //Start user
                jsonReader.beginObject();

                //Reads id
                //int id = getIntegerFromJson(jsonReader);

                //Reads username
                String username = getStringFromJson(jsonReader);

                //Reads password
                String password = getStringFromJson(jsonReader);

                //Reads tags
                String[] tags = getStringListFromJson(jsonReader).toArray(new String[0]);

                //Reads followers
                Set<String> follower = getStringSetFromJson(jsonReader);

                //Reads followed
                Set<String> followed = getStringSetFromJson(jsonReader);

                //Reads blog
                Set<Integer> blog = getIntegerSetFromJson(jsonReader);

                //Read wallet
                Gson gson = new Gson();
                jsonReader.nextName();
                Wallet wallet = gson.fromJson(jsonReader, Wallet.class);

                //End user
                jsonReader.endObject();

                //Add the user to the server
                users.put(
                        username,
                        new User( //Creates the user
                                //id,
                                username,
                                password,
                                tags,
                                follower,
                                followed,
                                blog,
                                wallet
                        )
                );
            }
            //Removes ']'
            jsonReader.endArray();
        } catch (FileNotFoundException e) {
            System.err.println("Error while reading json file: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("I/O Error: " + e.getMessage());
        }
    }

    private static Set<String> getStringSetFromJson(JsonReader jsonReader) throws IOException {
        jsonReader.nextName();
        //Removes '['
        jsonReader.beginArray();
        Set<String> follower = ConcurrentHashMap.newKeySet();
        while (jsonReader.hasNext()) //Reads all the elements in the array
            follower.add(jsonReader.nextString());
        jsonReader.endArray(); //Removes the ']'
        return follower;
    }

    private static Set<Comment> getCommentSetFromJson(JsonReader jsonReader) throws IOException{
        jsonReader.nextName();
        //Removes '['
        jsonReader.beginArray();
        Set<Comment> comments = ConcurrentHashMap.newKeySet();
        Gson gson = new Gson();
        int nextId = 0;
        while (jsonReader.hasNext()) {//Reads all the elements in the array
            Comment comment = gson.fromJson(jsonReader, Comment.class);
            comments.add(comment);

            if (comment.getId() >= nextId) Comment.setNextId(comment.getId() + 1);
        }
        jsonReader.endArray(); //Removes the ']'
        return comments;
    }

    private static Set<Integer> getIntegerSetFromJson(JsonReader jsonReader) throws IOException {
        jsonReader.nextName();
        //Removes '['
        jsonReader.beginArray();
        Set<Integer> follower = ConcurrentHashMap.newKeySet();
        while (jsonReader.hasNext()) //Reads all the elements in the array
            follower.add(jsonReader.nextInt());
        jsonReader.endArray(); //Removes the ']'
        return follower;
    }

    private static List<String> getStringListFromJson(JsonReader jsonReader) throws IOException {
        jsonReader.nextName();
        //Removes the '['
        jsonReader.beginArray();
        List<String> stringList = new ArrayList<>();
        while (jsonReader.hasNext()) //Reads all the elements in the array
            stringList.add(jsonReader.nextString());
        jsonReader.endArray(); //Removes the ']'
        return stringList;
    }

    private static String getStringFromJson(JsonReader jsonReader) throws IOException {
        jsonReader.nextName();
        return jsonReader.nextString();
    }

    private static int getIntegerFromJson(JsonReader jsonReader) throws IOException {
        //Reads id
        jsonReader.nextName();
        return jsonReader.nextInt();
    }

    private static Map<String, Integer> getCommentStatFromJson(JsonReader jsonReader) throws IOException{
        jsonReader.nextName();
        //Removes the '['
        jsonReader.beginObject();
        Map<String, Integer> commentStat = new ConcurrentHashMap<>();

        while (jsonReader.hasNext()) { //Reads all the elements in the array
            commentStat.put(jsonReader.nextName(), jsonReader.nextInt());
        }

        jsonReader.endObject(); //Removes the ']'

        return commentStat;
    }
}
