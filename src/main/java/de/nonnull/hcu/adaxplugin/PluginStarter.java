package de.nonnull.hcu.adaxplugin;

import static de.nonnull.hcu.adaxplugin.PluginContext.PLUGIN_NAME;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.eq3.plugin.domain.inclusion.ExclusionEvent;
import de.eq3.plugin.domain.inclusion.InclusionEvent;
import de.eq3.plugin.domain.plugin.PluginReadinessStatus;
import de.eq3.plugin.serialization.PluginMessage;
import de.nonnull.hcu.adaxplugin.adax.AdaxRemoteClient;
import de.nonnull.hcu.adaxplugin.handler.ConfigTemplateRequestHandler;
import de.nonnull.hcu.adaxplugin.handler.ConfigUpdateRequestHandler;
import de.nonnull.hcu.adaxplugin.handler.ControlRequestHandler;
import de.nonnull.hcu.adaxplugin.handler.DeviceInclusionExclusionHandler;
import de.nonnull.hcu.adaxplugin.handler.DiscoverRequestHandler;
import de.nonnull.hcu.adaxplugin.handler.HmipSystemEventHandler;
import de.nonnull.hcu.adaxplugin.handler.HmipSystemResponseHandler;
import de.nonnull.hcu.adaxplugin.handler.PeriodicHandler;
import de.nonnull.hcu.adaxplugin.handler.PluginStateRequestHandler;
import de.nonnull.hcu.adaxplugin.handler.StatusRequestHandler;
import de.nonnull.hcu.adaxplugin.handler.SyncAdaxHeatingVerticle;
import de.nonnull.hcu.adaxplugin.service.ConversionService;
import de.nonnull.hcu.adaxplugin.service.DeviceService;
import de.nonnull.hcu.adaxplugin.service.PersistenceService;
import de.nonnull.hcu.adaxplugin.service.PluginStateService;
import de.nonnull.hcu.adaxplugin.service.RoomMeasuringValuesCache;
import de.nonnull.hcu.adaxplugin.ws.PluginWebsocketClient;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;

public class PluginStarter {
    private static final Logger LOGGER = LogManager.getLogger(PluginStarter.class);

    private static final String PLUGIN_PROPERTIES_FILE = "plugin.properties";

    private final Vertx vertx;
    private final PluginContext context;

    private PluginStarter() {
        vertx = Vertx.vertx();

        final var conversionService = new ConversionService();
        final var persistenceService = new PersistenceService(vertx);
        final var adaxClient = new AdaxRemoteClient(WebClient.create(vertx), persistenceService);
        final var deviceService = new DeviceService(conversionService);
        final var pluginStateService = new PluginStateService(persistenceService);
        final var roomMeasuringValuesCache = new RoomMeasuringValuesCache(deviceService, conversionService);
        context = PluginContext.builder()
                .persistenceService(persistenceService)
                .adaxClient(adaxClient)
                .deviceService(deviceService)
                .pluginStateService(pluginStateService)
                .roomMeasuringValuesCache(roomMeasuringValuesCache)
                .conversionService(conversionService)
                .build();
    }

    public static void main(String[] args) throws IOException {
        PersistenceService.loadPluginProperties().ifPresent(PluginStarter::completeSystemProperties);
        new PluginStarter().run();
    }

    private static void completeSystemProperties(Properties properties) {
        properties.forEach((key, value) -> {
            if (System.getProperties().putIfAbsent(key, value) == null) {
                LOGGER.info("Setting system property {} from resource {} to value {}", key, PLUGIN_PROPERTIES_FILE,
                        value);
            }
        });
    }

    private void run() {
        LOGGER.info("Starting {} plugin…", PLUGIN_NAME);

        final var wsClient = vertx.deployVerticle(() -> new PluginWebsocketClient(context), new DeploymentOptions());

        final var stateService = context.getPluginStateService();

        wsClient.compose(deploymentId -> Future.join(
                List.of(vertx.deployVerticle(() -> new PluginStateRequestHandler(context), new DeploymentOptions()),
                        vertx.deployVerticle(() -> new ConfigTemplateRequestHandler(context), new DeploymentOptions()),
                        vertx.deployVerticle(() -> new ConfigUpdateRequestHandler(context), new DeploymentOptions()),
                        vertx.deployVerticle(() -> new DeviceInclusionExclusionHandler<>(context, InclusionEvent.class),
                                new DeploymentOptions()),
                        vertx.deployVerticle(() -> new DeviceInclusionExclusionHandler<>(context, ExclusionEvent.class),
                                new DeploymentOptions()),
                        vertx.deployVerticle(() -> new ControlRequestHandler(context), new DeploymentOptions()),
                        vertx.deployVerticle(() -> new StatusRequestHandler(context), new DeploymentOptions()),
                        vertx.deployVerticle(() -> new DiscoverRequestHandler(context), new DeploymentOptions()),
                        vertx.deployVerticle(() -> new HmipSystemResponseHandler(context), new DeploymentOptions()),
                        vertx.deployVerticle(() -> new HmipSystemEventHandler(context), new DeploymentOptions()),
                        vertx.deployVerticle(() -> new SyncAdaxHeatingVerticle(context), new DeploymentOptions()))))
        .onSuccess(future -> {
            LOGGER.info("All verticles started successfully");
            final var status = stateService.calculatePluginReadinessStatus();
            LOGGER.info("Plugin status: {}", status);
            sendPluginReadinessStatus(status);

            vertx.setPeriodic(60_000, new PeriodicHandler(vertx, context));
        }).onFailure(throwable -> {
            LOGGER.error("SYSTEM: Error starting verticles", throwable);
            sendPluginReadinessStatus(PluginReadinessStatus.ERROR);
        });
    }

    private void sendPluginReadinessStatus(PluginReadinessStatus status) {
        final var message = context.getPluginStateService().createPluginStateResponseMessage(status);
        sendMessage(message);
    }

    private void sendMessage(PluginMessage<?> message) {
        final var encodedMessage = JsonObject.mapFrom(message).encode();
        LOGGER.info("Sending {}", encodedMessage);
        vertx.eventBus().send(context.getWebSocketHandlerId(), encodedMessage);
    }
}
