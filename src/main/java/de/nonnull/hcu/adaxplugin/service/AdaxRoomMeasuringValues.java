package de.nonnull.hcu.adaxplugin.service;

import java.time.Instant;

import de.nonnull.hcu.adaxplugin.adax.model.Room;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.NonNull;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class AdaxRoomMeasuringValues {
    @NonNull @Default
    private final Instant timestamp = Instant.now();
    private final Boolean heatingEnabled;
    private final Integer targetTemperature;
    private final Integer temperature;

    public static AdaxRoomMeasuringValues fromRoom(@NonNull Room room) {
        return AdaxRoomMeasuringValues.builder()
                .heatingEnabled(room.getHeatingEnabled())
                .targetTemperature(room.getTargetTemperature())
                .temperature(room.getTemperature())
                .build();
    }

}
