/**
 * This interface defines a method to register a new user to the server
 */

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface RegisterInterface extends Remote {
    /**
     * Register a new user to the server
     *
     * @param username username of the new user
     * @param password password of the new user
     * @param tag      list of tag of interests (maxium 5)
     * @return -1 if there is an error server side,
     * 0 if the registration completed correctly
     * 1 if password field is empty
     * 2 if tag list is empty
     * 3 if tag list have more than 5 element
     * 4 if the user is already registered
     */
    int register(String username, String password, List<String> tag) throws RemoteException;
}