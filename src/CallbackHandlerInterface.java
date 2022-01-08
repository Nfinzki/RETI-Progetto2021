/**
 * This interface defines methods to register and unregister for callbacks
 */

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface CallbackHandlerInterface extends Remote {
    /**
     * Registers the client to receive the callbacks
     * @param user the username of the user who wants to receive notifications
     * @param clientStub stub to notify the client
     */
    void registerForCallback(String user, NotifyNewFollower clientStub) throws RemoteException;

    /**
     * Unregisters the client from receiving the callbacks
     * @param user the username of the user who wants to unregister
     */
    void unregisterForCallback(String user) throws RemoteException;
}
