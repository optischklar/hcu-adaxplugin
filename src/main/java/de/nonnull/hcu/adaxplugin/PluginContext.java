package de.nonnull.hcu.adaxplugin;

import java.util.concurrent.atomic.AtomicReference;

import de.nonnull.hcu.adaxplugin.adax.AdaxRemoteClient;
import de.nonnull.hcu.adaxplugin.service.ConversionService;
import de.nonnull.hcu.adaxplugin.service.DeviceService;
import de.nonnull.hcu.adaxplugin.service.PersistenceService;
import de.nonnull.hcu.adaxplugin.service.PluginStateService;
import de.nonnull.hcu.adaxplugin.service.RoomMeasuringValuesCache;
import lombok.Builder;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@RequiredArgsConstructor
@Builder
public class PluginContext {
    public static final String PLUGIN_ID = "de.nonnull.hcu.adaxplugin";
    public static final String PLUGIN_NAME = "ADAX-Plugin";

    public static final String LANG_EN = "en";

    private static final String TOKEN_PROPERTY = "websocket.token";

    @NonNull
    private final PersistenceService persistenceService;
    @NonNull
    private final AdaxRemoteClient adaxClient;
    @NonNull
    private final DeviceService deviceService;
    @NonNull
    private final PluginStateService pluginStateService;
    @NonNull
    private final AtomicReference<String> webSocketHandlerIdRef = new AtomicReference<String>();
    @NonNull
    private final RoomMeasuringValuesCache roomMeasuringValuesCache;
    @NonNull
    private final ConversionService conversionService;

    public String getAuthToken() {
        final var propertyToken = System.getProperty(TOKEN_PROPERTY, null);
        if (propertyToken != null) {
            return propertyToken;
        }
        return persistenceService.loadAuthToken();
    }

    public String getWebSocketHandlerId() {
        return webSocketHandlerIdRef.get();
    }

    public void setWebSocketHandlerId(String id) {
        webSocketHandlerIdRef.set(id);
    }
}
