package de.nonnull.hcu.adaxplugin.handler;

import static de.nonnull.hcu.adaxplugin.PluginContext.PLUGIN_ID;

import java.util.UUID;
import java.util.stream.Collectors;

import de.eq3.plugin.domain.device.Device;
import de.eq3.plugin.domain.features.IFeature;
import de.eq3.plugin.domain.status.StatusEvent;
import de.eq3.plugin.serialization.Feature;
import de.eq3.plugin.serialization.PluginMessage;
import de.eq3.plugin.serialization.PluginMessageType;
import de.nonnull.hcu.adaxplugin.PluginContext;
import de.nonnull.hcu.adaxplugin.config.ActualTemperatureHandling;
import de.nonnull.hcu.adaxplugin.config.RoomConfig;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class PeriodicHandler implements Handler<Long> {

    private final Vertx vertx;
    private final PluginContext context;

    @Override
    public void handle(Long timerId) {
        final var optConfig = context.getPersistenceService().getConfiguration();
        if (optConfig.isEmpty()) {
            LOGGER.debug("Skip querying actual temperatures: no configuration present");
            return;
        }

        final var config = optConfig.get();
        if(!config.isAdaxCredentialsComplete() || !config.isRoomConfigurationInitialized()) {
            LOGGER.debug("Skip querying actual temperatures: plugin not configured");
            return;
        }

        final var actualTemperatureRooms = config.getRoomConfigurations().values().stream()
                .filter(this::handlesActualTemperature).toList();
        if (actualTemperatureRooms.isEmpty()) {
            LOGGER.debug("Skip querying actual temperatures: no rooms that handle the actual temperature");
            return;
        }

        context.getAdaxClient().getContent(ar -> {
            if (ar.failed()) {
                final var cause = ar.cause();
                LOGGER.error("Couldn't query ADAX API: {}", cause.getMessage(), cause);
                return;
            }

            final var valuesCache = context.getRoomMeasuringValuesCache();

            final var content = ar.result();
            final var devices = context.getDeviceService().createDevices(actualTemperatureRooms, content)
                    .filter(this::hasActualTemperature)
                    .filter(valuesCache::actualTemperatureHasChanged)
                    .collect(Collectors.toSet());

            valuesCache.putAdaxValuesFromContent(content);

            devices.forEach(this::sendStatusEvent);
        });
    }

    private boolean handlesActualTemperature(RoomConfig config) {
        return config.getActualTemperatureHandling() == ActualTemperatureHandling.INCLUDE
                || config.getActualTemperatureHandling() == ActualTemperatureHandling.EXTRA_DEVICE;
    }

    private boolean hasActualTemperature(Device device) {
        return device.getFeatures().stream().map(IFeature::getType).anyMatch(t -> t == Feature.ACTUAL_TEMPERATURE);
    }

    private void sendStatusEvent(Device device) {
        final var statusEvent = new StatusEvent(device.getDeviceId(), device.getFeatures());
        LOGGER.debug("Sending status event {}", statusEvent);
        final var message = new PluginMessage<>(UUID.randomUUID().toString(), PLUGIN_ID, PluginMessageType.STATUS_EVENT,
                statusEvent);
        vertx.eventBus().send(context.getWebSocketHandlerId(), JsonObject.mapFrom(message).encode());
    }
}
