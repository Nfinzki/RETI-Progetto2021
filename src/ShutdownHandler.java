import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;

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
    private final List<Thread> activeThreads;
    private final AtomicBoolean stateChanged;

    public ShutdownHandler(String usersFile, String postsFile, Map<String, User> users, Map<Integer, Post> posts, ThreadPoolExecutor threadPool, List<Thread> activeThreads, AtomicBoolean stateChanged) {
        this.usersFile = usersFile;
        this.postsFile = postsFile;
        this.users = users;
        this.posts = posts;
        this.threadPool = threadPool;
        this.activeThreads = activeThreads;
        this.stateChanged = stateChanged;

        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                threadPool.shutdown();

                for (Thread thread : activeThreads)
                    thread.interrupt();

                if (stateChanged.get()) {
                    //Saves the server state
                    SaveState.serverStateToJson(users, usersFile);
                    SaveState.serverStateToJson(posts, postsFile);
                    stateChanged.set(false);
                }

                if (!threadPool.isTerminated()) threadPool.shutdownNow();
            }
        });

    }
}