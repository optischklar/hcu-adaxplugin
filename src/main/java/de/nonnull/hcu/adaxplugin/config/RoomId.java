package de.nonnull.hcu.adaxplugin.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.Value;

@Value
public class RoomId {
    private final long homeId;
    private final long roomId;

    @JsonCreator
    public RoomId(@JsonProperty("homeId") long aHomeId, @JsonProperty("roomId") long aRoomId) {
        homeId = aHomeId;
        roomId = aRoomId;
    }

    @JsonValue
    public String toIdentifier() {
        return String.format("%d-%d", homeId, roomId);
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
