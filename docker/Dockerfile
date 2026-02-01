FROM --platform=linux/arm64/v8 ghcr.io/homematicip/alpine-jre:0.0.1
COPY maven/ /app/
WORKDIR /app
ENTRYPOINT java -Xmx32M -cp "/app/*" de.nonnull.hcu.adaxplugin.PluginStarter
LABEL de.eq3.hmip.plugin.metadata="{\"pluginId\":\"de.nonnull.hcu.adaxplugin\", \
  \"version\":\"${project.version}\", \
  \"hcuMinVersion\":\"1.4.7\", \
  \"friendlyName\":{\"de\":\"ADAX-Plugin\",\"en\":\"ADAX-Plugin\"}, \
  \"changelog\": \"New Features\",\
  \"issuer\":\"optischklar\", \
  \"description\":{\"de\":\"HCU Plugin zur Steuerung von ADAX Heizkörpern.\",\"en\":\"HCU Plugin to control ADAX heaters.\"}, \
  \"scope\":\"CLOUD\", \
  \"logsEnabled\": true, \
  \"image\":null \
  }"