package de.nonnull.hcu.adaxplugin.handler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.nonnull.hcu.adaxplugin.PluginContext;
import de.nonnull.hcu.adaxplugin.adax.model.ControlResponseRoom;
import de.nonnull.hcu.adaxplugin.adax.model.ControlStatus;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ControlAdaxVerticle extends AbstractVerticle implements Handler<Message<JsonObject>> {

    private static final Logger LOGGER = LogManager.getLogger(ControlAdaxVerticle.class);

    @NonNull
    private final PluginContext context;

    @Override
    public void start() {
        vertx.eventBus().consumer(ControlAdaxEvent.class.getName(), this);
        LOGGER.info("{} verticle started", getClass().getSimpleName());
    }

    @Override
    public void handle(Message<JsonObject> message) {
        if (message == null || message.body() == null) {
            return;
        }

        final var event = message.body().mapTo(ControlAdaxEvent.class);
        LOGGER.info("Got {}", event);

        context.getPersistenceService().getConfiguration().ifPresentOrElse(config -> {
            if (!config.isAdaxCredentialsComplete() || !config.isRoomConfigurationInitialized()) {
                LOGGER.error("Configuration not completed");
                return;
            }

            final var roomId = event.getRoomId();
            final var roomConfig = config.getRoomConfigurations().get(roomId);

            if (roomConfig == null || roomConfig.isExcludeThermostat()) {
                LOGGER.error("Ignoring room {}: room is excluded", roomId);
                return;
            }

            var setPointTemperature = event.getSetPointTemperature();
            if (setPointTemperature == null) {
                if (event.isHeatingEnabled()) {
                    LOGGER.error("Ignoring room {}: set point temperature not set", roomId);
                    return;
                }
                setPointTemperature = 0d;
            }

            final var targetTemperature = (int) ((setPointTemperature + roomConfig.getSetPointTemperatureOffset())
                    * 100);

            context.getAdaxClient().controlRoom(roomId, event.isHeatingEnabled(), targetTemperature, ar -> {
                if (ar.succeeded()) {
                    final var response = ar.result();
                    final var status = response.getRooms().stream().filter(r -> r.getId() == roomId.getRoomId())
                            .map(ControlResponseRoom::getStatus).findAny();
                    if (status.filter(s -> s == ControlStatus.OK).isPresent()) {
                        LOGGER.info("Successfully set temperature of room {} to {} with heating enabled {}",
                                roomId.toIdentifier(), targetTemperature, event.isHeatingEnabled());
                    } else {
                        LOGGER.error("Failed to set temperature of room {} to {} with heating enabled {}: {}",
                                roomId.toIdentifier(), targetTemperature, event.isHeatingEnabled(),
                                status.map(ControlStatus::name).orElse("not found"));
                    }
                } else {
                    final var cause = ar.cause();
                    LOGGER.error("ADAX API - Error calling API request, cause {}", cause.getMessage(), cause);
                }
            });

        }, () -> LOGGER.error("no configuration present"));
    }



}
