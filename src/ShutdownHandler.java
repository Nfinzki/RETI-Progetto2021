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
    private final Thread saveStateThread;

    public ShutdownHandler(String usersFile, String postsFile, Map<String, User> users, Map<Integer, Post> posts, ThreadPoolExecutor threadPool, Thread revenueThread, Thread saveStateThread) {
        this.usersFile = usersFile;
        this.postsFile = postsFile;
        this.users = users;
        this.posts = posts;
        this.threadPool = threadPool;
        this.revenueThread = revenueThread;
        this.saveStateThread = saveStateThread;

        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                threadPool.shutdown();
                revenueThread.interrupt();
                saveStateThread.interrupt();

                //TODO Conviene fare un salvataggio di sicurezza? Oppure mi fido del booleano?
                //Saves the server state
                SaveState.serverStateToJson(users, usersFile);
                SaveState.serverStateToJson(posts, postsFile);

                if (!threadPool.isTerminated()) threadPool.shutdownNow();
            }
        });

    }
}