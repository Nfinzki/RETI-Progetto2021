import java.rmi.RemoteException;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class SignUpService implements RegisterInterface{
    private final Map<String, User> users;

    public SignUpService(Map<String, User> users, Map<Integer, Post> posts) {
        this.users = users;
    }

    public int register(String username, String password, List<String> tag) throws RemoteException {
        if (username == null || password == null || tag == null) throw new NullPointerException();

        if (!password.equals("") && tag.size() > 0 && tag.size() < 6) {
            String []tags = new String[tag.size()];
            for (int i = 0; i < tag.size(); i++) {
                tags[i] = tag.get(i).toLowerCase();
            }

            try {
                if (users.putIfAbsent(username, new User(username, Hash.bytesToHex(Hash.sha256(username + password)), tags)) != null)
                    return 4; //User already registered

                return 0;
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
