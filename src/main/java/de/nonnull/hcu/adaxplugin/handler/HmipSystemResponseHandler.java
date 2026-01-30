package de.nonnull.hcu.adaxplugin.handler;

import java.util.ArrayList;

import de.eq3.plugin.domain.control.HmipSystemResponse;
import de.eq3.plugin.serialization.DeviceType;
import de.nonnull.hcu.adaxplugin.PluginContext;
import io.vertx.core.json.JsonObject;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HmipSystemResponseHandler extends PluginMessageHandler<HmipSystemResponse> {

    private static final String DEVICE_TYPE_PLUGIN = "PLUGIN_EXTERNAL";
    private static final String GROUP_TYPE_HEATING = "HEATING";

    public HmipSystemResponseHandler(PluginContext aContext) {
        super(aContext, HmipSystemResponse.class);
    }

    @Override
    protected void handle(@NonNull String messageId, @NonNull HmipSystemResponse response) {
        LOGGER.trace("Incoming Hmip system response: {}", response);

        final var deviceService = context.getDeviceService();

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

                final var adaxEvent = new SyncAdaxHeatingEvent(roomId, group);
                publish(adaxEvent);

                handledRooms++;
            }
        }
        LOGGER.info("Got values from {} rooms", handledRooms);
    }
}
