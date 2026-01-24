package de.nonnull.hcu.adaxplugin.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.eq3.plugin.domain.device.Device;
import de.eq3.plugin.domain.features.ActualTemperature;
import de.eq3.plugin.domain.features.IFeature;
import de.eq3.plugin.serialization.Feature;
import de.nonnull.hcu.adaxplugin.adax.model.ContentResponse;
import de.nonnull.hcu.adaxplugin.adax.model.Home;
import de.nonnull.hcu.adaxplugin.config.RoomId;
import de.nonnull.hcu.adaxplugin.handler.PeriodicHandler;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class RoomMeasuringValuesCache {

    private static final Logger LOGGER = LogManager.getLogger(PeriodicHandler.class);

    private final DeviceService deviceService;

    @Data
    private static class Entry {
        private AdaxRoomMeasuringValues adaxValues;
        private HcuRoomMeasuringValues hcuValues;
    }

    private final Map<RoomId, Entry> cache = new HashMap<>();

    public void putAdaxValuesFromContent(@NonNull ContentResponse content) {
        final var idHomeMap = content.getHomes().stream().collect(Collectors.toMap(Home::getId, Function.identity()));
        content.getRooms().forEach(room -> {
            final var home = idHomeMap.getOrDefault(room.getHomeId(), Home.DEFAULT_HOME);
            final var roomId = new RoomId(home.getId(), room.getId());
            final var values = AdaxRoomMeasuringValues.fromRoom(room);
            putAdaxValues(roomId, values);
        });
        LOGGER.info("Updated cached ADAX values of {} rooms.", content.getRooms().size());
    }

    public void putAdaxValues(@NonNull RoomId roomId, AdaxRoomMeasuringValues values) {
        synchronized (roomId) {
            final var entry = cache.computeIfAbsent(roomId, k -> new Entry());
            entry.setAdaxValues(values);
        }
    }

    public void putHcuValues(@NonNull RoomId roomId, HcuRoomMeasuringValues values) {
        synchronized (roomId) {
            final var entry = cache.computeIfAbsent(roomId, k -> new Entry());
            entry.setHcuValues(values);
        }
    }

    /**
     * Compares the actual temperature of the given {@link Device} with the actual
     * temperature of the cached ADAX value.
     * 
     * @param device the {@link Device}, must not be <code>null</code>
     * @return <code>true</code> if the temperature of the given {@link Device}
     *         differs from the actual temperature of the cached ADAX value
     */
    public boolean actualTemperatureHasChanged(@NonNull Device device) {
        final var deviceActualTemperature = device.getFeatures().stream()
                .map(IFeature::getType)
                .filter(t -> t == Feature.ACTUAL_TEMPERATURE)
                .map(ActualTemperature.class::cast)
                .map(ActualTemperature::getActualTemperature)
                .findAny()
                .orElse(null);
        final var adaxActualTemperature = deviceService.parseRoomId(device.getDeviceId())
                .flatMap(this::findAdaxRoomMeasuringValues)
                .map(AdaxRoomMeasuringValues::getTemperature)
                .map(deviceService::convertTemperature)
                .orElse(null);
        return Objects.equals(deviceActualTemperature, adaxActualTemperature);
    }

    public Optional<AdaxRoomMeasuringValues> findAdaxRoomMeasuringValues(@NonNull RoomId roomId) {
        return findEntry(roomId).map(Entry::getAdaxValues);
    }

    private Optional<Entry> findEntry(@NonNull RoomId roomId) {
        return Optional.ofNullable(cache.get(roomId));
    }
}
