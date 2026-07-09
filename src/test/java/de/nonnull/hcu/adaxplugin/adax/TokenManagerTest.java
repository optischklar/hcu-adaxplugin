package de.nonnull.hcu.adaxplugin.adax;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import de.nonnull.hcu.adaxplugin.config.Credentials;
import de.nonnull.hcu.adaxplugin.service.PersistenceService;
import io.vertx.core.AsyncResult;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;

public class TokenManagerTest {

    private static final long RESPONSE_DELAY_MS = 100;
    private static final long AWAIT_SECONDS = 5;

    private Vertx vertx;
    private HttpServer server;
    private int port;
    private Path tempDir;

    private final AtomicInteger tokenRequestCount = new AtomicInteger();
    private final List<String> grantTypes = Collections.synchronizedList(new ArrayList<>());
    private volatile long tokenValiditySeconds = 3600;

    @Before
    public void setUp() throws Exception {
        vertx = Vertx.vertx();
        tokenRequestCount.set(0);
        grantTypes.clear();

        final var started = new CountDownLatch(1);
        server = vertx.createHttpServer().requestHandler(req -> {
            if (!req.path().endsWith("/auth/token")) {
                req.response().setStatusCode(404).end();
                return;
            }
            req.bodyHandler(body -> {
                final var count = tokenRequestCount.incrementAndGet();
                grantTypes.add(body.toString().contains("grant_type=refresh_token") ? "refresh_token" : "password");
                final var token = new JsonObject()
                        .put("access_token", "access-" + count)
                        .put("refresh_token", "refresh-token")
                        .put("expires_in", tokenValiditySeconds);
                // Delay the response so that concurrent getValidToken calls are all
                // registered before the first request completes.
                vertx.setTimer(RESPONSE_DELAY_MS, t -> req.response()
                        .putHeader("content-type", "application/json").end(token.encode()));
            });
        });
        server.listen(0, "localhost").onComplete(ar -> {
            port = ar.result().actualPort();
            started.countDown();
        });
        assertTrue(started.await(AWAIT_SECONDS, TimeUnit.SECONDS));

        tempDir = Files.createTempDirectory("tokenmanager-test");
        System.setProperty("persistence.folder", tempDir.toString());
    }

    @After
    public void tearDown() throws Exception {
        System.clearProperty("persistence.folder");
        final var closed = new CountDownLatch(1);
        vertx.close().onComplete(ar -> closed.countDown());
        closed.await(AWAIT_SECONDS, TimeUnit.SECONDS);
        deleteRecursively(tempDir);
    }

    private TokenManager newTokenManager() {
        final var persistenceService = new PersistenceService(vertx);
        persistenceService.saveAdaxCredentials(new Credentials("http://localhost:" + port, "user", "secret"));
        return new TokenManager(WebClient.create(vertx), persistenceService);
    }

    @Test
    public void getValidToken_authenticatesAndReturnsToken() throws Exception {
        final var token = awaitToken(newTokenManager());
        assertEquals("access-1", token.getAccessToken());
        assertEquals(1, tokenRequestCount.get());
        assertEquals(Collections.singletonList("password"), new ArrayList<>(grantTypes));
    }

    @Test
    public void getValidToken_reusesCachedTokenOnSecondCall() throws Exception {
        final var tokenManager = newTokenManager();
        awaitToken(tokenManager);
        awaitToken(tokenManager);
        assertEquals("cached token should be reused", 1, tokenRequestCount.get());
    }

    @Test
    public void getValidToken_coalescesConcurrentRequests() throws Exception {
        final var tokenManager = newTokenManager();
        final int callers = 5;
        final var latch = new CountDownLatch(callers);
        final List<AsyncResult<Token>> results = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < callers; i++) {
            tokenManager.getValidToken(ar -> {
                results.add(ar);
                latch.countDown();
            });
        }

        assertTrue(latch.await(AWAIT_SECONDS, TimeUnit.SECONDS));
        assertEquals(callers, results.size());
        results.forEach(result -> {
            assertTrue("every caller should succeed", result.succeeded());
            assertEquals("every caller should get the same token", "access-1", result.result().getAccessToken());
        });
        assertEquals("only a single authentication request expected", 1, tokenRequestCount.get());
    }

    @Test
    public void getValidToken_refreshesExpiredToken() throws Exception {
        tokenValiditySeconds = 0; // token is expired as soon as it is created
        final var tokenManager = newTokenManager();

        awaitToken(tokenManager);
        awaitToken(tokenManager);

        assertEquals(2, tokenRequestCount.get());
        assertEquals(java.util.Arrays.asList("password", "refresh_token"), new ArrayList<>(grantTypes));
    }

    private Token awaitToken(TokenManager tokenManager) throws InterruptedException {
        final var latch = new CountDownLatch(1);
        final var ref = new AtomicReference<AsyncResult<Token>>();
        tokenManager.getValidToken(ar -> {
            ref.set(ar);
            latch.countDown();
        });
        assertTrue(latch.await(AWAIT_SECONDS, TimeUnit.SECONDS));
        assertTrue("token fetch should succeed but failed with: "
                + (ref.get().failed() ? ref.get().cause() : ""), ref.get().succeeded());
        return ref.get().result();
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (var walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (final IOException e) {
                    // best effort cleanup
                }
            });
        }
    }
}
