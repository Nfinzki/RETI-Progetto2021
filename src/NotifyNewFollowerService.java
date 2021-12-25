import java.rmi.RemoteException;
import java.util.List;

public class NotifyNewFollowerService implements NotifyNewFollower{
    private final List<String> follower;

    public NotifyNewFollowerService(List<String> follower) {
        this.follower = follower;
    }

    public synchronized void notifyNewFollower(String follower) throws RemoteException {
        this.follower.add(follower);
    }

    public synchronized void notifyLostFollower(String exFollower) throws RemoteException {
        this.follower.remove(exFollower);
    }
}
