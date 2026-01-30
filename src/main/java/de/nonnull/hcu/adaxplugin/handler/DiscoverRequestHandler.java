package de.nonnull.hcu.adaxplugin.handler;

import static de.nonnull.hcu.adaxplugin.PluginContext.LANG_EN;
import static de.nonnull.hcu.adaxplugin.PluginContext.PLUGIN_ID;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import de.eq3.plugin.domain.control.HmipSystemRequest;
import de.eq3.plugin.domain.device.Device;
import de.eq3.plugin.domain.discover.DiscoverRequest;
import de.eq3.plugin.domain.discover.DiscoverResponse;
import de.eq3.plugin.domain.error.Error;
import de.eq3.plugin.domain.user.message.BehaviorType;
import de.eq3.plugin.domain.user.message.CreateUserMessageRequest;
import de.eq3.plugin.domain.user.message.DeleteUserMessageRequest;
import de.eq3.plugin.domain.user.message.MessageCategory;
import de.eq3.plugin.serialization.PluginMessageType;
import de.nonnull.hcu.adaxplugin.PluginContext;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DiscoverRequestHandler extends PluginMessageHandler<DiscoverRequest> {

    public DiscoverRequestHandler(PluginContext aContext) {
        super(aContext, DiscoverRequest.class);
    }

    @Override
    protected void handle(@NonNull String messageId, @NonNull DiscoverRequest body) {
        final var persistenceService = context.getPersistenceService();
        final var configuration = persistenceService.getConfiguration();

        configuration.ifPresentOrElse(config -> {

            if (!config.isAdaxCredentialsComplete() || !config.isRoomConfigurationInitialized()) {
                LOGGER.error("Configuration incomplete");
                sendErrorResponse(messageId, "Configuration incomplete");
                return;
            }

            context.getAdaxClient().getContent(ar -> {
                if (ar.failed()) {
                    final var cause = ar.cause();
                    LOGGER.error("Couldn't query ADAX API: {}", cause.getMessage(), cause);
                    sendErrorResponse(messageId, "Couldn't query ADAX API: " + cause.getMessage());
                    return;
                }

                final var content = ar.result();
                context.getRoomMeasuringValuesCache().putAdaxValuesFromContent(content);
                final var devices = context.getDeviceService()
                        .createDevices(config.getRoomConfigurations().values(), content)
                        .collect(Collectors.toSet());

                LOGGER.info("Devices: {}", devices);

                if (devices.isEmpty()) {
                    sendInfoMessage("Plugin Info", "No devices found!");
                } else {
                    deleteInfoMessage();
                }
                sendSuccessResponse(messageId, devices);
                sendHmipSystemRequest();
            });
        }, () -> {
            LOGGER.error("No configuration present");
            sendErrorResponse(messageId, "No configuration present.");
        });
    }

    private void sendInfoMessage(String title, String message) {
        final CreateUserMessageRequest request = new CreateUserMessageRequest();
        request.setBehaviorType(BehaviorType.DISMISSIBLE);
        request.setTimestamp(System.currentTimeMillis());
        request.setUserMessageId(PLUGIN_ID + "_no_devices");
        request.setMessageCategory(MessageCategory.INFO);
        request.setMessage(Map.of(LANG_EN, message));
        request.setTitle(Map.of(LANG_EN, title));
        final var pluginMessage = createMessage(UUID.randomUUID().toString(), PluginMessageType.CREATE_USER_MESSAGE_REQUEST,
                request);
        sendMessage(pluginMessage);
    }

    private void deleteInfoMessage() {
        final DeleteUserMessageRequest request = new DeleteUserMessageRequest(PLUGIN_ID + "_no_devices");
        final var message = createMessage(UUID.randomUUID().toString(), PluginMessageType.DELETE_USER_MESSAGE_REQUEST,
                request);
        sendMessage(message);
    }

    private void sendSuccessResponse(String requestId, Set<Device> devices) {
        LOGGER.info("ADAX: Successfully discovered and converted {} device(s)", devices.size());
        final var message = createMessage(requestId, PluginMessageType.DISCOVER_RESPONSE,
                new DiscoverResponse(true, devices, null));
        sendMessage(message);
    }

    private void sendErrorResponse(String requestId, String errorMessage) {
        final Error error = new Error("DISCOVER_REQUEST_FAILED", errorMessage);
        final var message = createMessage(requestId, PluginMessageType.DISCOVER_RESPONSE,
                new DiscoverResponse(false, null, error));
        sendMessage(message);
    }

    private void sendHmipSystemRequest() {
        final var request = new HmipSystemRequest();
        request.setPath("/hmip/home/getSystemState");
        request.setBody(Map.of());
        final var message = createMessage(UUID.randomUUID().toString(), PluginMessageType.HMIP_SYSTEM_REQUEST, request);
        sendMessage(message);
    }
}
