/**
 * This class recovers the state of the server from json files
 */

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RecoverState {
    /**
     * Recovers the post state from the json file
     * @param posts map to populate
     * @param postsFile json file to read
     */
    public static void readPosts(Map<Integer, Post> posts, String postsFile) {
        //Checks if postFile is a file
        File file = new File(postsFile);
        if (!file.isFile() && file.exists()) {
            System.err.println(postsFile + " isn't a file!");
            System.exit(1);
        }

        //Checks if the file is empty
        if (file.length() == 0) return;

        try (FileInputStream fileInputStream = new FileInputStream(postsFile);
             InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream); //Opens the stream to read the file
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

                //Reads the revenueIteration
                int revenueIteration = getIntegerFromJson(jsonReader);

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
                                rewinner,
                                revenueIteration
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

    /**
     * Recovers the user state from the json file
     * @param users map to populate
     * @param usersFile json file to read
     */
    public static void readUsers(Map<String, User> users, String usersFile) {
        //Checks if userFile is a file
        File file = new File(usersFile);
        if (!file.isFile() && file.exists()) {
            System.err.println(usersFile + " isn't a file!");
            System.exit(1);
        }

        //Checks if the file is empty
        if (file.length() == 0) return;

        try (FileInputStream fileInputStream = new FileInputStream(usersFile);
             InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream); //Opens the stream to read the file
             JsonReader jsonReader = new JsonReader(inputStreamReader)) {
            //Removes the first '['
            jsonReader.beginArray();

            //Reads all the users
            while (jsonReader.hasNext()) {
                //Start user
                jsonReader.beginObject();

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

    /**
     * Reads a string json array
     * @param jsonReader reader to read from a file
     * @return a set of strings
     */
    private static Set<String> getStringSetFromJson(JsonReader jsonReader) throws IOException {
        //Reads the name
        jsonReader.nextName();
        //Removes '['
        jsonReader.beginArray();

        Set<String> follower = ConcurrentHashMap.newKeySet(); //Creates a concurrent set
        while (jsonReader.hasNext()) //Reads all the elements in the array
            follower.add(jsonReader.nextString());
        jsonReader.endArray(); //Removes the ']'

        return follower;
    }

    /**
     * Reads a comment json array
     * @param jsonReader reader to read from a file
     * @return a set of comments
     */
    private static Set<Comment> getCommentSetFromJson(JsonReader jsonReader) throws IOException{
        //Reads the name
        jsonReader.nextName();
        //Removes '['
        jsonReader.beginArray();

        Set<Comment> comments = ConcurrentHashMap.newKeySet(); //Creates a concurrent set
        Gson gson = new Gson();
        int nextId = 0;
        //Reads all the elements in the array
        while (jsonReader.hasNext()) {
            //Reads a comment from the json array
            Comment comment = gson.fromJson(jsonReader, Comment.class);

            comments.add(comment);

            //Checks if the id of the current comment is grater
            //then next id inside Comment class
            if (comment.getId() >= nextId) Comment.setNextId(comment.getId() + 1);
        }
        jsonReader.endArray(); //Removes the ']'

        return comments;
    }

    /**
     * Reads a int json array
     * @param jsonReader reader to read from a file
     * @return a set of int
     */
    private static Set<Integer> getIntegerSetFromJson(JsonReader jsonReader) throws IOException {
        //Reads the name
        jsonReader.nextName();
        //Removes '['
        jsonReader.beginArray();

        Set<Integer> follower = ConcurrentHashMap.newKeySet(); //Creates a concurrent set
        while (jsonReader.hasNext()) //Reads all the elements in the array
            follower.add(jsonReader.nextInt());
        jsonReader.endArray(); //Removes the ']'

        return follower;
    }

    /**
     * Reads a string json array
     * @param jsonReader reader to read from a file
     * @return a list of string
     */
    private static List<String> getStringListFromJson(JsonReader jsonReader) throws IOException {
        //Reads the name
        jsonReader.nextName();
        //Removes the '['
        jsonReader.beginArray();

        List<String> stringList = new ArrayList<>();
        while (jsonReader.hasNext()) //Reads all the elements in the array
            stringList.add(jsonReader.nextString());
        jsonReader.endArray(); //Removes the ']'

        return stringList;
    }

    /**
     * Reads a string from json
     * @param jsonReader reader to read from a file
     * @return the string read
     */
    private static String getStringFromJson(JsonReader jsonReader) throws IOException {
        //Reads the name
        jsonReader.nextName();
        return jsonReader.nextString();
    }

    /**
     * Reads a int from json
     * @param jsonReader reader to read from a file
     * @return the int read
     */
    private static int getIntegerFromJson(JsonReader jsonReader) throws IOException {
        //Reads the name
        jsonReader.nextName();
        return jsonReader.nextInt();
    }

    /**
     * Reads a map from json
     * @param jsonReader reader to read from a file
     * @return the map read
     */
    private static Map<String, Integer> getCommentStatFromJson(JsonReader jsonReader) throws IOException{
        //Reads the name
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
