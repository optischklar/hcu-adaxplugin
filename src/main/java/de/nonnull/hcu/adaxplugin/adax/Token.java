package de.nonnull.hcu.adaxplugin.adax;

import java.time.Instant;
import java.util.Optional;

import io.vertx.core.json.JsonObject;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

@Value
@Builder
public class Token {
    private static final long EXPIRY_GAP_SECONDS = 30;
    private static final long DEFAULT_VALIDITY_SECONDS = 3600;

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
        final long expiresIn = Optional.ofNullable(tokenData.getLong("expires_in"))
                .orElse(DEFAULT_VALIDITY_SECONDS);
        return createdAt.plusSeconds(expiresIn - EXPIRY_GAP_SECONDS);
    }

    public String getAccessToken() {
        return tokenData.getString("access_token");
    }

    public String getRefreshToken() {
        return tokenData.getString("refresh_token");
    }
}
