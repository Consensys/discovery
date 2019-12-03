#!/bin/sh
set -e
export TEST_DURATION=60

echo "System ip info"
ip a

echo "Running discovery discv5 for up to 300 seconds."
(cd /discovery && gradle discv5) & pid=$!
(sleep 300 && kill -9 $pid) &

#apt-get update
#apt-get install net-tools
#ifconfig


