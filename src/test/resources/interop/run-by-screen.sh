#!/bin/sh

#set -o xtrace

# Get the working directory of this script file
DIR=$(dirname $0)

SCRIPT_DIR=`pwd`

# Create the arrays to hold the input files that were specified
INPUTS=()

## WARNING: this script torches any active containers, and networks labeled mynet123

echo "Test starting..."
echo "Removing any active docker containers, and the network with label 'mynet123'"
docker rm -f $(docker container ls -a -q)
docker network rm mynet123

echo "Creating docker network bridge 'mynet123'"
docker network create --subnet=172.18.0.0/16 mynet123

echo "Starting docker containers"
screen -c interop.screenrc

echo "Removing any active docker containers, and the network with label 'mynet123'"
docker rm -f $(docker container ls -a -q)
docker network rm mynet123
echo "Test finished..."

#set +o xtrace