package de.nonnull.hcu.adaxplugin.adax;

import java.util.List;

import io.vertx.core.json.JsonObject;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

@Value
@Builder
public class ContentResponse {
    @NonNull
    private final List<Home> homes;
    @NonNull
    private final List<Room> rooms;

    public static ContentResponse fromJsonObject(@NonNull JsonObject jsonObject) {
        final var homes = Home.fromJsonArray(jsonObject.getJsonArray("homes"));
        final var rooms = Room.fromJsonArray(jsonObject.getJsonArray("rooms"));
        return builder().homes(homes).rooms(rooms).build();
    }
}
