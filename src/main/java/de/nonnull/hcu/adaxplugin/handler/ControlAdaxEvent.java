package de.nonnull.hcu.adaxplugin.handler;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.nonnull.hcu.adaxplugin.config.RoomId;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

@Value
@Builder
public class ControlAdaxEvent {
    private final RoomId roomId;
    private final Double setPointTemperature;
    private final boolean heatingEnabled;

    @JsonCreator
    public ControlAdaxEvent(
            @JsonProperty("roomId") @NonNull RoomId aRoomId,
            @JsonProperty("setPointTemperature") Double aSetPointTemperature,
            @JsonProperty("heatingEnabled") boolean aHeatingEnabled) {
        roomId = aRoomId;
        setPointTemperature = aSetPointTemperature;
        heatingEnabled = aHeatingEnabled;
    }

}
