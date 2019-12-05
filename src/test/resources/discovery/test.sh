#!/bin/sh
set -e
#set -o xtrace

## show some system info
echo "System ip info"

## good for debian
#ip a

## good for ubuntu
ifconfig

echo "Running discovery discv5."

## start the server

## build approach
#cd /discovery
#gradle run -x test -x checkLicenses -x spotlessCheck

## run approach
/discovery/build/docker-discovery/discovery/bin/discovery

## for timeout operations
# (gradle run) & pid=$!
#(sleep 300 && kill -9 $pid) &

#set +o xtrace