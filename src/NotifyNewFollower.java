import java.rmi.Remote;
import java.rmi.RemoteException;

public interface NotifyNewFollower extends Remote {

    void notifyNewFollower(String follower) throws RemoteException;
    void notifyLostFollower(String exFollower) throws RemoteException;
}

