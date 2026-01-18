package de.nonnull.hcu.adaxplugin.adax;

import java.util.List;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

@Value
@Builder
public class Room {
    private final int id;
    private final String name;
    private final int homeId;
    private final Boolean heatingEnabled;
    private final Integer targetTemperature;
    private final Integer temperature;

    public static List<Room> fromJsonArray(JsonArray a) {
        if (a == null) {
            return List.of();
        }
        return a.stream().map(JsonObject.class::cast).map(Room::fromJsonObject).toList();
    }

    public static Room fromJsonObject(@NonNull JsonObject o) {
        return builder()
                .id(o.getInteger("id"))
                .name(o.getString("name"))
                .homeId(o.getInteger("homeId"))
                .heatingEnabled(o.getBoolean("heatingEnabled"))
                .targetTemperature(o.getInteger("targetTemperature"))
                .temperature(o.getInteger("temperature"))
                .build();
    }
}
