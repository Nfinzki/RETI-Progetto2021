import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.Map;

/**
 *	Handler for termination that saves the state of the server
 *	when Ctrl+C is pressed or even when the server is terminated.
 *  Ensures that is always possible to recover the state of the server
 *  after a reboot
 */

public class ShutdownHandler {
    private final String usersFile;
    private final String postsFile;
    private final Map<String, User> users;
    private final Map<Integer, Post> posts;
    //TODO Aggiungere i thread da interrompere

    public ShutdownHandler(String usersFile, String postsFile, Map<String, User> users, Map<Integer, Post> posts) {
        this.usersFile = usersFile;
        this.postsFile = postsFile;
        this.users = users;
        this.posts = posts;

        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                //Saves the server state
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                serverStateToJson(gson, usersFile, users);
                serverStateToJson(gson, postsFile, posts);
            }
        });
    }

    private <K, V> void serverStateToJson(Gson gson, String jsonFile, Map<K, V> map) {
        try (WritableByteChannel out = Channels.newChannel(new FileOutputStream(jsonFile))){
            //Initializes ByteBuffer
            int bufferSize = 1024 * 16;
            ByteBuffer buffer = ByteBuffer.allocate(bufferSize);

            //Writes first '[' to open the json file
            buffer.put("[\n".getBytes());

            boolean firstEntry = true;
            //Writes users to the json file
            for(V value : map.values()) {
                //Writes the ',' before every new user after the first
                if (!firstEntry) buffer.put(",\n".getBytes());

                //Serializes user
                String serializedUser = gson.toJson(value);

                //Writes serialized user to the buffer
                buffer.put(serializedUser.getBytes());

                //Sets the buffer in read mode to write to the file
                buffer.flip();
                //Writes to the file
                while(buffer.hasRemaining()) out.write(buffer);

                //Sets the buffer in write mode
                buffer.clear();

                firstEntry = false;
            }

            //Writes ']' to close the json file
            buffer.put("\n]".getBytes());
            //Sets the buffer in read mode to write to the file
            buffer.flip();
            //Writes to the file
            while(buffer.hasRemaining()) out.write(buffer);

        } catch (FileNotFoundException e) {
            System.err.println("FileNotFoundException while saving the server state: " + e.getMessage());
            System.exit(1);
        } catch (IOException e) {
            System.err.println("IOException while saving the server state: " + e.getMessage());
            System.exit(1);
        }
    }
}