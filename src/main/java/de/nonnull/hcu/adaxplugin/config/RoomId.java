package de.nonnull.hcu.adaxplugin.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.Value;

@Value
public class RoomId {
    private final int homeId;
    private final int roomId;

    @JsonCreator
    public RoomId(@JsonProperty("homeId") int aHomeId, @JsonProperty("roomId") int aRoomId) {
        homeId = aHomeId;
        roomId = aRoomId;
    }

    @JsonValue
    public String toIdentifier() {
        return "%d-%d".formatted(homeId, roomId);
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static RoomId fromIdentifier(String identifier) {
        final String[] parts = identifier.split("-");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid RoomId: " + identifier);
        }
        try {
            return new RoomId(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        } catch (final NumberFormatException e) {
            throw new IllegalArgumentException("Invalid RoomId: " + identifier);
        }
    }
}
