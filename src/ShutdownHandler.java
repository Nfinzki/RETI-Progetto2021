import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonWriter;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

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
    private final ThreadPoolExecutor threadPool;
    private final Thread revenueThread;

    public ShutdownHandler(String usersFile, String postsFile, Map<String, User> users, Map<Integer, Post> posts, ThreadPoolExecutor threadPool, Thread revenueThread) {
        this.usersFile = usersFile;
        this.postsFile = postsFile;
        this.users = users;
        this.posts = posts;
        this.threadPool = threadPool;
        this.revenueThread = revenueThread;

        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                threadPool.shutdown();
                revenueThread.interrupt();

                //Saves the server state
                serverStateToJson(users, usersFile);
                serverStateToJson(posts, postsFile);

                if (!threadPool.isTerminated()) threadPool.shutdownNow();
            }
        });

    }

    private <K, V extends BufferedSerialization> void serverStateToJson(Map<K, V> map, String jsonFile) {
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