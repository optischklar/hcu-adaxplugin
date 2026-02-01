package de.nonnull.hcu.adaxplugin.service;

import de.nonnull.hcu.adaxplugin.config.RoomConfig;
import lombok.NonNull;

public class ConversionService {
    public Double convertAdaxToHcuTemperature(Integer adaxValue) {
        if (adaxValue == null) {
            return null;
        }
        final var hcuValue = adaxValue / 100d;
        return clamp(hcuValue, -50d, 60d);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public Integer convertHcuSetPointTemperatureToAdaxTargetTemperature(@NonNull RoomConfig roomConfig,
            Double setPointTemperature) {
        if (setPointTemperature == null) {
            return null;
        }
        return (int) ((setPointTemperature + roomConfig.getSetPointTemperatureOffset()) * 100);
    }
}
