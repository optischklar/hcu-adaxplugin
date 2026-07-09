package de.nonnull.hcu.adaxplugin.adax;

import static de.nonnull.hcu.adaxplugin.adax.HttpResponseUtil.createFailedFuture;
import static de.nonnull.hcu.adaxplugin.adax.HttpResponseUtil.isOk;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import de.nonnull.hcu.adaxplugin.service.PersistenceService;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TokenManager {
    private final WebClient webClient;
    private final PersistenceService persistenceService;

    private final AtomicReference<Token> tokenRef = new AtomicReference<>();

    /**
     * Handlers waiting for an in-flight token fetch. Guarded by {@link #pending}
     * itself. As long as this list is non-empty a fetch is in progress, so
     * concurrent {@link #getValidToken} calls from different event-loop threads are
     * coalesced into a single authentication/refresh request.
     */
    private final List<Handler<AsyncResult<Token>>> pending = new ArrayList<>();

    public TokenManager(WebClient aWebClient, PersistenceService aPersistenceService) {
        webClient = aWebClient;
        persistenceService = aPersistenceService;
    }

    public void getValidToken(Handler<AsyncResult<Token>> handler) {
        final var token = tokenRef.get();
        if (token != null && !token.isExpired()) {
            handler.handle(Future.succeededFuture(token));
            return;
        }

        final boolean startFetch;
        synchronized (pending) {
            startFetch = pending.isEmpty();
            pending.add(handler);
        }

        if (startFetch) {
            fetchToken();
        }
    }

    private void fetchToken() {
        final var token = tokenRef.get();
        final var refreshToken = token == null ? null : token.getRefreshToken();
        if (refreshToken != null) {
            clearToken();
            refresh(refreshToken);
        } else {
            authenticate();
        }
    }

    private void authenticate() {
        LOGGER.debug("Authenticate");

        final var config = persistenceService.getConfiguration();
        if (config.isEmpty()) {
            completeFetch(Future.failedFuture("Couldn't find plugin configuration"));
            return;
        }

        final var credentials = config.get().getAdaxCredentials();

        if (credentials == null) {
            completeFetch(Future.failedFuture("Couldn't find ADAX credentials"));
            return;
        }

        final var form = MultiMap.caseInsensitiveMultiMap();
        form.add("grant_type", "password");
        form.add("username", credentials.getClientId());
        form.add("password", credentials.getClientSecret());

        webClient.postAbs(credentials.getApiUrl() + "/auth/token")
                .putHeader("Content-Type", "application/x-www-form-urlencoded")
                .sendForm(form, ar -> {
                    if (isOk(ar)) {
                        LOGGER.info("Authentication succeeded");
                        parseTokenResponse(credentials.getApiUrl(), ar.result());
                    } else {
                        LOGGER.error("Authentication failed");
                        completeFetch(createFailedFuture(ar));
                    }
                });
    }

    private void refresh(String refreshToken) {
        LOGGER.debug("Refresh");

        final var config = persistenceService.getConfiguration();
        if (config.isEmpty()) {
            completeFetch(Future.failedFuture("Couldn't find plugin configuration"));
            return;
        }

        final var credentials = config.get().getAdaxCredentials();

        if (credentials == null) {
            completeFetch(Future.failedFuture("Couldn't find ADAX credentials"));
            return;
        }

        final var form = MultiMap.caseInsensitiveMultiMap();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);

        webClient.postAbs(credentials.getApiUrl() + "/auth/token")
                .putHeader("Content-Type", "application/x-www-form-urlencoded")
                .sendForm(form, ar -> {
                    if (isOk(ar)) {
                        LOGGER.info("Refresh succeeded");
                        parseTokenResponse(credentials.getApiUrl(), ar.result());
                    } else {
                        LOGGER.error("Refresh failed. Trying to authenticate.");
                        authenticate();
                    }
                });
    }

    private void parseTokenResponse(String apiUrl, HttpResponse<Buffer> response) {
        try {
            final JsonObject json = response.bodyAsJsonObject();
            if (json.containsKey("access_token")) {
                final var token = Token.builder().apiUrl(apiUrl).tokenData(json).createdAt(Instant.now()).build();
                LOGGER.info("New token will expire at {}", token.getExpiry());
                tokenRef.set(token);
                completeFetch(Future.succeededFuture(token));
            } else {
                completeFetch(
                        Future.failedFuture("Token response does not contain access_token: " + json.encodePrettily()));
            }
        } catch (final Exception e) {
            completeFetch(Future.failedFuture(e));
        }
    }

    private void completeFetch(AsyncResult<Token> result) {
        final List<Handler<AsyncResult<Token>>> toNotify;
        synchronized (pending) {
            toNotify = new ArrayList<>(pending);
            pending.clear();
        }
        toNotify.forEach(handler -> handler.handle(result));
    }

    public void clearToken() {
        LOGGER.info("Clearing token");
        tokenRef.set(null);
    }
}
