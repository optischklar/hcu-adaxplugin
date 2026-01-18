package de.nonnull.hcu.adaxplugin.adax;

import java.util.List;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

@Value
@Builder
public class Home {
    public static final Home DEFAULT_HOME = builder().id(0).name("No Home").build();

    private final int id;
    private final String name;

    public static List<Home> fromJsonArray(JsonArray a) {
        if (a == null) {
            return List.of();
        }
        return a.stream().map(JsonObject.class::cast).map(Home::fromJsonObject).toList();
    }

    public static Home fromJsonObject(@NonNull JsonObject o) {
        return builder().id(o.getInteger("id")).name(o.getString("name")).build();
    }
}
