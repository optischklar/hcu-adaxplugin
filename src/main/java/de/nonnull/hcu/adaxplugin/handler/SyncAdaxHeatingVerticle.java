package de.nonnull.hcu.adaxplugin.handler;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import de.nonnull.hcu.adaxplugin.PluginContext;
import de.nonnull.hcu.adaxplugin.adax.model.ControlResponseRoom;
import de.nonnull.hcu.adaxplugin.adax.model.ControlStatus;
import de.nonnull.hcu.adaxplugin.config.Configuration;
import de.nonnull.hcu.adaxplugin.config.RoomConfig;
import de.nonnull.hcu.adaxplugin.config.RoomId;
import de.nonnull.hcu.adaxplugin.service.HcuRoomMeasuringValues;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import lombok.Builder;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class SyncAdaxHeatingVerticle extends AbstractVerticle implements Handler<Message<JsonObject>> {

    private static final long HANDLE_BUFFER_MILLIS = 20_000L;

    @Builder(toBuilder = true)
    @Value
    private static class BufferEntry {
        @NonNull
        private final HcuRoomMeasuringValues values;
        @NonNull
        private final Instant dueTime;

        boolean isDue(@NonNull Instant reference) {
            return !dueTime.isAfter(reference);
        }
    }

    private final ConcurrentMap<RoomId, BufferEntry> buffer = new ConcurrentHashMap<>();

    @NonNull
    private final PluginContext context;

    @Override
    public void start() {
        vertx.eventBus().consumer(SyncAdaxHeatingEvent.class.getName(), this);
        vertx.setPeriodic(HANDLE_BUFFER_MILLIS, this::handleBuffer);
        LOGGER.info("{} verticle started", getClass().getSimpleName());
    }

    @Override
    public void handle(Message<JsonObject> message) {
        if (message == null || message.body() == null) {
            return;
        }

        final var event = message.body().mapTo(SyncAdaxHeatingEvent.class);
        LOGGER.debug("Got event for room {}", event.getRoomId());

        final var roomId = event.getRoomId();
        final var values = HcuRoomMeasuringValues.fromGroupJsonObject(event.getHcuHeatingGroup());
        final var entry = createEntry(roomId, values);
        buffer.merge(roomId, entry, this::mergeEntries);
    }

    private BufferEntry createEntry(@NonNull RoomId roomId, @NonNull HcuRoomMeasuringValues values) {
        final var windowClosing = context.getRoomMeasuringValuesCache().getHcuValues(roomId)
                .map(prevValues -> prevValues.isWindowClosing(values)).orElse(false);

        final Instant dueTime;
        if (windowClosing) {
            dueTime = Instant.now().plus(getWindowClosedHeatingDelayMinutes(roomId), ChronoUnit.MINUTES);
        } else {
            dueTime = Instant.now();
        }

        return new BufferEntry(values, dueTime);
    }

    private int getWindowClosedHeatingDelayMinutes(@NonNull RoomId roomId) {
        return context.getPersistenceService().getConfiguration()
                .map(Configuration::getRoomConfigurations)
                .map(rc -> rc.get(roomId))
                .map(RoomConfig::getWindowClosedHeatingDelayMinutes)
                .orElse(0);
    }

    private BufferEntry mergeEntries(@NonNull BufferEntry oldEntry, @NonNull BufferEntry newEntry) {
        final var windowOpening = oldEntry.getValues().isWindowOpening(newEntry.getValues());
        final var dueTime = windowOpening ? newEntry.getDueTime() : oldEntry.getDueTime();
        return newEntry.toBuilder().dueTime(dueTime).build();
    }

    private void handleBuffer(Long timerId) {
        LOGGER.trace("Current buffer size is {}", buffer.size());

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

        final var referenceTimestamp = Instant.now();
        final var roomIds = Set.copyOf(buffer.keySet());
        roomIds.forEach(roomId -> handleRoom(config, roomId, referenceTimestamp));
    }

    private void handleRoom(Configuration config, RoomId roomId, Instant referenceTimestamp) {
        final var roomConfig = config.getRoomConfigurations().get(roomId);

        buffer.computeIfPresent(roomId, (k, entry) -> {
            if (roomConfig == null || roomConfig.isExcludeThermostat()) {
                LOGGER.debug("Ignoring room {}: room is excluded", roomId);
                return null;
            }

            if (!entry.isDue(referenceTimestamp)) {
                LOGGER.debug("Setting heating values for room {} is not due ({})", roomId, entry.getDueTime());
                return entry;
            }

            final var values = entry.getValues();

            final var heatingEnabled = !HcuRoomMeasuringValues.WINDOW_STATE_OPEN.equals(values.getWindowState());
            var targetTemperature = context.getConversionService()
                    .convertHcuSetPointTemperatureToAdaxTargetTemperature(roomConfig,
                            values.getSetPointTemperature());

            if (targetTemperature == null) {
                if (heatingEnabled) {
                    LOGGER.error("Ignoring room {}: target temperature not set", roomId);
                    return null;
                }
                targetTemperature = 0;
            }

            if (context.getRoomMeasuringValuesCache().heatingHasChanged(roomId, heatingEnabled, targetTemperature)) {
                controlRoom(roomId, heatingEnabled, targetTemperature);
            } else {
                LOGGER.debug("Ignoring room {}: heating parameters have not changed "
                        + "(heating enabled: {}, target temperature: {})", roomId, heatingEnabled,
                        targetTemperature);
            }

            context.getRoomMeasuringValuesCache().putHcuValues(roomId, values);

            return null;
        });
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
