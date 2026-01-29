# hcu-adaxplugin

Homematic IP HCU Plugin to control ADAX heaters. Based on the Homematic IP [ConnectAPI Java Example](https://github.com/homematicip/connect-api/tree/main/examples/java/vertx).

## Build

To be able to build this Maven project you need to download and install the dependencies [connect-api-documentation-model](https://github.com/homematicip/connect-api-documentation-model) and [connect-api-java](https://github.com/homematicip/connect-api-java).

Install both with:

```
mvn clean install
```
You should then be abled to build this project with

```
mvn clean package
```
 
## Start plugin from the command line

To start the plugin on your computer, you need to modify `src/main/resources/plugin.properties`.

- Set `websocket.host` to the host name or IP address of your Home Control Unit.
- Add the `websocket.token` you received from your Home Control Unit for the plugin ID `de.nonnull.hcu.adaxplugin`. (See 2.4. "Get an authorization token" of the [Homematic IP Connect API Documentation](https://github.com/homematicip/connect-api)).
- Define a `persistence.folder` where the plugin can write its configuration data.

```
websocket.host=192.168.xxx.xxx
websocket.token=<TOKEN>
persistence.folder=/data
```

You can then start the plugin as follows:

```
mvn exec:java -Dexec.mainClass="de.nonnull.hcu.adaxplugin.PluginStarter"
```