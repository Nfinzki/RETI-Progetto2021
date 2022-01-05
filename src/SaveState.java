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
                if (stateChanged.get()) {
                    synchronized (posts) {
                        serverStateToJson(posts, postsFile);
                    }
                    synchronized (users) {
                        serverStateToJson(users, usersFile);
                    }
                    stateChanged.set(false);
                }
                Thread.sleep(iterationTime);
            }
        } catch (InterruptedException ignored) {}
    }

    public static <K, V extends BufferedSerialization> void serverStateToJson(Map<K, V> map, String jsonFile) {
        try (JsonWriter writer = new JsonWriter(new OutputStreamWriter(new FileOutputStream(jsonFile)))) {
            writer.setIndent("  ");
            writer.beginArray();

            //Writes users to the json file
            for(V value : map.values()) {
                value.toJsonFile(writer);
            }

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
