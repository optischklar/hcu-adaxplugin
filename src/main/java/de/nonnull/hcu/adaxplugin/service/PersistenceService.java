package de.nonnull.hcu.adaxplugin.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import de.nonnull.hcu.adaxplugin.PluginStarter;
import de.nonnull.hcu.adaxplugin.config.Configuration;
import de.nonnull.hcu.adaxplugin.config.Credentials;
import de.nonnull.hcu.adaxplugin.config.RoomConfig;
import de.nonnull.hcu.adaxplugin.config.RoomId;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystemException;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PersistenceService {
    private static final String PLUGIN_TEMPLATE_AUTH_FOLDER = "persistence.folder";
    private static final String DEFAULT_PATH = "/data";
    private static final String FILE_NAME = "/plugin.data";
    private static final String OLD_FILE_ENDING = ".OLD";
    private static final String NEW_FILE_ENDING = ".NEW";
    private static final String TOKEN_FILE_PATH = "/TOKEN";
    private static final String PLUGIN_PROPERTIES_FILE = "plugin.properties";

    private final Vertx vertx;
    private final AtomicReference<PersistentData> dataRef;

    public PersistenceService(@NonNull Vertx aVertx) {
        vertx = aVertx;
        dataRef = new AtomicReference<PersistentData>(new PersistentData());
        init();
    }

    private void init() {
        final var dir = getStorageDir();
        if (!vertx.fileSystem().existsBlocking(dir)) {
            vertx.fileSystem().mkdirsBlocking(dir);
        }
        loadData();
    }

    private void loadData() {
        try {
            final var storagePath = resolveFilePath(FILE_NAME);
            LOGGER.debug("Loading data from {}", storagePath);
            Buffer fileBuffer;
            if (vertx.fileSystem().existsBlocking(storagePath)) {
                fileBuffer = vertx.fileSystem().readFileBlocking(storagePath);
            } else {
                fileBuffer = vertx.fileSystem().readFileBlocking(storagePath + OLD_FILE_ENDING);
            }
            dataRef.set(fileBuffer.toJsonObject().mapTo(PersistentData.class));
            LOGGER.info("Persistent data successfully read from file");
        } catch (final FileSystemException e) {
            LOGGER.info("Could not read persistent data, creating new");
        } catch (IllegalArgumentException | DecodeException e) {
            LOGGER.info("Could not parse persistent data, creating new");
        }
    }

    public String loadAuthToken() {
        return vertx.fileSystem().readFileBlocking(TOKEN_FILE_PATH).toString().trim();
    }

    public Optional<Configuration> getConfiguration() {
        return Optional.ofNullable(data().getConfiguration());
    }

    public void saveAdaxCredentials(@NonNull Credentials credentials) {
        var config = data().getConfiguration();
        if (config == null) {
            config = new Configuration();
        }

        config.setAdaxCredentials(credentials);

        data().setConfiguration(config);
        persist();
        LOGGER.info("Auth data saved");
    }

    public void saveRoomConfigurations(Map<RoomId, RoomConfig> configs) {
        var config = data().getConfiguration();
        if (config == null) {
            config = new Configuration();
            data().setConfiguration(config);
        }

        config.setRoomConfigurations(configs);
        persist();
        LOGGER.info("Room configurations saved");
    }

    private void persist() {
        final var storagePath = resolveFilePath(FILE_NAME);
        final var updateMetaDataJson = JsonObject.mapFrom(data());
        final var metaDataBuffer = updateMetaDataJson.toBuffer();

        try {
            vertx.fileSystem().writeFileBlocking(storagePath + NEW_FILE_ENDING, metaDataBuffer);
            if (vertx.fileSystem().existsBlocking(storagePath)) {
                Files.move(Paths.get(storagePath), Paths.get(storagePath + OLD_FILE_ENDING),
                        StandardCopyOption.ATOMIC_MOVE);
            }

            Files.move(Paths.get(storagePath + NEW_FILE_ENDING), Paths.get(storagePath),
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (final IOException e) {
            LOGGER.error("Could not persist data: {}", e.getMessage(), e);
        }
    }

    private PersistentData data() {
        return dataRef.get();
    }

    private String resolveFilePath(String filename) {
        return Path.of(getStorageDir(), filename).toString();
    }

    private String getStorageDir() {
        return System.getProperty(PLUGIN_TEMPLATE_AUTH_FOLDER, DEFAULT_PATH);
    }

    public static Optional<Properties> loadPluginProperties() {
        try (var fis = PluginStarter.class.getClassLoader().getResourceAsStream(PLUGIN_PROPERTIES_FILE)) {
            if (fis == null) {
                return Optional.empty();
            }
            final Properties properties = new Properties();
            properties.load(fis);
            return Optional.of(properties);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
