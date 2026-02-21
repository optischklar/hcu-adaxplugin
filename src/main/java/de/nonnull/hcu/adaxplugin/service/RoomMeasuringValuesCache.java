package de.nonnull.hcu.adaxplugin.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import de.eq3.plugin.domain.device.Device;
import de.eq3.plugin.domain.features.ActualTemperature;
import de.eq3.plugin.serialization.Feature;
import de.nonnull.hcu.adaxplugin.adax.model.ContentResponse;
import de.nonnull.hcu.adaxplugin.adax.model.Home;
import de.nonnull.hcu.adaxplugin.config.RoomId;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class RoomMeasuringValuesCache {

    private final DeviceService deviceService;
    private final ConversionService conversionService;

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
        LOGGER.debug("Updated cached ADAX values of {} rooms.", content.getRooms().size());
    }

    public void putAdaxHeatingValues(@NonNull RoomId roomId, boolean heatingEnabled, int targetTemperature) {
        final var values = Optional.ofNullable(cache.get(roomId)).map(Entry::getAdaxValues)
                .map(AdaxRoomMeasuringValues::toBuilder)
                .orElse(AdaxRoomMeasuringValues.builder())
                .heatingEnabled(heatingEnabled)
                .targetTemperature(targetTemperature)
                .build();
        putAdaxValues(roomId, values);
    }

    public void putAdaxValues(@NonNull RoomId roomId, AdaxRoomMeasuringValues values) {
        synchronized (roomId) {
            final var entry = cache.computeIfAbsent(roomId, k -> new Entry());
            entry.setAdaxValues(values);
            LOGGER.debug("Put ADAX values for room {}", roomId);
        }
    }

    public void putHcuValues(@NonNull RoomId roomId, HcuRoomMeasuringValues values) {
        synchronized (roomId) {
            final var entry = cache.computeIfAbsent(roomId, k -> new Entry());
            entry.setHcuValues(values);
            LOGGER.debug("Put HCU values of group {} for room {}",
                    Optional.ofNullable(values).map(HcuRoomMeasuringValues::getGroupId).orElse(null), roomId);
        }
    }

    public Optional<HcuRoomMeasuringValues> getHcuValues(@NonNull RoomId roomId) {
        return Optional.ofNullable(cache.get(roomId)).map(Entry::getHcuValues);
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
                .filter(f -> f.getType() == Feature.ACTUAL_TEMPERATURE)
                .map(ActualTemperature.class::cast)
                .map(ActualTemperature::getActualTemperature)
                .findAny()
                .orElse(null);
        final var adaxActualTemperature = deviceService.parseRoomId(device.getDeviceId())
                .flatMap(this::findAdaxRoomMeasuringValues)
                .map(AdaxRoomMeasuringValues::getTemperature)
                .map(conversionService::convertAdaxToHcuTemperature)
                .orElse(null);
        return Objects.equals(deviceActualTemperature, adaxActualTemperature);
    }

    /**
     * Compares the given heating parameters with the heating parameters of the
     * cached ADAX values.
     * 
     * @param roomId            the {@link RoomId}, must not be <code>null</code>
     * @param heatingEnabled    the preferred heating enabled parameter
     * @param targetTemperature the preferred target temperature
     * @return <code>true</code> if the given heating parameters differs from the
     *         actual heating parameters of the cached ADAX values
     */
    public boolean heatingHasChanged(@NonNull RoomId roomId, boolean heatingEnabled, int targetTemperature) {
        final var values = Optional.ofNullable(cache.get(roomId)).map(Entry::getAdaxValues).orElse(null);
        if (values != null) {
            return !Objects.equals(values.getHeatingEnabled(), heatingEnabled)
                    || !Objects.equals(values.getTargetTemperature(), targetTemperature);
        }
        return true;
    }

    public Optional<AdaxRoomMeasuringValues> findAdaxRoomMeasuringValues(@NonNull RoomId roomId) {
        return findEntry(roomId).map(Entry::getAdaxValues);
    }

    private Optional<Entry> findEntry(@NonNull RoomId roomId) {
        return Optional.ofNullable(cache.get(roomId));
    }

    public List<RoomId> listRoomsAssociatedWithHcuGroupId(@NonNull String groupId) {
        return cache.entrySet().stream()
                .filter(entry -> Optional.ofNullable(entry.getValue().getHcuValues())
                        .map(HcuRoomMeasuringValues::getGroupId)
                        .filter(groupId::equals).isPresent())
                .map(e -> e.getKey())
                .collect(Collectors.toList());
    }
}
