package de.nonnull.hcu.adaxplugin.handler;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.eq3.plugin.domain.error.Error;
import de.eq3.plugin.domain.status.StatusRequest;
import de.eq3.plugin.domain.status.StatusResponse;
import de.eq3.plugin.serialization.PluginMessageType;
import de.nonnull.hcu.adaxplugin.PluginContext;
import lombok.NonNull;

public class StatusRequestHandler extends PluginMessageHandler<StatusRequest> {
    private static final Logger LOGGER = LogManager.getLogger(StatusRequestHandler.class);

    public StatusRequestHandler(PluginContext aContext) {
        super(aContext, StatusRequest.class);
    }

    @Override
    protected void handle(@NonNull String messageId, @NonNull StatusRequest request) {
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
                final var roomConfigs = config.getRoomConfigurations();
                final var requestedDeviceIds = Optional.ofNullable(request.getDeviceIds()).orElse(Set.of());
                final var devices = context.getDeviceService()
                        .createDevices(roomConfigs.values(), content)
                        .filter(device -> requestedDeviceIds.isEmpty()
                                || requestedDeviceIds.contains(device.getDeviceId()))
                        .collect(Collectors.toSet());

                LOGGER.info("Sending status response for {} device(s): {}", devices.size(), devices);
                final var response = new StatusResponse(true, devices, null);
                final var message = createMessage(messageId, PluginMessageType.STATUS_RESPONSE, response);
                sendMessage(message);
            });
        }, () -> {
            LOGGER.error("No configuration present");
            sendErrorResponse(messageId, "No configuration present.");
        });
    }

    private void sendErrorResponse(String requestId, String errorMessage) {
        final Error error = new Error("STATUS_REQUEST_FAILED", errorMessage);

        final var message = createMessage(requestId, PluginMessageType.STATUS_RESPONSE,
                new StatusResponse(false, null, error));
        sendMessage(message);
    }
}
