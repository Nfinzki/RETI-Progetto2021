import com.google.gson.stream.JsonReader;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
                int id = getIdFromJson(jsonReader);

                //Reads postTitle
                String postTitle = getStringFromJson(jsonReader);

                //Reads postContent
                String postContent = getStringFromJson(jsonReader);

                //Reads comment
                List<String> comments = getStringListFromJson(jsonReader);

                //Reads upvote
                List<Integer> upvotes = getIntegerListFromJson(jsonReader);

                //Reads downvote
                List<Integer> downvotes = getIntegerListFromJson(jsonReader);

                //End post
                jsonReader.endObject();

                //Adds post to the server
                posts.put(
                        id,
                        new Post( //Create the post
                                id,
                                postTitle,
                                postContent,
                                comments,
                                upvotes,
                                downvotes
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
                int id = getIdFromJson(jsonReader);

                //Reads username
                String username = getStringFromJson(jsonReader);

                //Reads password
                String password = getStringFromJson(jsonReader);

                //Reads tags
                String[] tags = getStringListFromJson(jsonReader).toArray(new String[0]);

                //Reads followers
                List<Integer> follower = getIntegerListFromJson(jsonReader);

                //Reads followed
                List<Integer> followed = getIntegerListFromJson(jsonReader);

                //Reads blog
                List<Integer> blog = getIntegerListFromJson(jsonReader);

                //Read wallet
                double wincoin = getWincoinFromJson(jsonReader);

                //End user
                jsonReader.endObject();

                //Add the user to the server
                users.put(
                        username,
                        new User( //Creates the user
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
            //Removes ']'
            jsonReader.endArray();
        } catch (FileNotFoundException e) {
            System.err.println("Error while reading json file: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("I/O Error: " + e.getMessage());
        }
    }

    private static double getWincoinFromJson(JsonReader jsonReader) throws IOException {
        jsonReader.nextName();
        //Removes '{'
        jsonReader.beginObject();
        jsonReader.nextName();
        double wincoin = jsonReader.nextDouble();
        jsonReader.endObject(); //Removes '}'
        return wincoin;
    }

    private static List<Integer> getIntegerListFromJson(JsonReader jsonReader) throws IOException {
        jsonReader.nextName();
        //Removes '['
        jsonReader.beginArray();
        List<Integer> follower = new ArrayList<>();
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

    private static int getIdFromJson(JsonReader jsonReader) throws IOException {
        //Reads id
        jsonReader.nextName();
        return jsonReader.nextInt();
    }
}
