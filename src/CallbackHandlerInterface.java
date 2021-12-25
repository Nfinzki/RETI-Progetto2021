import java.rmi.Remote;
import java.rmi.RemoteException;

public interface CallbackHandlerInterface extends Remote {
    void registerForCallback(String user, NotifyNewFollower clientStub) throws RemoteException;

    void unregisterForCallback(String user, NotifyNewFollower clientStub) throws RemoteException;
}
