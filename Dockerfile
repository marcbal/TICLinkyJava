FROM arm32v6/openjdk:8u212-jdk-alpine


ADD run.sh target/tic.jar /data/bin/

RUN chmod u+x /data/bin/*

ENV TIC_MQTT_SERVER_IP="127.0.0.1"
ENV TIC_MQTT_HASS_DISCOVERY_PREFIX="homeassistant"
ENV TIC_MQTT_USERNAME="name"
ENV TIC_MQTT_PASSWORD="pass"

WORKDIR /data/workdir

ENTRYPOINT ["/data/bin/run.sh", "-i"]