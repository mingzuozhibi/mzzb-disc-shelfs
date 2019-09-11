package mingzuozhibi.discshelfs;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import javax.persistence.Version;
import java.io.IOException;
import java.time.Instant;

public abstract class GsonUtils {

    public static Gson getGson() {
        GsonBuilder gson = new GsonBuilder();
        gson.setExclusionStrategies(new ExclusionStrategy() {
            @Override
            public boolean shouldSkipField(FieldAttributes f) {
                return f.getAnnotation(Version.class) != null;
            }

            @Override
            public boolean shouldSkipClass(Class<?> clazz) {
                return false;
            }
        });
        gson.registerTypeAdapter(Instant.class, new TypeAdapter<Instant>() {
            @Override
            public void write(JsonWriter out, Instant instant) throws IOException {
                if (instant != null) {
                    out.value(instant.toEpochMilli());
                }
            }

            @Override
            public Instant read(JsonReader in) throws IOException {
                if (in.hasNext()) {
                    return Instant.ofEpochMilli(in.nextLong());
                }
                return null;
            }
        });
        return gson.create();
    }

}
