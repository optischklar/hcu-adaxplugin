package de.nonnull.hcu.adaxplugin.handler;

import de.eq3.plugin.domain.status.HmipSystemEvent;
import de.nonnull.hcu.adaxplugin.PluginContext;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class HmipSystemEventHandler extends AbstractVerticle implements Handler<Message<JsonObject>> {

    private static final String EVENT_TYPE_GROUP_CHANGED = "GROUP_CHANGED";

    private final PluginContext context;

    @Override
    public void start() {
        vertx.eventBus().consumer(HmipSystemEvent.class.getName(), this);
        LOGGER.info("{} verticle started", getClass().getSimpleName());
    }

    @Override
    public void handle(Message<JsonObject> message) {
        if (message == null || message.body() == null) {
            return;
        }

        final var body = message.body();


        final var eventTransaction = body.getJsonObject("body").getJsonObject("eventTransaction");
        final var events = eventTransaction.getJsonObject("events");

        final var eventIndexes = events.fieldNames().stream().sorted().toList();
        LOGGER.trace("Got {} events", eventIndexes.size());
        for (final var index : eventIndexes) {
            final var event = events.getJsonObject(index);
            final var pushEventType = event.getString("pushEventType");
            if (EVENT_TYPE_GROUP_CHANGED.equals(pushEventType)) {
                final var group = event.getJsonObject("group");
                final var groupId = group.getString("id");
                final var roomIds = context.getRoomMeasuringValuesCache().listRoomsAssociatedWithHcuGroupId(groupId);
                if (roomIds.isEmpty()) {
                    continue;
                }
                LOGGER.debug("Got event for rooms {}", roomIds);
                for (final var roomId : roomIds) {
                    final var adaxEvent = new SyncAdaxHeatingEvent(roomId, group);
                    publish(adaxEvent);
                }
            }
        }
    }

    private void publish(@NonNull SyncAdaxHeatingEvent event) {
        vertx.eventBus().publish(SyncAdaxHeatingEvent.class.getName(), JsonObject.mapFrom(event));
    }
}
