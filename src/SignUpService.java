/**
 * This class implements the registration service
 */

import java.rmi.RemoteException;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class SignUpService implements RegisterInterface {
    private final Map<String, User> users;

    public SignUpService(Map<String, User> users) {
        this.users = users;
    }

    /**
     * Registers a new user to the server
     * @param username username of the new user
     * @param password password of the new user
     * @param tag      list of tag of interests (maxium 5)
     * @return -1 if there is an error server side,
     *          0 if the registration completed correctly
     *          1 if password field is empty
     *          2 if tag list is empty
     *          3 if tag list have more than 5 element
     *          4 if the user is already registered
     */
    public int register(String username, String password, List<String> tag) throws RemoteException {
        if (username == null || password == null || tag == null) throw new NullPointerException();

        if (!password.equals("") && tag.size() > 0 && tag.size() < 6) {
            String []tags = new String[tag.size()];

            //Adds the tags in an array
            for (int i = 0; i < tag.size(); i++) {
                tags[i] = tag.get(i).toLowerCase();
            }

            try {
                //Tries to register a new user
                //The password is stored as the hash value of the concatenation of the username and the password
                if (users.putIfAbsent(username, new User(username, Hash.bytesToHex(Hash.sha256(username + password)), tags)) != null)
                    return 4; //User already registered

                return 0; //User registered correctly
            } catch (NoSuchAlgorithmException e) {
                System.err.println("Error while hashing the password: " + e.getMessage());
                return -1; //Error server side
            }
        } else {
            if (password.equals("")) return 1; //Password field is empty
            if (tag.size() == 0) return 2; //No tag inserted

            return 3; //Too many tag
        }
    }
}
