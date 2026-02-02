package de.nonnull.hcu.adaxplugin.adax;

import java.util.List;

import de.nonnull.hcu.adaxplugin.adax.model.ContentResponse;
import de.nonnull.hcu.adaxplugin.adax.model.ControlRequest;
import de.nonnull.hcu.adaxplugin.adax.model.ControlRequestRoom;
import de.nonnull.hcu.adaxplugin.adax.model.ControlResponse;
import de.nonnull.hcu.adaxplugin.config.RoomId;
import de.nonnull.hcu.adaxplugin.service.PersistenceService;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AdaxRemoteClient {
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
                    .putHeader("Authorization", "Bearer " + accessToken)
                    .send(resp -> {
                        if (isOk(resp)) {
                            final var content = resp.result().bodyAsJson(ContentResponse.class);
                            LOGGER.debug("Got response: {}", content);
                            if (content != null) {
                                handler.handle(Future.succeededFuture(content));
                            } else {
                                handler.handle(Future.failedFuture("Adax response does not contain a json object"));
                            }
                        } else {
                            if (isUnauthorized(resp)) {
                                tokenManager.clearToken();
                            }
                            handler.handle(createFailedFuture(resp));
                        }
                    });
        });
    }

    public void controlRoom(@NonNull RoomId roomId, boolean heatingEnabled, int targetTemperature,
            Handler<AsyncResult<ControlResponse>> handler) {
        final var controlRequestRoom = ControlRequestRoom.builder().id(roomId.getRoomId())
                .heatingEnabled(heatingEnabled)
                .targetTemperature(targetTemperature).build();
        final var request = new ControlRequest(List.of(controlRequestRoom));
        control(request, handler);
    }

    public void control(ControlRequest request, Handler<AsyncResult<ControlResponse>> handler) {
        tokenManager.getValidToken(ar -> {
            if (ar.failed()) {
                handler.handle(Future.failedFuture(ar.cause()));
                return;
            }

            final var token = ar.result();
            final var accessToken = token.getAccessToken();
            webClient.postAbs(token.getApiUrl() + "/rest/v1/control")
                    .putHeader("Authorization", "Bearer " + accessToken)
                    .sendJson(request, resp -> {
                        if (isOk(resp)) {
                            final var control = resp.result().bodyAsJson(ControlResponse.class);
                            LOGGER.debug("Got response: {}", control);
                            if (control != null) {
                                handler.handle(Future.succeededFuture(control));
                            } else {
                                handler.handle(Future.failedFuture("Adax response does not contain a json object"));
                            }
                        } else {
                            if (isUnauthorized(resp)) {
                                tokenManager.clearToken();
                            }
                            handler.handle(createFailedFuture(resp));
                        }
                    });
        });
    }

    private boolean isOk(AsyncResult<HttpResponse<Buffer>> result) {
        if (result.failed()) {
            return false;
        }
        final var response = result.result();
        return response.statusCode() >= 200 && response.statusCode() < 300;
    }

    private boolean isUnauthorized(AsyncResult<HttpResponse<Buffer>> result) {
        return result.succeeded() && result.result().statusCode() == 401;
    }

    private <T> Future<T> createFailedFuture(AsyncResult<HttpResponse<Buffer>> result) {
        if (result.succeeded()) {
            final var httpResponse = result.result();
            return Future.failedFuture(httpResponse.statusCode() + ": " + httpResponse.bodyAsString());
        } else {
            return Future.failedFuture(result.cause());
        }
    }
}
