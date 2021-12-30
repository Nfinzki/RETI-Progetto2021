import com.google.gson.stream.JsonWriter;

import java.io.IOException;

public interface BufferedSerialization {
    void toJsonFile(JsonWriter writer) throws IOException;
}
