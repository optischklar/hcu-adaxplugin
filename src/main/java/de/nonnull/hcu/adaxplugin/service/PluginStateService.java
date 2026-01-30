package de.nonnull.hcu.adaxplugin.service;

import static de.nonnull.hcu.adaxplugin.PluginContext.LANG_EN;
import static de.nonnull.hcu.adaxplugin.PluginContext.PLUGIN_ID;
import static de.nonnull.hcu.adaxplugin.PluginContext.PLUGIN_NAME;

import java.util.Map;
import java.util.UUID;

import de.eq3.plugin.domain.plugin.PluginReadinessStatus;
import de.eq3.plugin.domain.plugin.PluginStateResponse;
import de.eq3.plugin.serialization.PluginMessage;
import de.eq3.plugin.serialization.PluginMessageType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class PluginStateService {
    private final PersistenceService persistenceService;

    public PluginMessage<PluginStateResponse> createPluginStateResponseMessage() {
        final var status = calculatePluginReadinessStatus();
        return createPluginStateResponseMessage(status);
    }

    public PluginMessage<PluginStateResponse> createPluginStateResponseMessage(PluginReadinessStatus status) {
        LOGGER.info("Sending plugin status: {}", status);
        final var pluginNameMap = Map.of(LANG_EN, PLUGIN_NAME);
        final var pluginState = new PluginStateResponse(pluginNameMap, status);
        return new PluginMessage<>(UUID.randomUUID().toString(), PLUGIN_ID,
                PluginMessageType.PLUGIN_STATE_RESPONSE, pluginState);
    }

    public PluginReadinessStatus calculatePluginReadinessStatus() {
        final var optConfig = persistenceService.getConfiguration();
        if (optConfig.isPresent()) {
            final var config = optConfig.get();
            if (config.isAdaxCredentialsComplete()) {
                return config.isRoomConfigurationInitialized() ? PluginReadinessStatus.READY
                        : PluginReadinessStatus.CONFIG_REQUIRED;
            } else {
                LOGGER.error("No ADAX credentials set");
                return PluginReadinessStatus.ERROR;
            }
        }
        return PluginReadinessStatus.CONFIG_REQUIRED;
    }
}
