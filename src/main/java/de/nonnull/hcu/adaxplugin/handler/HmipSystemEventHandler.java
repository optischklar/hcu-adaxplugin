package de.nonnull.hcu.adaxplugin.handler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.eq3.plugin.domain.status.HmipSystemEvent;
import de.nonnull.hcu.adaxplugin.PluginContext;
import lombok.NonNull;

public class HmipSystemEventHandler extends PluginMessageHandler<HmipSystemEvent> {

    private static final Logger LOGGER = LogManager.getLogger(HmipSystemEventHandler.class);

    public HmipSystemEventHandler(PluginContext aContext) {
        super(aContext, HmipSystemEvent.class);
    }

    @Override
    protected void handle(@NonNull String messageId, @NonNull HmipSystemEvent event) {
        LOGGER.info("Event Tx {}", event.getEventTransaction().getClass());
    }

}
