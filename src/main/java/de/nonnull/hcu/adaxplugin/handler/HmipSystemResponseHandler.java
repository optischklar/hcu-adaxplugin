package de.nonnull.hcu.adaxplugin.handler;

import java.util.ArrayList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.eq3.plugin.domain.control.HmipSystemResponse;
import de.eq3.plugin.serialization.DeviceType;
import de.nonnull.hcu.adaxplugin.PluginContext;
import de.nonnull.hcu.adaxplugin.service.HcuRoomMeasuringValues;
import io.vertx.core.json.JsonObject;
import lombok.NonNull;

public class HmipSystemResponseHandler extends PluginMessageHandler<HmipSystemResponse> {

    private static final Logger LOGGER = LogManager.getLogger(HmipSystemResponseHandler.class);

    private static final String DEVICE_TYPE_PLUGIN = "PLUGIN_EXTERNAL";
    private static final String GROUP_TYPE_HEATING = "HEATING";

    public HmipSystemResponseHandler(PluginContext aContext) {
        super(aContext, HmipSystemResponse.class);
    }

    @Override
    protected void handle(@NonNull String messageId, @NonNull HmipSystemResponse response) {
        LOGGER.trace("Incoming Hmip system response: {}", response);

        final var deviceService = context.getDeviceService();
        final var valuesCache = context.getRoomMeasuringValuesCache();

        final var body = JsonObject.mapFrom(response.getBody());
        final var groups = body.getJsonObject("groups");
        final var devices = body.getJsonObject("devices");

        var handledRooms = 0;

        for (final var key : devices.fieldNames()) {
            final var device = devices.getJsonObject(key);
            final var type = device.getString("type");
            final var pluginId = device.getString("pluginId");

            if (!DEVICE_TYPE_PLUGIN.equals(type) || !PluginContext.PLUGIN_ID.equals(pluginId)) {
                continue;
            }

            final var pluginDeviceId = device.getString("pluginDeviceId");
            final var deviceType = deviceService.parseDeviceType(pluginDeviceId).orElse(null);
            final var roomId = deviceService.parseRoomId(pluginDeviceId).orElse(null);

            if (roomId == null || deviceType != DeviceType.THERMOSTAT) {
                continue;
            }

            final var functionalChannels = device.getJsonObject("functionalChannels");
            final var groupsOfDevice = new ArrayList<String>();
            for (final var channelKey : functionalChannels.fieldNames()) {
                final var channel = functionalChannels.getJsonObject(channelKey);
                final var channelGroups = channel.getJsonArray("groups");
                channelGroups.stream().map(String.class::cast).forEach(groupsOfDevice::add);
            }

            for (final var groupId : groupsOfDevice) {
                final var group = groups.getJsonObject(groupId);
                final var groupType = group.getString("type");

                if (!GROUP_TYPE_HEATING.equals(groupType)) {
                    continue;
                }

                final var values = HcuRoomMeasuringValues.fromGroupJsonObject(group);
                valuesCache.putHcuValues(roomId, values);

                final var adaxEvent = ControlAdaxEvent.builder()
                        .roomId(roomId)
                        .heatingEnabled(!HcuRoomMeasuringValues.WINDOW_STATE_OPEN.equals(values.getWindowState()))
                        .setPointTemperature(values.getSetPointTemperature())
                        .build();
                publish(adaxEvent);

                handledRooms++;
            }
        }
        LOGGER.info("Got values from {} rooms", handledRooms);
    }
}
