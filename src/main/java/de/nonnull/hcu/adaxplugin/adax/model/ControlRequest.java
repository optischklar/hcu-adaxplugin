package de.nonnull.hcu.adaxplugin.adax.model;

import java.util.List;

import lombok.Value;

@Value
public class ControlRequest {
    private final List<ControlRequestRoom> rooms;
}
