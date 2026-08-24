package io.github.mizar107.zapegruntime.director;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

/** Bounded duplicate-key-rejecting reader for Director authority data. */
final class DirectorStrictJsonDocument {

    private static final int MAX_DEPTH = 12;
    private static final int MAX_VALUES = 512;
    private static final int MAX_STRING_LENGTH = 256;

    private DirectorStrictJsonDocument() {}

    static JsonElement parse(Reader source) {
        try {
            JsonReader reader = new JsonReader(source);
            reader.setLenient(false);
            Counter counter = new Counter();
            JsonElement value = readValue(reader, 0, counter);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new JsonParseException("trailing JSON content");
            }
            return value;
        } catch (IOException | NumberFormatException invalid) {
            throw new JsonParseException("invalid strict Director JSON", invalid);
        }
    }

    private static JsonElement readValue(JsonReader reader, int depth, Counter counter)
            throws IOException {
        if (depth > MAX_DEPTH) {
            throw new JsonParseException("Director JSON exceeds maximum nesting depth");
        }
        counter.increment();
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> readObject(reader, depth + 1, counter);
            case BEGIN_ARRAY -> readArray(reader, depth + 1, counter);
            case STRING -> new JsonPrimitive(readBoundedString(reader));
            case NUMBER -> new JsonPrimitive(readNumber(reader));
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> {
                reader.nextNull();
                yield JsonNull.INSTANCE;
            }
            default -> throw new JsonParseException("unexpected JSON token: " + reader.peek());
        };
    }

    private static JsonObject readObject(JsonReader reader, int depth, Counter counter)
            throws IOException {
        JsonObject object = new JsonObject();
        Set<String> keys = new HashSet<>();
        reader.beginObject();
        while (reader.hasNext()) {
            String key = readBoundedName(reader);
            if (!keys.add(key)) {
                throw new JsonParseException("duplicate JSON key: " + key);
            }
            object.add(key, readValue(reader, depth, counter));
        }
        reader.endObject();
        return object;
    }

    private static JsonArray readArray(JsonReader reader, int depth, Counter counter)
            throws IOException {
        JsonArray array = new JsonArray();
        reader.beginArray();
        while (reader.hasNext()) {
            array.add(readValue(reader, depth, counter));
        }
        reader.endArray();
        return array;
    }

    private static String readBoundedName(JsonReader reader) throws IOException {
        String value = reader.nextName();
        if (value.length() > MAX_STRING_LENGTH) {
            throw new JsonParseException("JSON key exceeds maximum length");
        }
        return value;
    }

    private static String readBoundedString(JsonReader reader) throws IOException {
        String value = reader.nextString();
        if (value.length() > MAX_STRING_LENGTH) {
            throw new JsonParseException("JSON string exceeds maximum length");
        }
        return value;
    }

    private static BigDecimal readNumber(JsonReader reader) throws IOException {
        String encoded = reader.nextString();
        if (encoded.length() > 64) {
            throw new JsonParseException("JSON number exceeds maximum length");
        }
        return new BigDecimal(encoded);
    }

    private static final class Counter {
        private int values;

        private void increment() {
            if (++values > MAX_VALUES) {
                throw new JsonParseException("Director JSON exceeds maximum value count");
            }
        }
    }
}
