package de.nonnull.hcu.adaxplugin.adax.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Value;

@Value
public class ControlResponseRoom {
    private final long id;
    private final ControlStatus status;

    @JsonCreator
    public ControlResponseRoom(@JsonProperty("id") long aId, @JsonProperty("status") ControlStatus aStatus) {
        id = aId;
        status = aStatus;
    }
}
