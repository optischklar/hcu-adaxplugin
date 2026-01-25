package de.nonnull.hcu.adaxplugin.util;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import io.vertx.core.json.JsonObject;

public class VertxJsonObjectSerializer extends JsonSerializer<JsonObject> {

    @Override
    public void serialize(JsonObject value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeObject(value.getMap());
    }

}
