/**
 * This class implements all the functionality to receive notifications
 * when changes the follower list
 */

import java.rmi.RemoteException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CallbackHandler implements CallbackHandlerInterface {
    private final Map<String, NotifyNewFollower> registeredClient;

    public CallbackHandler() {
        registeredClient = new ConcurrentHashMap<>();
    }

    /**
     * Registers the client to receive the callbacks
     * @param user the username of the user who wants to receive notifications
     * @param clientStub stub to notify the client
     */
    public void registerForCallback(String user, NotifyNewFollower clientStub) throws RemoteException {
        registeredClient.putIfAbsent(user, clientStub);
        System.out.println("Client registered");
    }

    /**
     * Unregisters the client from receiving the callbacks
     * @param user the username of the user who wants to unregister
     */
    public synchronized void unregisterForCallback(String user) throws RemoteException {
        if (registeredClient.remove(user) != null)
            System.out.println("Client unregistered");
        else
            System.err.println("Unable to register client");
    }

    /**
     * Notifies the user that he has a new follower
     * @param user the username of the user to notify
     * @param newFollower the username of the new follower
     */
    public synchronized void notifyNewFollower(String user, String newFollower) throws RemoteException {
        NotifyNewFollower userToNotify = registeredClient.get(user);

        //Checks if the client is still connected
        if (userToNotify != null)
            userToNotify.addNewFollower(newFollower);
    }

    /**
     * Notifies the user that he has lost a follower
     * @param user the username of the user to notify
     * @param exFollower the username of the lost follower
     */
    public synchronized void notifyLostFollower(String user, String exFollower) throws RemoteException {
        NotifyNewFollower userToNotify = registeredClient.get(user);

        //Checks if the client is still connected
        if (userToNotify != null)
            userToNotify.removeFollower(exFollower);
    }
}
