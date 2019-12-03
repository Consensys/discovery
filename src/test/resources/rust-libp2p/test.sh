#!/bin/sh
set -e

ifconfig

#ip a

echo "Running rust-libp2p discv5"

cd rust-libp2p/examples
#git checkout cdd5251d29e21a01aa2ffed8cb577a37a0f9e2eb

## attempts to try and catch the ip and pk of the other peer
#RUST_BACKTRACE=1 RUST_LOG=trace cargo run --example discv5 -- 127.0.0.1 9005  -Iu4QPXvlyEQi6rA-Go-OUdnBbTZ0lKybVZBaeHtORG2u5PfTvfVhtr0clGHo06jslx_gu1_GKMoiVHhp3yEzEAaHpuAgmlkgnY0gmlwhH8AAAGJc2VjcDI1NmsxoQKYWxKtyIs9FzrExWEWb1icpKJn6DlCn7KgTnsO6-3c4oN0Y3CCIyqDdWRwgiMq false
#RUST_BACKTRACE=1 RUST_LOG=trace cargo run --example discv5 -- 127.0.0.1 9005  -Iu4QICwYxNRJA7jvE9vJ5TuayfdWtGPWsekJdW_HauhH_xrBbzCc1xqhtcY5S57KxgeeFFcvfQB2fwRo0rCnBraJYqAgmlkgnY0gmlwhAAAAACJc2VjcDI1NmsxoQKYWxKtyIs9FzrExWEWb1icpKJn6DlCn7KgTnsO6-3c4oN0Y3CCIyqDdWRwgiMq
#RUST_BACKTRACE=1 RUST_LOG=trace cargo run --example discv5 -- 172.18.0.22 9005  -Iu4QICwYxNRJA7jvE9vJ5TuayfdWtGPWsekJdW_HauhH_xrBbzCc1xqhtcY5S57KxgeeFFcvfQB2fwRo0rCnBraJYqAgmlkgnY0gmlwhAAAAACJc2VjcDI1NmsxoQKYWxKtyIs9FzrExWEWb1icpKJn6DlCn7KgTnsO6-3c4oN0Y3CCIyqDdWRwgiMq false
#RUST_BACKTRACE=1 RUST_LOG=trace cargo run --example discv5 -- 172.17.0.3 9005  -Iu4QICwYxNRJA7jvE9vJ5TuayfdWtGPWsekJdW_HauhH_xrBbzCc1xqhtcY5S57KxgeeFFcvfQB2fwRo0rCnBraJYqAgmlkgnY0gmlwhAAAAACJc2VjcDI1NmsxoQKYWxKtyIs9FzrExWEWb1icpKJn6DlCn7KgTnsO6-3c4oN0Y3CCIyqDdWRwgiMq false
RUST_BACKTRACE=1 RUST_LOG=trace cargo run --example discv5 -- 172.18.0.3 9003  -Iu4QPVC2AkAf8x-dKHtlc6yfJeNl92__l_ClzbjX9hLhFwuW0LjmAno023amwOo1yuaGwWk5Q0KFumMB5wzHz9h-G-AgmlkgnY0gmlwhKwSAAKJc2VjcDI1NmsxoQKYWxKtyIs9FzrExWEWb1icpKJn6DlCn7KgTnsO6-3c4oN0Y3CCIyqDdWRwgiMq false # remote 172.18.0.2