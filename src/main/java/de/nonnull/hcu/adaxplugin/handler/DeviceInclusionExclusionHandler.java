package de.nonnull.hcu.adaxplugin.handler;

import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.eq3.plugin.domain.Body;
import de.eq3.plugin.domain.inclusion.ExclusionEvent;
import de.eq3.plugin.domain.inclusion.InclusionEvent;
import de.nonnull.hcu.adaxplugin.PluginContext;
import de.nonnull.hcu.adaxplugin.config.Configuration;
import lombok.NonNull;

public class DeviceInclusionExclusionHandler<T extends Body> extends PluginMessageHandler<T> {
    private static final Logger LOGGER = LogManager.getLogger(DeviceInclusionExclusionHandler.class);

    public DeviceInclusionExclusionHandler(PluginContext aContext, Class<T> eventType) {
        super(aContext, eventType);
        if (!ExclusionEvent.class.equals(eventType) && !InclusionEvent.class.equals(eventType)) {
            throw new IllegalArgumentException("Argument eventType must be " + ExclusionEvent.class + " or "
                    + InclusionEvent.class + " but was " + eventType);
        }
    }

    @Override
    protected void handle(@NonNull String messageId, @NonNull Body body) {
        if (body instanceof ExclusionEvent) {
            final var deviceIds = ((ExclusionEvent) body).getDeviceIds();
            setExcludeDevices(deviceIds, true);
        } else if (body instanceof InclusionEvent) {
            final var deviceIds = ((InclusionEvent) body).getDeviceIds();
            setExcludeDevices(deviceIds, false);
            triggerStatusResponse();
        }
    }

    private void setExcludeDevices(Set<String> deviceIds, boolean exclude) {
        final var persistenceService = context.getPersistenceService();
        persistenceService.getConfiguration().map(Configuration::getRoomConfigurations).ifPresentOrElse(roomConfigs -> {
            final var deviceService = context.getDeviceService();
            deviceIds.forEach(deviceId -> {
                deviceService.parseRoomId(deviceId).map(roomConfigs::get).ifPresent(roomConfig -> {
                    deviceService.parseDeviceType(deviceId).ifPresent(type -> {
                        roomConfig.setExcludeDevice(type, exclude);
                    });
                });
            });
            persistenceService.saveRoomConfigurations(roomConfigs);

            LOGGER.info("{} devices {}", (exclude ? "Excluded" : "Included"), deviceIds);
        }, () -> LOGGER.error("No room configuration available"));
    }
}
