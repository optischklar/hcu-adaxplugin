package de.nonnull.hcu.adaxplugin.adax;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.nonnull.hcu.adaxplugin.service.PersistenceService;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.ext.web.client.WebClient;

public class AdaxRemoteClient {
    private static final Logger LOGGER = LogManager.getLogger(AdaxRemoteClient.class);

    private final WebClient webClient;
    private final TokenManager tokenManager;

    public AdaxRemoteClient(WebClient aWebClient, PersistenceService aPersistenceService) {
        webClient = aWebClient;
        tokenManager = new TokenManager(webClient, aPersistenceService);
    }

    public void getContent(Handler<AsyncResult<ContentResponse>> handler) {
        tokenManager.getValidToken(ar -> {
            if (ar.failed()) {
                handler.handle(Future.failedFuture(ar.cause()));
                return;
            }

            final var token = ar.result();
            final var accessToken = token.getAccessToken();
            webClient.getAbs(token.getApiUrl() + "/rest/v1/content")
            .putHeader("Authorization", "Bearer " + accessToken).send(resp -> {
                if (resp.succeeded()) {
                    final var jsonObject = resp.result().bodyAsJsonObject();
                    if (jsonObject != null) {
                        final var content = ContentResponse.fromJsonObject(jsonObject);
                        handler.handle(Future.succeededFuture(content));
                    } else {
                        handler.handle(Future.failedFuture("Adax response does not contain a json object"));
                    }
                } else {
                    final var cause = resp.cause();
                    handler.handle(Future.failedFuture(cause));
                }
            });
        });
    }
}
