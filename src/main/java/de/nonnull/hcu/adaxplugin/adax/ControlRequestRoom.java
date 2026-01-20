package de.nonnull.hcu.adaxplugin.adax;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class ControlRequestRoom {
    private final int id;
    private final boolean heatingEnabled;
    private final int targetTemperature;
}
