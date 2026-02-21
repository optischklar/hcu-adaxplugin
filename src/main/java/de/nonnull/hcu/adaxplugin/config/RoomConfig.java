package de.nonnull.hcu.adaxplugin.config;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import de.eq3.plugin.serialization.DeviceType;
import de.nonnull.hcu.adaxplugin.adax.model.Home;
import de.nonnull.hcu.adaxplugin.adax.model.Room;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RoomConfig {
    @NonNull
    private RoomId id;

    @NonNull
    private String displayName;

    private String modelType;

    @NonNull
    private ActualTemperatureHandling actualTemperatureHandling = ActualTemperatureHandling.NONE;

    private double setPointTemperatureOffset = 0d;

    private boolean excludeThermostat = false;
    private boolean excludeClimateSensor = false;

    private Integer windowClosedHeatingDelayMinutes;

    public static Stream<RoomConfig> createRoomConfigs(@NonNull List<Home> homes, @NonNull List<Room> rooms) {
        final var idHomeMap = homes.stream().collect(Collectors.toMap(Home::getId, Function.identity()));
        return rooms.stream().map(room -> {
            final var home = idHomeMap.getOrDefault(room.getHomeId(), Home.DEFAULT_HOME);
            final var config = new RoomConfig();
            config.setDisplayName(String.format("%s / %s", home.getName(), room.getName()));
            config.setId(new RoomId(home.getId(), room.getId()));
            return config;
        });
    }

    public void setExcludeDevice(DeviceType type, boolean exclude) {
        switch (type) {
            case CLIMATE_SENSOR:
                setExcludeClimateSensor(exclude);
                break;
            case THERMOSTAT:
                setExcludeThermostat(exclude);
                break;
            default:
                break;
        }
    }

}
