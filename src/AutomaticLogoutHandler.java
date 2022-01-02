import java.net.Socket;
import java.util.Map;

public class AutomaticLogoutHandler implements Runnable {
    private final Map<String, Socket> loggedUsers;
    private final int checkTime;

    public AutomaticLogoutHandler(Map<String, Socket> loggedUsers, int checkTime) {
        this.loggedUsers = loggedUsers;
        this.checkTime = checkTime;
    }

    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                for (String user : loggedUsers.keySet())
                    if (!loggedUsers.get(user).isConnected())
                        loggedUsers.remove(user);

                Thread.sleep(checkTime);
            }
        } catch (InterruptedException ignored) {}
    }
}
