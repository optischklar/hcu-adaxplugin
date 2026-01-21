package de.nonnull.hcu.adaxplugin.adax.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class Room {
    private final long id;
    private final String name;
    private final long homeId;
    private final Boolean heatingEnabled;
    private final Integer targetTemperature;
    private final Integer temperature;

    @JsonCreator
    public Room(@JsonProperty("id") long aId, @JsonProperty("name") String aName, @JsonProperty("homeId") long aHomeId,
            @JsonProperty("heatingEnabled") Boolean aHeatingEnabled,
            @JsonProperty("targetTemperature") Integer aTargetTemperature,
            @JsonProperty("temperature") Integer aTemperature) {
        id = aId;
        name = aName;
        homeId = aHomeId;
        heatingEnabled = aHeatingEnabled;
        targetTemperature = aTargetTemperature;
        temperature = aTemperature;
    }

}
