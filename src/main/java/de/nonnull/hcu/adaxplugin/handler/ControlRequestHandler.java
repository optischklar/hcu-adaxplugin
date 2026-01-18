package de.nonnull.hcu.adaxplugin.handler;

import java.util.Set;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.eq3.plugin.domain.control.ControlRequest;
import de.eq3.plugin.domain.control.ControlResponse;
import de.eq3.plugin.domain.error.Error;
import de.eq3.plugin.domain.features.IFeature;
import de.eq3.plugin.serialization.PluginMessageType;
import de.nonnull.hcu.adaxplugin.PluginContext;
import lombok.NonNull;

public class ControlRequestHandler extends PluginMessageHandler<ControlRequest> {

    private static final String CONTROL_REQUEST_FAILED = "CONTROL_REQUEST_FAILED";

    private static final Logger LOGGER = LogManager.getLogger(ControlRequestHandler.class);

    public ControlRequestHandler(PluginContext aContext) {
        super(aContext, ControlRequest.class);
    }

    @Override
    protected void handle(@NonNull String messageId, @NonNull ControlRequest request) {
        final var deviceId = request.getDeviceId();

        context.getPersistenceService().getConfiguration().ifPresentOrElse(config -> {
            LOGGER.info("Incoming control request data: {}", request);

            if (!config.isAdaxCredentialsComplete() || !config.isRoomConfigurationInitialized()) {
                LOGGER.error("Configuration incomplete");
                sendControlRequestResponse(messageId, deviceId, false,
                        new Error(CONTROL_REQUEST_FAILED, "Configuration incomplete"));
                return;
            }

            final var roomId = context.getDeviceService().parseRoomId(deviceId).orElse(null);
            if (roomId == null) {
                sendControlRequestResponse(messageId, deviceId, false,
                        new Error(CONTROL_REQUEST_FAILED, "Device not found"));
                return;
            }

            final var roomConfig = config.getRoomConfigurations().get(roomId);
            final Set<IFeature> features = request.getFeatures();
            LOGGER.info("Features to set: {}", features);

            if (roomConfig == null) {
                sendControlRequestResponse(messageId, deviceId, false,
                        new Error(CONTROL_REQUEST_FAILED, "Device not found"));
                return;
            }

            final var succeeded = true;

            if (succeeded) {
                LOGGER.info("ADAX API - Successfully called api request");

                sendControlRequestResponse(messageId, deviceId, true, null);
            } else {
                final var cause = new NotImplementedException();
                final Error error = new Error(CONTROL_REQUEST_FAILED, "Not yet implemented");
                LOGGER.error("ADAX API - Error calling api request, cause {}", cause.getMessage());

                sendControlRequestResponse(messageId, deviceId, false, error);
            }
        }, () -> {
            sendControlRequestResponse(messageId, deviceId, false,
                    new Error(CONTROL_REQUEST_FAILED, "No configuration present"));
        });
    }

    private void sendControlRequestResponse(String requestId, String deviceId, boolean success, Error error) {
        final ControlResponse controlResponse = new ControlResponse(deviceId, success, error);
        final var message = createMessage(requestId, PluginMessageType.CONTROL_RESPONSE, controlResponse);
        sendMessage(message);
    }
}
