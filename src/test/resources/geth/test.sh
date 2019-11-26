#!/bin/sh
#
# Copyright 2019 [ <ether.camp> ]
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
# the License. You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
# an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
# specific language governing permissions and limitations under the License.
#
# SPDX-License-Identifier: Apache-2.0
#

#set -e
export TEST_DURATION=60
echo "Run interop_test for ${TEST_DURATION} seconds"
cd /go/go-ethereum/p2p/discover && go test -v -run TestNodesServer
