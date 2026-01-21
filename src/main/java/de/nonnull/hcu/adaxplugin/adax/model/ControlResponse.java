package de.nonnull.hcu.adaxplugin.adax.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Value;

@Value
public class ControlResponse {
    private final List<ControlResponseRoom> rooms;

    @JsonCreator
    public ControlResponse(@JsonProperty("rooms") List<ControlResponseRoom> aRooms) {
        rooms = aRooms;
    }
}
