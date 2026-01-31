package de.nonnull.hcu.adaxplugin.handler;

import de.nonnull.hcu.adaxplugin.PluginContext;
import de.nonnull.hcu.adaxplugin.adax.model.ControlResponseRoom;
import de.nonnull.hcu.adaxplugin.adax.model.ControlStatus;
import de.nonnull.hcu.adaxplugin.config.RoomId;
import de.nonnull.hcu.adaxplugin.service.HcuRoomMeasuringValues;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class SyncAdaxHeatingVerticle extends AbstractVerticle implements Handler<Message<JsonObject>> {

    @NonNull
    private final PluginContext context;

    @Override
    public void start() {
        vertx.eventBus().consumer(SyncAdaxHeatingEvent.class.getName(), this);
        LOGGER.info("{} verticle started", getClass().getSimpleName());
    }

    @Override
    public void handle(Message<JsonObject> message) {
        if (message == null || message.body() == null) {
            return;
        }

        final var event = message.body().mapTo(SyncAdaxHeatingEvent.class);
        LOGGER.debug("Got event for room {}", event.getRoomId());

        final var optConfig = context.getPersistenceService().getConfiguration();

        if (optConfig.isEmpty()) {
            LOGGER.error("no configuration present");
            return;
        }

        final var config = optConfig.get();

        if (!config.isAdaxCredentialsComplete() || !config.isRoomConfigurationInitialized()) {
            LOGGER.error("Configuration not completed");
            return;
        }

        final var roomId = event.getRoomId();
        final var roomConfig = config.getRoomConfigurations().get(roomId);

        if (roomConfig == null || roomConfig.isExcludeThermostat()) {
            LOGGER.debug("Ignoring room {}: room is excluded", roomId);
            return;
        }

        final var cache = context.getRoomMeasuringValuesCache();

        final var values = HcuRoomMeasuringValues.fromGroupJsonObject(event.getHcuHeatingGroup());

        try {
            final var heatingEnabled = !HcuRoomMeasuringValues.WINDOW_STATE_OPEN.equals(values.getWindowState());
            var targetTemperature = context.getConversionService()
                    .convertHcuSetPointTemperatureToAdaxTargetTemperature(roomConfig, values.getSetPointTemperature());

            if (targetTemperature == null) {
                if (heatingEnabled) {
                    LOGGER.error("Ignoring room {}: target temperature not set", roomId);
                    return;
                }
                targetTemperature = 0;
            }

            if (cache.heatingHasChanged(roomId, heatingEnabled, targetTemperature)) {
                controlRoom(roomId, heatingEnabled, targetTemperature);
            } else {
                LOGGER.debug("Ignoring room {}: heating parameters have not changed "
                        + "(heating enabled: {}, target temperature: {})", roomId, heatingEnabled, targetTemperature);
            }
        } finally {
            cache.putHcuValues(roomId, values);
        }
    }

    private void controlRoom(RoomId roomId, boolean heatingEnabled, int targetTemperature) {
        context.getAdaxClient().controlRoom(roomId, heatingEnabled, targetTemperature, ar -> {
            if (ar.succeeded()) {
                final var response = ar.result();
                final var status = response.getRooms().stream().filter(r -> r.getId() == roomId.getRoomId())
                        .map(ControlResponseRoom::getStatus).findAny();
                if (status.filter(s -> s == ControlStatus.OK).isPresent()) {
                    LOGGER.info("Successfully set temperature of room {} to {} with heating enabled {}",
                            roomId.toIdentifier(), targetTemperature, heatingEnabled);
                } else {
                    LOGGER.error("Failed to set temperature of room {} to {} with heating enabled {}: {}",
                            roomId.toIdentifier(), targetTemperature, heatingEnabled,
                            status.map(ControlStatus::name).orElse("not found"));
                }
            } else {
                final var cause = ar.cause();
                LOGGER.error("ADAX API - Error calling API request, cause {}", cause.getMessage(), cause);
            }
        });
    }
}
