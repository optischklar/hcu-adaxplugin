package de.nonnull.hcu.adaxplugin.adax;

import java.time.Instant;

import io.vertx.core.json.JsonObject;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

@Value
@Builder
public class Token {
    @NonNull
    private final String apiUrl;
    @NonNull
    private JsonObject tokenData;
    @NonNull
    private final Instant createdAt;

    public boolean isExpired() {
        final Long expiresIn = tokenData.getLong("expires_in");
        return expiresIn == null
                || (System.currentTimeMillis() > (createdAt.toEpochMilli() + expiresIn * 1000 - 30_000));
    }

    public String getAccessToken() {
        return tokenData.getString("access_token");
    }

    public String getRefreshToken() {
        return tokenData.getString("refresh_token");
    }
}
