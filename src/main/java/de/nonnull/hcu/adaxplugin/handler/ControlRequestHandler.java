package de.nonnull.hcu.adaxplugin.handler;

import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.eq3.plugin.domain.control.ControlRequest;
import de.eq3.plugin.domain.control.ControlResponse;
import de.eq3.plugin.domain.error.Error;
import de.eq3.plugin.domain.features.IFeature;
import de.eq3.plugin.domain.features.SetPointTemperature;
import de.eq3.plugin.serialization.DeviceType;
import de.eq3.plugin.serialization.Feature;
import de.eq3.plugin.serialization.PluginMessageType;
import de.nonnull.hcu.adaxplugin.PluginContext;
import de.nonnull.hcu.adaxplugin.adax.model.ControlResponseRoom;
import de.nonnull.hcu.adaxplugin.adax.model.ControlStatus;
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

            final var deviceType = context.getDeviceService().parseDeviceType(deviceId).orElse(null);
            if (deviceType != DeviceType.THERMOSTAT) {
                sendControlRequestResponse(messageId, deviceId, false,
                        new Error(CONTROL_REQUEST_FAILED, "Device of " + deviceType + " not supported"));
                return;
            }

            final var roomConfig = config.getRoomConfigurations().get(roomId);
            if (roomConfig == null) {
                sendControlRequestResponse(messageId, deviceId, false,
                        new Error(CONTROL_REQUEST_FAILED, "Device not found"));
                return;
            }

            final Set<IFeature> features = request.getFeatures();
            LOGGER.info("Features to set: {}", features);

            final var setPointTempFeature = features.stream().filter(f -> f.getType() == Feature.SET_POINT_TEMPERATURE)
                    .map(SetPointTemperature.class::cast).findAny();

            if (setPointTempFeature.isEmpty()) {
                sendControlRequestResponse(messageId, deviceId, false,
                        new Error(CONTROL_REQUEST_FAILED, "Unknown features"));
                return;
            }

            final var targetTemperature = (int) ((setPointTempFeature.map(SetPointTemperature::getSetPointTemperature)
                    .orElseThrow() + roomConfig.getSetPointTemperatureOffset()) * 100);

            LOGGER.info("Setting target temperature of room {} to {}", roomId.toIdentifier(), targetTemperature);

            context.getAdaxClient().controlRoom(roomId, true, targetTemperature, ar -> {
                if (ar.succeeded()) {
                    final var response = ar.result();
                    LOGGER.info("ADAX API - Successfully called api request. Response: {}", response);
                    final var status = response.getRooms().stream().filter(r -> r.getId() == roomId.getRoomId())
                            .map(ControlResponseRoom::getStatus).findAny();
                    if (status.filter(s -> s == ControlStatus.OK).isPresent()) {
                        sendControlRequestResponse(messageId, deviceId, true, null);
                    } else {
                        final Error error = new Error(CONTROL_REQUEST_FAILED,
                                status.map(ControlStatus::name).orElse("not found"));
                        sendControlRequestResponse(messageId, deviceId, false, error);
                    }
                } else {
                    final var cause = ar.cause();
                    LOGGER.error("ADAX API - Error calling api request, cause {}", cause.getMessage(), cause);
                    final Error error = new Error(CONTROL_REQUEST_FAILED, cause.getMessage());
                    sendControlRequestResponse(messageId, deviceId, false, error);
                }
            });

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
