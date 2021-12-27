import com.google.gson.Gson;

import java.util.Date;

public class Transaction {
    private final String action;
    private final Date timestamp;

    public Transaction(String action, Date timestamp) {
        this.action = action;
        this.timestamp = timestamp;
    }

    public String toJson() {
        Gson gson = new Gson();
        return gson.toJson(this);
    }
}
