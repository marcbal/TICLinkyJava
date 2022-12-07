#!/bin/sh

# The run.sh file must be the entry point of the docker container. It is not meant to be run by a user it a terminal.
# Please use update.sh instead

ls -la $TIC_TTY_INPUT
echo "Initializing serial port"
stty -F $TIC_TTY_INPUT 9600 raw cs7 parenb

echo "Starting TIC server"
java -cp res -jar /data/bin/tic.jar
