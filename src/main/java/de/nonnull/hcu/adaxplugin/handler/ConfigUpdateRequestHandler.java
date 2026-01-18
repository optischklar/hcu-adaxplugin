package de.nonnull.hcu.adaxplugin.handler;

import static de.nonnull.hcu.adaxplugin.handler.ConfigTemplateRequestHandler.PROPERTY_ADAX_API_URL;
import static de.nonnull.hcu.adaxplugin.handler.ConfigTemplateRequestHandler.PROPERTY_ADAX_CLIENT_ID;
import static de.nonnull.hcu.adaxplugin.handler.ConfigTemplateRequestHandler.PROPERTY_ADAX_CLIENT_SECRET;
import static de.nonnull.hcu.adaxplugin.handler.ConfigTemplateRequestHandler.PROPERTY_ROOM_ACTUAL_TEMPERATURE_HANDLING;
import static de.nonnull.hcu.adaxplugin.handler.ConfigTemplateRequestHandler.PROPERTY_ROOM_DISPLAY_NAME;
import static de.nonnull.hcu.adaxplugin.handler.ConfigTemplateRequestHandler.PROPERTY_ROOM_EXCLUDE_CLIMATE_SENSOR;
import static de.nonnull.hcu.adaxplugin.handler.ConfigTemplateRequestHandler.PROPERTY_ROOM_EXCLUDE_THERMOSTAT;
import static de.nonnull.hcu.adaxplugin.handler.ConfigTemplateRequestHandler.PROPERTY_ROOM_MODEL_TYPE;
import static de.nonnull.hcu.adaxplugin.handler.ConfigTemplateRequestHandler.PROPERTY_ROOM_PREFIX;
import static de.nonnull.hcu.adaxplugin.handler.ConfigTemplateRequestHandler.PROPERTY_ROOM_SET_POINT_TEMPERATURE_OFFSET;
import static de.nonnull.hcu.adaxplugin.handler.ConfigTemplateRequestHandler.createRoomPropertyPrefix;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.eq3.plugin.domain.config.ConfigUpdateRequest;
import de.eq3.plugin.domain.config.ConfigUpdateResponse;
import de.eq3.plugin.domain.config.ConfigUpdateResponseStatus;
import de.eq3.plugin.serialization.PluginMessageType;
import de.nonnull.hcu.adaxplugin.PluginContext;
import de.nonnull.hcu.adaxplugin.config.ActualTemperatureHandling;
import de.nonnull.hcu.adaxplugin.config.Credentials;
import de.nonnull.hcu.adaxplugin.config.RoomConfig;
import de.nonnull.hcu.adaxplugin.config.RoomId;
import lombok.NonNull;

public class ConfigUpdateRequestHandler extends PluginMessageHandler<ConfigUpdateRequest> {

    private static final Logger LOGGER = LogManager.getLogger(ConfigUpdateRequestHandler.class);

    public ConfigUpdateRequestHandler(PluginContext aContext) {
        super(aContext, ConfigUpdateRequest.class);
    }

    @Override
    protected void handle(@NonNull String messageId, @NonNull ConfigUpdateRequest configUpdateRequest) {
        final var properties = configUpdateRequest.getProperties();

        if(properties == null) {
            sendResponse(messageId, ConfigUpdateResponseStatus.FAILED,
                    "Couldn't get properties from message");
            return;
        }

        LOGGER.info("Properties: {}", properties);

        final var apiUrl = (String) properties.get(PROPERTY_ADAX_API_URL);
        final var clientId = (String) properties.get(PROPERTY_ADAX_CLIENT_ID);
        final var clientSecret = (String) properties.get(PROPERTY_ADAX_CLIENT_SECRET);

        if (StringUtils.isBlank(apiUrl)) {
            sendResponse(messageId, ConfigUpdateResponseStatus.FAILED, "Adax API URL not set");
            return;
        }

        if (StringUtils.isBlank(clientId)) {
            sendResponse(messageId, ConfigUpdateResponseStatus.FAILED, "Adax client ID not set");
            return;
        }

        if (clientSecret == null) {
            sendResponse(messageId, ConfigUpdateResponseStatus.FAILED, "Adax client secret not set");
            return;
        }

        final var credentials = new Credentials(apiUrl, clientId, clientSecret);

        final var persistenceService = context.getPersistenceService();
        persistenceService.saveAdaxCredentials(credentials);

        final var roomIds = parseAvailableRooms(properties);
        if (roomIds.isEmpty()) {
            sendResponse(messageId, ConfigUpdateResponseStatus.APPLIED,
                    "ADAX credential configuration saved. You need to configure the rooms now.");
            return;
        }

        final var roomConfigs = new HashMap<RoomId, RoomConfig>();
        roomIds.forEach(roomId -> {
            final var config = parseRoomConfig(properties, roomId);
            roomConfigs.put(roomId, config);
        });

        persistenceService.saveRoomConfigurations(roomConfigs);
        sendResponse(messageId, ConfigUpdateResponseStatus.APPLIED, "ADAX plugin configuration saved.");

        sendMessage(context.getPluginStateService().createPluginStateResponseMessage());

        triggerDiscoverResponse();
    }

    private void sendResponse(String requestId, ConfigUpdateResponseStatus status,
            String message) {
        final var updateResponse = createMessage(requestId, PluginMessageType.CONFIG_UPDATE_RESPONSE,
                new ConfigUpdateResponse(status, message));
        sendMessage(updateResponse);
    }

    private List<RoomId> parseAvailableRooms(Map<String, Object> properties) {
        return properties.keySet().stream().map(key -> {
            if (key.startsWith(PROPERTY_ROOM_PREFIX)) {
                final var roomId = StringUtils.substringBetween(key, PROPERTY_ROOM_PREFIX, ".");
                return Optional.ofNullable(roomId).map(RoomId::fromIdentifier).orElse(null);
            }
            return null;
        }).filter(Objects::nonNull).toList();
    }

    private RoomConfig parseRoomConfig(Map<String, Object> properties, RoomId roomId) {
        final var config = new RoomConfig();
        config.setId(roomId);
        final var prefix = createRoomPropertyPrefix(roomId);
        config.setDisplayName((String) properties.get(prefix + PROPERTY_ROOM_DISPLAY_NAME));
        config.setModelType((String) properties.get(prefix + PROPERTY_ROOM_MODEL_TYPE));
        config.setActualTemperatureHandling(ActualTemperatureHandling
                .valueOf((String) properties.get(prefix + PROPERTY_ROOM_ACTUAL_TEMPERATURE_HANDLING)));
        config.setSetPointTemperatureOffset(
                ((Number) properties.get(prefix + PROPERTY_ROOM_SET_POINT_TEMPERATURE_OFFSET)).doubleValue());
        config.setExcludeThermostat(Boolean.TRUE.equals(properties.get(prefix + PROPERTY_ROOM_EXCLUDE_THERMOSTAT)));
        config.setExcludeClimateSensor(
                Boolean.TRUE.equals(properties.get(prefix + PROPERTY_ROOM_EXCLUDE_CLIMATE_SENSOR)));
        return config;
    }
}
