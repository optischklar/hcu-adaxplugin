package de.nonnull.hcu.adaxplugin.service;

import java.time.Instant;

import io.vertx.core.json.JsonObject;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.NonNull;
import lombok.Value;

@Value
@Builder
public class HcuRoomMeasuringValues {
    public static final String WINDOW_STATE_OPEN = "OPEN";

    @NonNull @Default
    private final Instant timestamp = Instant.now();
    private final String groupId;
    private final Double windowOpenTemperature;
    private final Double setPointTemperature;
    private final Double actualTemperature;
    private final String windowState;

    public boolean isWindowOpen() {
        return WINDOW_STATE_OPEN.equals(windowState);
    }

    public static HcuRoomMeasuringValues fromGroupJsonObject(@NonNull JsonObject group) {
        return HcuRoomMeasuringValues.builder()
                .groupId(group.getString("id"))
                .windowOpenTemperature(group.getDouble("windowOpenTemperature"))
                .setPointTemperature(group.getDouble("setPointTemperature"))
                .actualTemperature(group.getDouble("actualTemperature"))
                .windowState(group.getString("windowState"))
                .build();
    }
}
