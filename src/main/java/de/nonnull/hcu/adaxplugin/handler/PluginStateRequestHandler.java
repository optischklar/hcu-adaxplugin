package de.nonnull.hcu.adaxplugin.handler;

import de.eq3.plugin.domain.plugin.PluginStateRequest;
import de.nonnull.hcu.adaxplugin.PluginContext;
import lombok.NonNull;

public class PluginStateRequestHandler extends PluginMessageHandler<PluginStateRequest> {

    public PluginStateRequestHandler(PluginContext aContext) {
        super(aContext, PluginStateRequest.class);
    }

    @Override
    protected void handle(@NonNull String messageId, @NonNull PluginStateRequest request) {
        final var message = context.getPluginStateService().createPluginStateResponseMessage();
        sendMessage(message);
    }

}
