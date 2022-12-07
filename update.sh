#!/bin/sh

cd "${0%/*}" || exit

echo "Creating workdir..."
mkdir -p workdir || exit

# needs the presence of a docker-compose.yml file (use the provided example)

echo "Stopping service..."
docker compose down || exit

echo "Pulling new version..."
git pull || exit

echo "Compiling new version..."
mvn clean package || exit

echo "Starting service"
docker compose up -d || exit