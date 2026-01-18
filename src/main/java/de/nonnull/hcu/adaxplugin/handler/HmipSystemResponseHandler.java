package de.nonnull.hcu.adaxplugin.handler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.eq3.plugin.domain.control.HmipSystemResponse;
import de.nonnull.hcu.adaxplugin.PluginContext;
import io.vertx.core.json.JsonObject;
import lombok.NonNull;

public class HmipSystemResponseHandler extends PluginMessageHandler<HmipSystemResponse> {

    private static final Logger LOGGER = LogManager.getLogger(HmipSystemResponseHandler.class);

    public HmipSystemResponseHandler(PluginContext aContext) {
        super(aContext, HmipSystemResponse.class);
    }

    @Override
    protected void handle(@NonNull String messageId, @NonNull HmipSystemResponse response) {
        LOGGER.trace("Incoming Hmip system response: {}", response);

        final var updateMetaDataJson = JsonObject.mapFrom(response.getBody());
        final var metaDataBuffer = updateMetaDataJson.toBuffer();

        vertx.fileSystem().writeFileBlocking("/tmp/hmipSystemResponse.json", metaDataBuffer);
    }

}
