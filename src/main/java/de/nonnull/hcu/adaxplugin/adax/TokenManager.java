package de.nonnull.hcu.adaxplugin.adax;

import static de.nonnull.hcu.adaxplugin.adax.HttpResponseUtil.createFailedFuture;
import static de.nonnull.hcu.adaxplugin.adax.HttpResponseUtil.isOk;

import java.time.Instant;
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

    public TokenManager(WebClient aWebClient, PersistenceService aPersistenceService) {
        webClient = aWebClient;
        persistenceService = aPersistenceService;
    }

    public void getValidToken(Handler<AsyncResult<Token>> handler) {
        final var token = tokenRef.get();
        if (token == null || token.isExpired()) {
            if (token == null) {
                authenticate(handler);
            } else {
                final var refreshToken = token.getRefreshToken();
                clearToken();
                refresh(refreshToken, handler);
            }
        } else {
            handler.handle(Future.succeededFuture(token));
        }
    }

    private void authenticate(Handler<AsyncResult<Token>> handler) {
        LOGGER.debug("Authenticate");

        final var config = persistenceService.getConfiguration();
        if (config.isEmpty()) {
            handler.handle(Future.failedFuture("Couldn't find plugin configuration"));
            return;
        }

        final var credentials = config.get().getAdaxCredentials();

        if (credentials == null) {
            handler.handle(Future.failedFuture("Couldn't find ADAX credentials"));
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
                        parseTokenResponse(credentials.getApiUrl(), ar.result(), handler);
                    } else {
                        LOGGER.error("Authentication failed");
                        handler.handle(createFailedFuture(ar));
                    }
                });
    }

    private void refresh(String refreshToken, Handler<AsyncResult<Token>> handler) {
        LOGGER.debug("Refresh");

        if (refreshToken == null) {
            authenticate(handler);
            return;
        }

        final var config = persistenceService.getConfiguration();
        if (config.isEmpty()) {
            handler.handle(Future.failedFuture("Couldn't find plugin configuration"));
            return;
        }

        final var credentials = config.get().getAdaxCredentials();

        if (credentials == null) {
            handler.handle(Future.failedFuture("Couldn't find ADAX credentials"));
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
                        parseTokenResponse(credentials.getApiUrl(), ar.result(), handler);
                    } else {
                        LOGGER.error("Refresh failed. Trying to authenticate.");
                        authenticate(handler);
                    }
                });
    }

    private void parseTokenResponse(String apiUrl, HttpResponse<Buffer> response, Handler<AsyncResult<Token>> handler) {
        try {
            final JsonObject json = response.bodyAsJsonObject();
            if (json.containsKey("access_token")) {
                final var token = Token.builder().apiUrl(apiUrl).tokenData(json).createdAt(Instant.now()).build();
                LOGGER.info("New token will expire at {}", token.getExpiry());
                tokenRef.set(token);
                handler.handle(Future.succeededFuture(token));
            } else {
                handler.handle(
                        Future.failedFuture("Token response does not contain access_token: " + json.encodePrettily()));
            }
        } catch (final Exception e) {
            handler.handle(Future.failedFuture(e));
        }
    }

    public void clearToken() {
        LOGGER.info("Clearing token");
        tokenRef.set(null);
    }
}
