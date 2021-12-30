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
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                //serverStateToJson(gson, usersFile, users);
                try {
                    prova("prova.json");
                } catch (IOException e) {
                    e.printStackTrace();
                }
                serverStateToJson(gson, postsFile, posts);

                if (!threadPool.isTerminated()) threadPool.shutdownNow();
            }
        });

    }

    private void prova(String jsonFile) throws IOException {
        JsonWriter writer = new JsonWriter(new OutputStreamWriter(new FileOutputStream(jsonFile)));
        writer.setIndent("  ");

        writer.beginArray();
        for (User p : users.values())
            p.toJsonFile(writer);
        writer.endArray();
        writer.flush();
        writer.close();

    }
    //TODO Creare un'interfaccia BufferedSerialization che implementa il metodo toJsonFile
    //Nella Map qua sotto devo mettere V implements BufferedSerialization
    //Dovrebbe essere fatta così. Poi basta cambiare la chiamata sopra (non serve più Gson gson)

    private <K, V extends BufferedSerialization> void serverStateToJson(Gson gson, String jsonFile, Map<K, V> map) {
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
                String serializedObj = gson.toJson(value);

                //Writes serialized user to the buffer
                buffer.put(serializedObj.getBytes());

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