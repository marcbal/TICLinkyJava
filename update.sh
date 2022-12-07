#!/bin/sh

cd "${0%/*}" || exit

mkdir -p workdir

# needs the presence of a docker-compose.yml file (use the provided example)

docker compose down && \
git pull && \
mvn clean package && \
docker compose up -d