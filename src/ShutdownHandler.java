/**
 *	Handler for termination that saves the state of the server
 *	when Ctrl+C is pressed or even when the server is terminated.
 *  Ensures that is always possible to recover the state of the server
 *  after a reboot
 */

import java.io.IOException;
import java.nio.channels.Selector;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ShutdownHandler {
    private final String usersFile;
    private final String postsFile;
    private final Map<String, User> users;
    private final Map<Integer, Post> posts;
    private final ThreadPoolExecutor threadPool;
    private final List<Thread> activeThreads;
    private final AtomicBoolean stateChanged;
    private final Selector selector;

    private final int threadPoolTimeout;

    public ShutdownHandler(String usersFile, String postsFile, Map<String, User> users, Map<Integer, Post> posts, ThreadPoolExecutor threadPool, List<Thread> activeThreads, AtomicBoolean stateChanged, Selector selector, int threadPoolTimeout) {
        this.usersFile = usersFile;
        this.postsFile = postsFile;
        this.users = users;
        this.posts = posts;
        this.threadPool = threadPool;
        this.activeThreads = activeThreads;
        this.stateChanged = stateChanged;
        this.selector = selector;
        this.threadPoolTimeout = threadPoolTimeout;

        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                //Closes the selector
                try {selector.close();} catch (IOException ignored) {}

                //Shutdowns the threadpool
                threadPool.shutdown();

                //Interrupts every thread active
                for (Thread thread : activeThreads)
                    thread.interrupt();

                try {
                    boolean terminated = threadPool.awaitTermination(threadPoolTimeout, TimeUnit.MILLISECONDS);
                    if (!terminated) threadPool.shutdownNow();
                } catch (InterruptedException e) {
                    //If threadpool isn't terminated forces the shutdown
                    if (!threadPool.isTerminated()) threadPool.shutdownNow();
                }

                //If the state changed saves the state of the server
                if (stateChanged.get()) {
                    //Saves the server state
                    SaveState.serverStateToJson(users, usersFile);
                    SaveState.serverStateToJson(posts, postsFile);

                    stateChanged.set(false);
                }

            }
        });

    }
}