package de.nonnull.hcu.adaxplugin.config;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.NonNull;
import lombok.Value;

@Value
public class Credentials {
    @NonNull
    private final String apiUrl;
    @NonNull
    private final String clientId;
    @NonNull
    private final String clientSecret;

    @JsonCreator
    public Credentials(@JsonProperty("apiUrl") @NonNull String aApiUrl,
            @JsonProperty("clientId") @NonNull String aClientId,
            @JsonProperty("clientSecret") @NonNull String aClientSecret) {
        apiUrl = aApiUrl;
        clientId = aClientId;
        clientSecret = aClientSecret;
    }

    @JsonIgnore
    public boolean isComplete() {
        return StringUtils.isNoneBlank(apiUrl, clientId, clientSecret);
    }
}
