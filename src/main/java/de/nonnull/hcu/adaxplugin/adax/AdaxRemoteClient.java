package de.nonnull.hcu.adaxplugin.adax;

import static de.nonnull.hcu.adaxplugin.adax.HttpResponseUtil.createFailedFuture;
import static de.nonnull.hcu.adaxplugin.adax.HttpResponseUtil.isOk;
import static de.nonnull.hcu.adaxplugin.adax.HttpResponseUtil.isUnauthorized;

import java.util.List;

import de.nonnull.hcu.adaxplugin.adax.model.ContentResponse;
import de.nonnull.hcu.adaxplugin.adax.model.ControlRequest;
import de.nonnull.hcu.adaxplugin.adax.model.ControlRequestRoom;
import de.nonnull.hcu.adaxplugin.adax.model.ControlResponse;
import de.nonnull.hcu.adaxplugin.config.RoomId;
import de.nonnull.hcu.adaxplugin.service.PersistenceService;
import de.nonnull.hcu.adaxplugin.service.RoomMeasuringValuesCache;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.ext.web.client.WebClient;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AdaxRemoteClient {
    public static final int MIN_TARGET_TEMPERATURE = 500;
    public static final int MAX_TARGET_TEMPERATURE = 3500;
    
    private final WebClient webClient;
    private final TokenManager tokenManager;
    private final RoomMeasuringValuesCache valuesCache;

    public AdaxRemoteClient(WebClient aWebClient, PersistenceService aPersistenceService,
            RoomMeasuringValuesCache aValuesCache) {
        webClient = aWebClient;
        tokenManager = new TokenManager(webClient, aPersistenceService);
        valuesCache = aValuesCache;
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
        control(request, ar -> {
            if (ar.succeeded()) {
                valuesCache.putAdaxHeatingValues(roomId, heatingEnabled, targetTemperature);
            }
            handler.handle(ar);
        });
    }

    private void control(ControlRequest request, Handler<AsyncResult<ControlResponse>> handler) {
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
}
