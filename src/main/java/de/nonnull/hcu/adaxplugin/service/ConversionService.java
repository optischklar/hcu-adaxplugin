package de.nonnull.hcu.adaxplugin.service;

import de.nonnull.hcu.adaxplugin.adax.AdaxRemoteClient;
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
        final var targetTemperature = (int) ((setPointTemperature + roomConfig.getSetPointTemperatureOffset()) * 100);
        return clamp(targetTemperature, AdaxRemoteClient.MIN_TARGET_TEMPERATURE,
                AdaxRemoteClient.MAX_TARGET_TEMPERATURE);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
