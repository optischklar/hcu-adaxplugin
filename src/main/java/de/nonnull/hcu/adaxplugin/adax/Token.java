package de.nonnull.hcu.adaxplugin.adax;

import java.time.Instant;

import io.vertx.core.json.JsonObject;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

@Value
@Builder
public class Token {
    private static final long EXPIRY_GAP_SECONDS = 30;

    @NonNull
    private final String apiUrl;
    @NonNull
    private JsonObject tokenData;
    @NonNull
    private final Instant createdAt;

    public boolean isExpired() {
        return Instant.now().isAfter(getExpiry());
    }

    public Instant getExpiry() {
        final Long expiresIn = tokenData.getLong("expires_in");
        if (expiresIn != null) {
            return createdAt.plusSeconds(expiresIn - EXPIRY_GAP_SECONDS);
        }
        return createdAt;
    }

    public String getAccessToken() {
        return tokenData.getString("access_token");
    }

    public String getRefreshToken() {
        return tokenData.getString("refresh_token");
    }
}
