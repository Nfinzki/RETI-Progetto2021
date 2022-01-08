/**
 * This interface defines a method for writing objects in json format
 * to a file
 */

import com.google.gson.stream.JsonWriter;

import java.io.IOException;

public interface BufferedSerialization {
    /**
     * Writes the current object in json format to a file
     * @param writer writer used to write the object as json object
     */
    void toJsonFile(JsonWriter writer) throws IOException;
}
