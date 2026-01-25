package de.nonnull.hcu.adaxplugin.handler;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import de.nonnull.hcu.adaxplugin.config.RoomId;
import de.nonnull.hcu.adaxplugin.util.VertxJsonObjectDeserializer;
import de.nonnull.hcu.adaxplugin.util.VertxJsonObjectSerializer;
import io.vertx.core.json.JsonObject;
import lombok.NonNull;
import lombok.Value;

@Value
public class SyncAdaxHeatingEvent {
    @NonNull
    private final RoomId roomId;

    @JsonSerialize(using = VertxJsonObjectSerializer.class)
    @JsonDeserialize(using = VertxJsonObjectDeserializer.class)
    @NonNull
    private final JsonObject hcuHeatingGroup;

    @JsonCreator
    public SyncAdaxHeatingEvent(
            @JsonProperty("roomId") @NonNull RoomId aRoomId,
            @JsonProperty("hcuHeatingGroup") @NonNull JsonObject aHcuHeatingGroup) {
        roomId = aRoomId;
        hcuHeatingGroup = aHcuHeatingGroup;
    }
}
