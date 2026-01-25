package de.nonnull.hcu.adaxplugin.util;

import java.io.IOException;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import io.vertx.core.json.JsonObject;

public class VertxJsonObjectDeserializer extends JsonDeserializer<JsonObject> {

    @Override
    public JsonObject deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JacksonException {
        final var node = p.getCodec().readTree(p);
        return new JsonObject(node.toString());
    }

}
