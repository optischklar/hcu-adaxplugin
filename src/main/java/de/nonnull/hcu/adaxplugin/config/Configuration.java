package de.nonnull.hcu.adaxplugin.config;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Configuration {
    private Credentials adaxCredentials = null;
    private Map<RoomId, RoomConfig> roomConfigurations = null;

    @JsonIgnore
    public boolean isRoomConfigurationInitialized() {
        return roomConfigurations != null;
    }

    @JsonIgnore
    public boolean isAdaxCredentialsComplete() {
        return adaxCredentials != null && adaxCredentials.isComplete();
    }

}
