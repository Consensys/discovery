#!/bin/sh
set -e
export TEST_DURATION=60
echo "Running discv5"

ls

echo ""
echo "Going in rust-libp2p"
echo ""

cd rust-libp2p
#git checkout cdd5251d29e21a01aa2ffed8cb577a37a0f9e2eb

ls

echo ""
echo "Going in rust-libp2p/examples"
echo ""

cd examples
ls

echo ""
echo "HERE"
echo ""

apt-get update
apt-get install net-tools
ifconfig

ip a

#cargo build

#RUST_BACKTRACE=1 RUST_LOG=trace cargo run --example discv5 -- 127.0.0.1 9005  -Iu4QPXvlyEQi6rA-Go-OUdnBbTZ0lKybVZBaeHtORG2u5PfTvfVhtr0clGHo06jslx_gu1_GKMoiVHhp3yEzEAaHpuAgmlkgnY0gmlwhH8AAAGJc2VjcDI1NmsxoQKYWxKtyIs9FzrExWEWb1icpKJn6DlCn7KgTnsO6-3c4oN0Y3CCIyqDdWRwgiMq false
#RUST_BACKTRACE=1 RUST_LOG=trace cargo run --example discv5 -- 127.0.0.1 9005  -Iu4QICwYxNRJA7jvE9vJ5TuayfdWtGPWsekJdW_HauhH_xrBbzCc1xqhtcY5S57KxgeeFFcvfQB2fwRo0rCnBraJYqAgmlkgnY0gmlwhAAAAACJc2VjcDI1NmsxoQKYWxKtyIs9FzrExWEWb1icpKJn6DlCn7KgTnsO6-3c4oN0Y3CCIyqDdWRwgiMq
#RUST_BACKTRACE=1 RUST_LOG=trace cargo run --example discv5 -- 172.18.0.22 9005  -Iu4QICwYxNRJA7jvE9vJ5TuayfdWtGPWsekJdW_HauhH_xrBbzCc1xqhtcY5S57KxgeeFFcvfQB2fwRo0rCnBraJYqAgmlkgnY0gmlwhAAAAACJc2VjcDI1NmsxoQKYWxKtyIs9FzrExWEWb1icpKJn6DlCn7KgTnsO6-3c4oN0Y3CCIyqDdWRwgiMq false
RUST_BACKTRACE=1 RUST_LOG=trace cargo run --example discv5 -- 0.0.0.0 9005  -Iu4QICwYxNRJA7jvE9vJ5TuayfdWtGPWsekJdW_HauhH_xrBbzCc1xqhtcY5S57KxgeeFFcvfQB2fwRo0rCnBraJYqAgmlkgnY0gmlwhAAAAACJc2VjcDI1NmsxoQKYWxKtyIs9FzrExWEWb1icpKJn6DlCn7KgTnsO6-3c4oN0Y3CCIyqDdWRwgiMq false