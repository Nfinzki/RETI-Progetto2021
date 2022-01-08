/**
 * This class implements methods to add or remove a follower
 */

import java.rmi.RemoteException;
import java.util.List;

public class NotifyNewFollowerService implements NotifyNewFollower{
    private final List<String> follower;

    public NotifyNewFollowerService(List<String> follower) {
        this.follower = follower;
    }

    /**
     * Adds a new follower to the followers list
     * @param follower username of the new follower
     */
    public synchronized void addNewFollower(String follower) throws RemoteException {
        this.follower.add(follower);
    }

    /**
     * Removes a follower from the followers list
     * @param exFollower username of the follower to remove
     */
    public synchronized void removeFollower(String exFollower) throws RemoteException {
        this.follower.remove(exFollower);
    }
}
