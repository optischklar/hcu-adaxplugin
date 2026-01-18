package de.nonnull.hcu.adaxplugin.ws;

import static de.nonnull.hcu.adaxplugin.PluginContext.PLUGIN_ID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.eq3.plugin.Headers;
import de.eq3.plugin.serialization.PluginMessage;
import de.nonnull.hcu.adaxplugin.PluginContext;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.WebSocket;
import io.vertx.core.http.WebSocketConnectOptions;
import io.vertx.core.json.JsonObject;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class PluginWebsocketClient extends AbstractVerticle {
    private static final Logger LOGGER = LogManager.getLogger(PluginWebsocketClient.class);

    private static final long RECONNECT_DELAY = 20000L;

    private final PluginContext context;

    @Override
    public void start(Promise<Void> startPromise) {
        connect().onSuccess(startHandler -> {
            startPromise.complete();
            LOGGER.info("{} verticle started", getClass().getSimpleName());
        });

        LOGGER.info("{} verticle starting. Waiting for websocket connection", getClass().getSimpleName());
    }

    private Future<Void> connect() {
        final Promise<Void> success = Promise.promise();
        final HttpClientOptions clientOptions = new HttpClientOptions().setTrustAll(true)
                .setVerifyHost(false)
                .setMaxWebSocketFrameSize(1_024_000)
                .setMaxWebSocketMessageSize(1_024_000);

        final WebSocketConnectOptions connectOptions = new WebSocketConnectOptions()
                .setRegisterWriteHandlers(true)
                .setHost(System.getProperty("websocket.host", "host.containers.internal"))
                .setPort(Integer.valueOf(System.getProperty("websocket.port", "9001")))
                .setSsl(true)
                .putHeader(Headers.PLUGIN_ID.toString(), PLUGIN_ID)
                // .putHeader(Headers.HMIP_SYSTEM_EVENTS.toString(), "true")
                .putHeader(Headers.AUTHTOKEN.toString(), context.getAuthToken());

        final Future<WebSocket> wsConnection = vertx.createHttpClient(clientOptions).webSocket(connectOptions);

        wsConnection.onSuccess(webSocket -> {

            webSocket.closeHandler(aVoid -> {
                LOGGER.info("Closed WebSocket - wsHandlerId: {}", webSocket.textHandlerID());

                context.setWebSocketHandlerId(null);
                vertx.setTimer(RECONNECT_DELAY, timerId -> connect());
            });

            webSocket.exceptionHandler(throwable -> {
                LOGGER.error("WebSocket exception - message: {}, wsHandlerId: {}", throwable.getMessage(),
                        webSocket.textHandlerID());

                webSocket.close();
            });

            webSocket.handler(buffer -> {
                final PluginMessage<?> message = buffer.toJsonObject().mapTo(PluginMessage.class);
                LOGGER.info("Received WS message {}", message);
                vertx.eventBus().send(message.getType().getMappingClazz().getName(), JsonObject.mapFrom(message));
            });

            context.setWebSocketHandlerId(webSocket.textHandlerID());
            success.complete();
        });

        wsConnection.onFailure(throwable -> {
            context.setWebSocketHandlerId(null);
            LOGGER.error("Error opening websocket connection", throwable.fillInStackTrace());
            vertx.setTimer(RECONNECT_DELAY, timerId -> connect().onSuccess(handler -> success.complete()));
        });
        return success.future();
    }
}
