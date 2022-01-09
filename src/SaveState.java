/**
 * This class implements a task that periodically saves the state of the server in json files
 */

import com.google.gson.stream.JsonWriter;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class SaveState implements Runnable{
    private final Map<String, User> users;
    private final Map<Integer, Post> posts;
    private final String usersFile;
    private final String postsFile;
    private final AtomicBoolean stateChanged;
    private final int iterationTime;

    public SaveState(Map<String, User> users, Map<Integer, Post> posts, String usersFile, String postsFile, AtomicBoolean stateChanged, int iterationTime) {
        this.users = users;
        this.posts = posts;
        this.usersFile = usersFile;
        this.postsFile = postsFile;
        this.stateChanged = stateChanged;
        this.iterationTime = iterationTime;
    }

    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                if (stateChanged.get()) { //If the state of the server has changed
                    System.out.println("Saving state to json files...");

                    synchronized (posts) {
                        serverStateToJson(posts, postsFile);
                    }
                    synchronized (users) {
                        serverStateToJson(users, usersFile);
                    }
                    stateChanged.set(false);
                }

                System.out.println("Saved correctly");

                //Waits until next iteration
                Thread.sleep(iterationTime);
            }
        } catch (InterruptedException ignored) {}
    }

    /**
     * Saves the state of the server to a json file
     * @param map map to save
     * @param jsonFile file where to save the state
     */
    public static <K, V extends BufferedSerialization> void serverStateToJson(Map<K, V> map, String jsonFile) {
        try (JsonWriter writer = new JsonWriter(new OutputStreamWriter(new FileOutputStream(jsonFile)))) {
            writer.setIndent("  ");

            //Writes '['
            writer.beginArray();

            //Writes value to the json file
            for(V value : map.values()) {
                value.toJsonFile(writer);
            }

            //Writes ']'
            writer.endArray();
            writer.flush();
        } catch (FileNotFoundException e) {
            System.err.println("FileNotFoundException while saving the server state: " + e.getMessage());
            System.exit(1);
        } catch (IOException e) {
            System.err.println("IOException while saving the server state: " + e.getMessage());
            System.exit(1);
        }
    }
}
