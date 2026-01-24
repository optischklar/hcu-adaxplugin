package de.nonnull.hcu.adaxplugin.handler;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.stream.Stream;

import de.eq3.plugin.domain.config.ConfigTemplateRequest;
import de.eq3.plugin.domain.config.ConfigTemplateResponse;
import de.eq3.plugin.domain.config.GroupTemplate;
import de.eq3.plugin.domain.config.PropertyTemplate;
import de.eq3.plugin.domain.config.PropertyType;
import de.eq3.plugin.serialization.PluginMessageType;
import de.nonnull.hcu.adaxplugin.PluginContext;
import de.nonnull.hcu.adaxplugin.config.ActualTemperatureHandling;
import de.nonnull.hcu.adaxplugin.config.Configuration;
import de.nonnull.hcu.adaxplugin.config.RoomConfig;
import de.nonnull.hcu.adaxplugin.config.RoomId;
import lombok.NonNull;

public class ConfigTemplateRequestHandler extends PluginMessageHandler<ConfigTemplateRequest> {

    static final String PROPERTY_ADAX_API_URL = "adaxApiUrl";
    static final String PROPERTY_ADAX_CLIENT_ID = "adaxClientId";
    static final String PROPERTY_ADAX_CLIENT_SECRET = "adaxClientSecret";

    static final String PROPERTY_ROOM_PREFIX = "room-";
    static final String PROPERTY_ROOM_DISPLAY_NAME = ".displayName";
    static final String PROPERTY_ROOM_MODEL_TYPE = ".modelType";
    static final String PROPERTY_ROOM_ACTUAL_TEMPERATURE_HANDLING = ".actTempHandling";
    static final String PROPERTY_ROOM_SET_POINT_TEMPERATURE_OFFSET = ".setPointTempOfs";
    static final String PROPERTY_ROOM_EXCLUDE_THERMOSTAT = ".exclThermostat";
    static final String PROPERTY_ROOM_EXCLUDE_CLIMATE_SENSOR = ".exclClimateSensor";

    private static final String GROUP_ID_CREDENTIALS = "credentials";

    public ConfigTemplateRequestHandler(PluginContext aContext) {
        super(aContext, ConfigTemplateRequest.class);
    }

    @Override
    protected void handle(@NonNull String messageId, @NonNull ConfigTemplateRequest configTemplateRequest) {
        final var properties = new HashMap<String, PropertyTemplate>();
        final var apiUrl = new PropertyTemplate("API URL", "The URL of the ADAX Remote Client API.", true,
                PropertyType.STRING);
        apiUrl.setGroupId(GROUP_ID_CREDENTIALS);
        apiUrl.setOrder(0);
        apiUrl.setDefaultValue("https://api-1.adax.no/client-api");
        final var clientId = new PropertyTemplate("Client-ID  (Account-ID)",
                "The client-ID to use with the ADAX Remote Client API.", true, PropertyType.STRING);
        clientId.setGroupId(GROUP_ID_CREDENTIALS);
        clientId.setOrder(1);
        final var clientSecret = new PropertyTemplate("Client secret (Password)",
                "The secret to use with the ADAX Remote Client API.", true, PropertyType.PASSWORD);
        clientSecret.setOrder(2);
        clientSecret.setGroupId(GROUP_ID_CREDENTIALS);

        final var configuration = context.getPersistenceService().getConfiguration();

        configuration.map(Configuration::getAdaxCredentials)
        .ifPresent(credentials -> {
            apiUrl.setCurrentValue(credentials.getApiUrl());
            clientId.setCurrentValue(credentials.getClientId());
            clientSecret.setCurrentValue(credentials.getClientSecret());
        });

        properties.put(PROPERTY_ADAX_API_URL, apiUrl);
        properties.put(PROPERTY_ADAX_CLIENT_ID, clientId);
        properties.put(PROPERTY_ADAX_CLIENT_SECRET, clientSecret);

        final var credentialsGroup = new GroupTemplate("ADAX Remote Client API Credentials");
        credentialsGroup.setOrder(0);

        final var groups = new HashMap<String, GroupTemplate>();
        groups.put(GROUP_ID_CREDENTIALS, credentialsGroup);

        if (configuration.isPresent()) {
            final var config = configuration.get();
            if (config.isRoomConfigurationInitialized()) {
                addRoomTemplates(properties, groups, config.getRoomConfigurations().values().stream());
                sendResponse(messageId, properties, groups);
                return;
            }
            if (config.isAdaxCredentialsComplete()) {
                context.getAdaxClient().getContent(ar -> {
                    if (ar.succeeded()) {
                        final var content = ar.result();
                        context.getRoomMeasuringValuesCache().putAdaxValuesFromContent(content);
                        final var roomConfigs = RoomConfig.createRoomConfigs(content.getHomes(), content.getRooms());
                        addRoomTemplates(properties, groups, roomConfigs);
                    }
                    sendResponse(messageId, properties, groups);
                });
                return;
            }
        }

        credentialsGroup
        .setDescription("Once the credentials have been entered and saved, the rooms can be configured.");

        sendResponse(messageId, properties, groups);
    }

