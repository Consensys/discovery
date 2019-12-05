#!/bin/sh

#set -o xtrace

# Get the working directory of this script file
DIR=$(dirname $0)

SCRIPT_DIR=`pwd`

# Create the arrays to hold the input files that were specified
INPUTS=()

docker rm -f $(docker container ls -a -q)

screen -c interop.screenrc

docker rm -f $(docker container ls -a -q)

#set +o xtrace