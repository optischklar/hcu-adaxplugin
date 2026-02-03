package de.nonnull.hcu.adaxplugin.adax;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;

class HttpResponseUtil {
    private HttpResponseUtil() {
        throw new UnsupportedOperationException();
    }

    public static boolean isOk(AsyncResult<HttpResponse<Buffer>> result) {
        if (result.failed()) {
            return false;
        }
        final var response = result.result();
        return response.statusCode() >= 200 && response.statusCode() < 300;
    }

    public static boolean isUnauthorized(AsyncResult<HttpResponse<Buffer>> result) {
        return result.succeeded() && result.result().statusCode() == 401;
    }

    public static <T> Future<T> createFailedFuture(AsyncResult<HttpResponse<Buffer>> result) {
        if (result.succeeded()) {
            final var httpResponse = result.result();
            return Future.failedFuture(httpResponse.statusCode() + ": " + httpResponse.bodyAsString());
        } else {
            return Future.failedFuture(result.cause());
        }
    }
}
