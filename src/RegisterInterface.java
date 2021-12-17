import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface RegisterInterface extends Remote {
    int register(String username, String password, List<String> tag) throws RemoteException;
}
