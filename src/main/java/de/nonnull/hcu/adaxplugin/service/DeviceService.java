package de.nonnull.hcu.adaxplugin.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.eq3.plugin.domain.device.Device;
import de.eq3.plugin.domain.features.ActualTemperature;
import de.eq3.plugin.domain.features.IFeature;
import de.eq3.plugin.domain.features.Maintenance;
import de.eq3.plugin.domain.features.SetPointTemperature;
import de.eq3.plugin.serialization.DeviceType;
import de.nonnull.hcu.adaxplugin.adax.ContentResponse;
import de.nonnull.hcu.adaxplugin.adax.Home;
import de.nonnull.hcu.adaxplugin.adax.Room;
import de.nonnull.hcu.adaxplugin.config.ActualTemperatureHandling;
import de.nonnull.hcu.adaxplugin.config.RoomConfig;
import de.nonnull.hcu.adaxplugin.config.RoomId;
import lombok.NonNull;

public class DeviceService {
    private static final Logger LOGGER = LogManager.getLogger(DeviceService.class);

    private static final String SUFFIX_THERMOSTAT = "-T";
    private static final String SUFFIX_CLIMATE_SENSOR = "-C";

    private static final String FIRMWARE_VERSION = "1.0.0";

    public Stream<Device> createDevices(@NonNull Collection<RoomConfig> roomConfigs, @NonNull ContentResponse content) {
        final var idHomeMap = content.getHomes().stream().collect(Collectors.toMap(Home::getId, Function.identity()));
        final var idRoomMap = content.getRooms().stream().collect(Collectors.toMap(room -> {
            final var home = idHomeMap.getOrDefault(room.getHomeId(), Home.DEFAULT_HOME);
            return new RoomId(home.getId(), room.getId());
        }, r -> r));
        return roomConfigs.stream().map(cfg -> {
            final var devices = new ArrayList<Device>();
            final var room = idRoomMap.get(cfg.getId());
            if (!cfg.isExcludeThermostat()) {
                devices.add(createThermostat(cfg, room));
            }
            if (cfg.getActualTemperatureHandling() == ActualTemperatureHandling.EXTRA_DEVICE
                    && !cfg.isExcludeClimateSensor()) {
                devices.add(createClimateSensor(cfg, room));
            }
            return devices;
        }).flatMap(List::stream);
    }

    public Device createThermostat(@NonNull RoomConfig config, Room room) {
        final var optRoom = Optional.ofNullable(room);
        final var features = new HashSet<IFeature>();
        final var targetTemperature = optRoom.map(Room::getTargetTemperature).orElse(null);
        final var setPointTemperature = Optional.ofNullable(convertTemperature(targetTemperature))
                .map(t -> t - config.getSetPointTemperatureOffset()).orElse(null);
        features.add(SetPointTemperature.builder().setPointTemperature(setPointTemperature).build());
        if (config.getActualTemperatureHandling() == ActualTemperatureHandling.INCLUDE) {
            final var actualTemperature = ActualTemperature.builder()
                    .actualTemperature(convertTemperature(optRoom.map(Room::getTemperature).orElse(null))).build();
            features.add(actualTemperature);
        }
        final var maintenance = Maintenance.builder().unreach(optRoom.isEmpty()).build();
        features.add(maintenance);
        final var device = createDevice(config, DeviceType.THERMOSTAT, SUFFIX_THERMOSTAT, "Heater");
        device.setFeatures(features);
        return device;
    }

    public Device createClimateSensor(@NonNull RoomConfig config, Room room) {
        final var optRoom = Optional.ofNullable(room);
        final var actualTemperature = ActualTemperature.builder()
                .actualTemperature(convertTemperature(optRoom.map(Room::getTemperature).orElse(null)))
                .build();
        final var maintenance = Maintenance.builder().unreach(optRoom.isEmpty()).build();
        final var device = createDevice(config, DeviceType.CLIMATE_SENSOR, SUFFIX_CLIMATE_SENSOR, "Thermometer");
        device.setFeatures(Set.of(actualTemperature, maintenance));
        return device;
    }

    private Device createDevice(RoomConfig config, DeviceType type, String deviceIdSuffix, String displayName) {
        final var device = new Device();
        device.setDeviceId(createDeviceId(config.getId(), deviceIdSuffix));
        device.setDeviceType(type);
        device.setFriendlyName("ADAX %s %s".formatted(displayName, config.getDisplayName()));
        device.setFirmwareVersion(FIRMWARE_VERSION);
        device.setModelType(StringUtils.trimToEmpty(config.getModelType()));
        return device;
    }

    private Double convertTemperature(Integer adaxValue) {
        if (adaxValue == null) {
            return null;
        }
        final var hcuValue = adaxValue / 100d;
        return Math.clamp(hcuValue, -50d, 60d);
    }

    public Device copyOf(@NonNull Device other) {
        final var device = new Device();
        device.setDeviceId(other.getDeviceId());
        device.setDeviceType(other.getDeviceType());
        device.setFeatures(Set.copyOf(other.getFeatures()));
        device.setFirmwareVersion(other.getFirmwareVersion());
        device.setFriendlyName(other.getFriendlyName());
        device.setModelType(other.getModelType());
        return device;
    }

    private String createDeviceId(RoomId id, String suffix) {
        return id.toIdentifier() + suffix;
    }

    public Optional<DeviceType> parseDeviceType(@NonNull String deviceId) {
        if (deviceId.endsWith(SUFFIX_THERMOSTAT)) {
            return Optional.of(DeviceType.THERMOSTAT);
        }
        if (deviceId.endsWith(SUFFIX_CLIMATE_SENSOR)) {
            return Optional.of(DeviceType.CLIMATE_SENSOR);
        }
        LOGGER.error("Unsupported deviceId " + deviceId);
        return Optional.empty();
    }

    public Optional<RoomId> parseRoomId(@NonNull String deviceId) {
        try {
            final var id = StringUtils.substringBeforeLast(deviceId, "-");
            return Optional.of(RoomId.fromIdentifier(id));
        } catch (final IllegalArgumentException e) {
            LOGGER.error("Unsupported deviceId " + deviceId, e);
            return Optional.empty();
        }

    }
}
