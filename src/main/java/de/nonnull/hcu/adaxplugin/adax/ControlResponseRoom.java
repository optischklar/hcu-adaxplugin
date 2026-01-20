package de.nonnull.hcu.adaxplugin.adax;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Value;

@Value
public class ControlResponseRoom {
    private final int id;
    private final ControlStatus status;

    @JsonCreator
    public ControlResponseRoom(@JsonProperty("id") int aId, @JsonProperty("status") ControlStatus aStatus) {
        id = aId;
        status = aStatus;
    }
}