    private void addRoomTemplates(Map<String, PropertyTemplate> properties, Map<String, GroupTemplate> groups,
            Stream<RoomConfig> roomConfigs) {
        final var roomIds = new HashSet<String>();
        roomConfigs.forEach(roomConfig -> {
            final var roomId = roomConfig.getId();
            final var groupId = "room-" + roomId.toIdentifier();
            createRoomPropertyTemplates(roomConfig, groupId).forEach(properties::put);
            final var group = new GroupTemplate(
                    "Room %s, %s".formatted(roomId.toIdentifier(), roomConfig.getDisplayName()));
            group.setOrder((int) roomId.getRoomId());
            groups.put(groupId, group);
            roomIds.add(roomId.toIdentifier());
        });
    }

    private void sendResponse(String requestId, Map<String, PropertyTemplate> properties,
            Map<String, GroupTemplate> groups) {
        final var response = createMessage(requestId, PluginMessageType.CONFIG_TEMPLATE_RESPONSE,
                new ConfigTemplateResponse(properties, groups));
        sendMessage(response);
    }

    private Map<String, PropertyTemplate> createRoomPropertyTemplates(RoomConfig config, String groupId) {
        final var displayName = new PropertyTemplate("Display name", "Specifies the display name of this room.", true,
                PropertyType.STRING);
        displayName.setDefaultValue(config.getDisplayName());
        displayName.setCurrentValue(config.getDisplayName());
        displayName.setGroupId(groupId);
        displayName.setOrder(0);

        final var modelType = new PropertyTemplate("Model type", "An optional vendor specific model type.", false,
                PropertyType.STRING);
        modelType.setDefaultValue("");
        modelType.setCurrentValue(config.getModelType());
        modelType.setGroupId(groupId);
        modelType.setOrder(1);

        final var actualTemperatureHandling = new PropertyTemplate("Actual temperature handling",
                "The handling of the actual temperature value of a room.", true, PropertyType.ENUM);
        actualTemperatureHandling.setValues(Arrays.stream(ActualTemperatureHandling.values()).map(Enum::name).toList());
        actualTemperatureHandling.setDefaultValue(ActualTemperatureHandling.NONE.name());
        actualTemperatureHandling.setCurrentValue(config.getActualTemperatureHandling().name());
        actualTemperatureHandling.setGroupId(groupId);
        actualTemperatureHandling.setOrder(2);

        final var setPointTemperatureOffset = new PropertyTemplate("Target temperature offset",
                "The target temperature offset in °C that is added to the rooms target temperature.", true,
                PropertyType.NUMBER);
        setPointTemperatureOffset.setDefaultValue("0.0");
        setPointTemperatureOffset.setCurrentValue(Double.toString(config.getSetPointTemperatureOffset()));
        setPointTemperatureOffset.setGroupId(groupId);
        setPointTemperatureOffset.setOrder(3);

        final var excludeThermostat = new PropertyTemplate("Exclude thermostat",
                "Specifies if the thermostat device is excluded.", false, PropertyType.BOOLEAN);
        excludeThermostat.setDefaultValue(Boolean.FALSE.toString());
        excludeThermostat.setCurrentValue(Boolean.toString(config.isExcludeThermostat()));
        excludeThermostat.setGroupId(groupId);
        excludeThermostat.setOrder(4);

        final var excludeClimateSensor = new PropertyTemplate("Exclude climate sensor",
                "Specifies if the climate sensor device is excluded. The device is only availabe if the actual temperature handling is set to "
                        + ActualTemperatureHandling.EXTRA_DEVICE + ".",
                        false, PropertyType.BOOLEAN);
        excludeClimateSensor.setDefaultValue(Boolean.FALSE.toString());
        excludeClimateSensor.setCurrentValue(Boolean.toString(config.isExcludeThermostat()));
        excludeClimateSensor.setGroupId(groupId);
        excludeClimateSensor.setOrder(5);

        final var propertyPrefix = createRoomPropertyPrefix(config.getId());

        return Map.of(propertyPrefix + PROPERTY_ROOM_DISPLAY_NAME, displayName,
                propertyPrefix + PROPERTY_ROOM_MODEL_TYPE, modelType,
                propertyPrefix + PROPERTY_ROOM_ACTUAL_TEMPERATURE_HANDLING, actualTemperatureHandling,
                propertyPrefix + PROPERTY_ROOM_SET_POINT_TEMPERATURE_OFFSET, setPointTemperatureOffset,
                propertyPrefix + PROPERTY_ROOM_EXCLUDE_THERMOSTAT, excludeThermostat,
                propertyPrefix + PROPERTY_ROOM_EXCLUDE_CLIMATE_SENSOR, excludeClimateSensor);
    }

    static String createRoomPropertyPrefix(RoomId roomId) {
        return PROPERTY_ROOM_PREFIX + roomId.toIdentifier();
    }
}
