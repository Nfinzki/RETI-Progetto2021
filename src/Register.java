import java.rmi.RemoteException;
import java.util.*;

public class Register implements RegisterInterface{
    private final Map<String, User> users;


    public Register() {
        this.users = new HashMap<>();
    }

    public int register(String username, String password, List<String> tag) throws RemoteException {
        if (username == null || password == null || tag == null) throw new NullPointerException();

        if (!users.containsKey(username) && !password.equals("") && tag.size() > 0 && tag.size() < 6) {
            String []tags = new String[tag.size()];
            for (int i = 0; i < tag.size(); i++) {
                tags[i] = tag.get(i).toLowerCase();
            }

            users.put(username, new User(username, password, tags));
            return 0;
        } else {
            if (password.equals("")) return 1; //Password field is empty
            if (tag.size() == 0) return 2; //No tag inserted
            if (tag.size() >= 6) return 3; //Too many tag

            return 4; //User already registered
        }
    }
}
