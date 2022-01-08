/**
 * This class implements a transaction
 */

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

    /**
     * @return transaction in json format
     */
    public synchronized String toJson() {
        Gson gson = new Gson();
        return gson.toJson(this);
    }

    /**
     * Writes the post in a file in json format
     * @param writer writer used to write the object as json object
     */
    public synchronized void toJsonFile(JsonWriter writer) throws IOException {
        Gson gson = new Gson();

        writer.beginObject();
        writer.name("action").value(action);

        writer.name("timestamp");
        gson.toJson(timestamp, Date.class, writer); //Serializes the Date class

        writer.endObject();
        writer.flush();
    }
}
