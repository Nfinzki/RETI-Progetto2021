/**
 * This interface defines the methods to add a new follower or remove a follower
 */

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface NotifyNewFollower extends Remote {
    /**
     * Adds a new follower to the followers list
     * @param follower username of the new follower
     */
    void addNewFollower(String follower) throws RemoteException;

    /**
     * Removes a follower from the followers list
     * @param exFollower username of the follower to remove
     */
    void removeFollower(String exFollower) throws RemoteException;
}

