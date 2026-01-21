package de.nonnull.hcu.adaxplugin.adax.model;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class ControlRequestRoom {
    private final long id;
    private final boolean heatingEnabled;
    private final int targetTemperature;
}
