#!/bin/sh

# The run.sh file must be the entry point of the docker container. It is not meant to be run by a user in a terminal.
# Please use update.sh instead

ls -la /dev/ttyTIC
echo "Initializing serial port"
stty -F /dev/ttyTIC 9600 raw cs7 parenb & # make it async so if it locks, it doesnt lock everything
sleep 1

echo "Starting TIC server"
java -cp res -jar /data/bin/tic.jar
