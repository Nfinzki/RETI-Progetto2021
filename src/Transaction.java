import com.google.gson.Gson;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
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

    public void toJsonFile(JsonWriter writer) throws IOException {
        Gson gson = new Gson();

        writer.beginObject();
        writer.name("action").value(action);

        writer.name("timestamp");
        gson.toJson(timestamp, Date.class, writer);

        writer.endObject();
        writer.flush();
    }
}
