# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Homematic IP HCU (Home Control Unit) plugin that bridges the HCU to cloud-connected
ADAX heaters. The HCU exposes each ADAX room as a virtual thermostat device; the plugin
translates HCU heating groups into ADAX REST API calls and (optionally) reports the
ADAX-measured actual temperature back to the HCU.

It is built on the eQ-3 Connect API (`de.eq-3.plugin:connect-api`) and runs as a Vert.x
application. It is based on the Homematic IP
[ConnectAPI Java Example](https://github.com/homematicip/connect-api/tree/main/examples/java/vertx).

## Build & Run

Prerequisites: the `connect-api` dependency (`de.eq-3.plugin:connect-api:1.4.1`) must be
installed into the local Maven repo first — see the README for the two upstream repos to
`mvn clean install`. Without it, the build fails to resolve dependencies.

```bash
mvn clean package                              # build the fat jar (artifact-with-dependencies)
mvn clean package docker:build docker:save     # build + produce target/*-latest.tar.gz to upload to the HCU
mvn exec:java -Dexec.mainClass="de.nonnull.hcu.adaxplugin.PluginStarter"   # run locally
mvn test                                       # run the JUnit test suite
mvn test -Dtest=TokenManagerTest               # run a single test class
mvn test -Dtest=TokenManagerTest#getValidToken_coalescesConcurrentRequests   # run a single test
```

Tests use **JUnit 4** and live under `src/test/java`. `TokenManagerTest` is an
integration-style test that spins up a real local Vert.x HTTP server (no mocking
framework is used). Java release target is **11**. The Docker image targets
**linux/arm64** (the HCU is ARM).

### Local run configuration

`PluginStarter.main` loads `src/main/resources/plugin.properties` and copies each entry
into System properties (via `putIfAbsent`, so real env/system properties win). For local
runs, set `websocket.host`, `websocket.token`, and `persistence.folder` there. On the HCU
these come from the environment instead, and the auth token is read from the `/TOKEN`
file (see `PersistenceService.loadAuthToken`).

## Architecture

### Verticle + event-bus model

Everything is a Vert.x `AbstractVerticle` communicating over the event bus — there are no
direct method calls between handlers. `PluginStarter` builds a single immutable
`PluginContext` (a Lombok `@Value @Builder` holding all shared services) and deploys every
verticle with it.

Message flow:
1. `PluginWebsocketClient` holds the single WebSocket to the HCU. On connect it stores the
   socket's `textHandlerID` in the context; **outbound** messages are sent to the HCU by
   publishing to that handler id on the event bus. It auto-reconnects after 20s on close/error.
2. Inbound HCU messages are decoded to a `PluginMessage` and re-published on the event bus
   under the key `message.getType().getMappingClazz().getName()` (the fully-qualified class
   name of the message body type).
3. Each `PluginMessageHandler<T>` subclass registers an event-bus consumer for its body
   type's class name — that is the routing mechanism. To handle a new message type, add a
   handler that consumes `SomeBodyType.class.getName()` and deploy it in `PluginStarter.run`.

`PluginMessageHandler` (base class) provides `sendMessage` (to the HCU), `createMessage`,
and helpers to re-trigger discover/status and to `publish` a `SyncAdaxHeatingEvent`.

### Handlers (verticles)

- `PluginStateRequestHandler`, `ConfigTemplateRequestHandler`, `ConfigUpdateRequestHandler` —
  plugin lifecycle & configuration UI shown in the HCU web interface.
- `DiscoverRequestHandler` — reports ADAX rooms as HCU devices.
- `ControlRequestHandler` — HCU → ADAX control commands.
- `StatusRequestHandler` — reports device status back to the HCU.
- `DeviceInclusionExclusionHandler<T>` — parameterized by `InclusionEvent`/`ExclusionEvent`.
- `HmipSystemEventHandler` — listens for `GROUP_CHANGED` system events, maps the HCU group
  id to affected `RoomId`s (via the cache), and publishes a `SyncAdaxHeatingEvent` per room.
- `SyncAdaxHeatingVerticle` — see below.
- `PeriodicHandler` — a plain `Handler<Long>` (not a verticle) run every 120s by
  `vertx.setPeriodic`; polls the ADAX API for actual temperatures and pushes `STATUS_EVENT`s
  to the HCU for rooms configured with `INCLUDE`/`EXTRA_DEVICE` actual-temperature handling.

### Debounced heating sync — `SyncAdaxHeatingVerticle`

The core control logic. Incoming `SyncAdaxHeatingEvent`s are **buffered** per `RoomId` (not
applied immediately) and flushed by a 20s periodic timer. This exists to implement window
open/close behavior:
- **Window opening**: applied immediately (heating disabled, target = `MIN_TARGET_TEMPERATURE`).
- **Window closing**: heating resumes after a configurable `windowClosedHeatingDelayMinutes`
  (the `BufferEntry.dueTime` is pushed into the future; the entry only flushes once due).
- Merging repeated events for the same room preserves the delayed due time unless a new
  window-opening event arrives.

Before calling ADAX it checks `RoomMeasuringValuesCache.heatingHasChanged` to avoid redundant
API calls, and skips rooms marked `excludeThermostat`.

### ADAX client — `adax/`

`AdaxRemoteClient` wraps the ADAX REST API (`/rest/v1/content`, `/rest/v1/control`) using the
Vert.x `WebClient`. `TokenManager` handles OAuth token acquisition/refresh; on a 401 the token
is cleared so it is re-fetched. Temperatures on the ADAX side are integer centi-degrees
(e.g. `500` = 5.0°C); `MIN_TARGET_TEMPERATURE`=500, `MAX_TARGET_TEMPERATURE`=3500. Model
classes for request/response JSON live in `adax/model/`.

### Services (`service/`)

- `RoomMeasuringValuesCache` — in-memory cache keyed by `RoomId`, holding both the last known
  ADAX values and HCU values per room. Source of truth for change detection
  (`heatingHasChanged`, `actualTemperatureHasChanged`) and for mapping HCU group ids → rooms.
- `ConversionService` — unit conversions between HCU and ADAX temperature representations,
  clamped to the room config's min/max.
- `DeviceService` — builds HCU `Device`/`Feature` objects from ADAX content.
- `PersistenceService` — persists `PersistentData` (config + credentials) as JSON to
  `<persistence.folder>/plugin.data`, using an atomic write-to-`.NEW`-then-rename scheme with a
  `.OLD` backup. Reads run **blocking** on the Vert.x filesystem API.

### Config & identity

- `RoomId` = `(homeId, roomId)`, serialized as the string `"homeId-roomId"` (see
  `toIdentifier`/`fromIdentifier`). This string form is the device/room identifier used
  throughout the HCU integration and JSON.
- `Configuration`/`RoomConfig` hold per-room settings: exclusion, min/max target temperature,
  `ActualTemperatureHandling` (`INCLUDE`/`EXTRA_DEVICE`/others), and
  `windowClosedHeatingDelayMinutes`.

## Conventions

- **Lombok** is used heavily (`@Value`, `@Builder`, `@Data`, `@RequiredArgsConstructor`,
  `@NonNull`, `@Slf4j`). `lombok.config` sets `lombok.log.fieldName=LOGGER`, so the logger is
  referenced as `LOGGER` (not `log`).
- Logging is **SLF4J → Log4j2** (`log4j2.xml`); do not add Log4j API calls directly.
- JSON everywhere uses Vert.x `JsonObject` / Jackson; custom (de)serializers for
  `JsonObject` live in `util/`.
- Package root: `de.nonnull.hcu.adaxplugin`. Plugin id: `de.nonnull.hcu.adaxplugin`.

## Notes

- HCU minimum version is declared in the `Dockerfile` metadata label (`hcuMinVersion`).
