package de.nonnull.hcu.adaxplugin.handler;

import static de.nonnull.hcu.adaxplugin.PluginContext.PLUGIN_ID;

import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.eq3.plugin.domain.Body;
import de.eq3.plugin.domain.discover.DiscoverRequest;
import de.eq3.plugin.domain.status.StatusRequest;
import de.eq3.plugin.serialization.PluginMessage;
import de.eq3.plugin.serialization.PluginMessageType;
import de.nonnull.hcu.adaxplugin.PluginContext;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
abstract class PluginMessageHandler<T extends Body> extends AbstractVerticle implements Handler<Message<JsonObject>> {
    private static final Logger LOGGER = LogManager.getLogger(PluginMessageHandler.class);

    protected final PluginContext context;
    protected final Class<T> pluginMessageType;

    @Override
    public void start() {
        vertx.eventBus().consumer(pluginMessageType.getName(), this);
        LOGGER.info("{} verticle started", getClass().getSimpleName());
    }

    @Override
    public void handle(Message<JsonObject> event) {
        if (event == null || event.body() == null) {
            return;
        }
        @SuppressWarnings("unchecked")
        final PluginMessage<T> message = event.body().mapTo(PluginMessage.class);
        handle(message.getId(), message.getBody());
    }

    protected abstract void handle(@NonNull String messageId, @NonNull T body);

    protected void sendMessage(PluginMessage<?> pluginMessage) {
        vertx.eventBus().send(context.getWebSocketHandlerId(), JsonObject.mapFrom(pluginMessage).encode());
    }

    protected <B extends Body> PluginMessage<B> createMessage(String id, PluginMessageType type, B body) {
        return new PluginMessage<>(id, PLUGIN_ID, type, body);
    }

    protected void triggerDiscoverResponse() {
        final PluginMessage<DiscoverRequest> discoveryRequest = new PluginMessage<>(UUID.randomUUID().toString(),
                PLUGIN_ID, PluginMessageType.DISCOVER_REQUEST, new DiscoverRequest());
        vertx.eventBus().publish(DiscoverRequest.class.getName(), JsonObject.mapFrom(discoveryRequest));
    }

    protected void triggerStatusResponse() {
        final PluginMessage<StatusRequest> statusRequest = new PluginMessage<>(UUID.randomUUID().toString(), PLUGIN_ID,
                PluginMessageType.STATUS_REQUEST, new StatusRequest());
        vertx.eventBus().publish(StatusRequest.class.getName(), JsonObject.mapFrom(statusRequest));
    }

    protected void publish(@NonNull ControlAdaxEvent event) {
        vertx.eventBus().publish(ControlAdaxEvent.class.getName(), JsonObject.mapFrom(event));
    }
}
