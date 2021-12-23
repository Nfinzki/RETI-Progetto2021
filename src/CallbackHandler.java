import java.rmi.RemoteException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CallbackHandler implements CallbackHandlerInterface {
    private final Map<String, NotifyNewFollower> registeredClient;

    public CallbackHandler() {
        registeredClient = new ConcurrentHashMap<>();
    }

    public void registerForCallback(String user, NotifyNewFollower clientStub) throws RemoteException {
        registeredClient.putIfAbsent(user, clientStub);
        System.out.println("Client registered");
    }

    public synchronized void unregisterForCallback(String user, NotifyNewFollower clientStub) throws RemoteException {
        if (registeredClient.remove(user) != null)
            System.out.println("Client unregistered");
        else
            System.out.println("Unable to register client");
    }

    public synchronized void notifyNewFollower(String user, String newFollower) throws RemoteException {
        NotifyNewFollower userToNotify = registeredClient.get(user);
        if (userToNotify != null)
            userToNotify.notifyNewFollower(newFollower);
    }

    public synchronized void notifyLostFollower(String user, String exFollower) throws RemoteException {
        NotifyNewFollower userToNotify = registeredClient.get(user);
        if (userToNotify != null)
            userToNotify.notifyLostFollower(exFollower);
    }
}
