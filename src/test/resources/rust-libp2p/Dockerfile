FROM rust:latest

## add ip tools
RUN apt-get update && apt-get install -y net-tools

## lighthouse; however, this test focuses on rust-libp2p (which lighthouse depends on)
#ARG branch=master
#RUN git clone --depth 1 --branch $branch https://github.com/sigp/lighthouse.git
#RUN cd lighthouse && make

## rust-libp2p
# discv5

## pull down sources

## select custom interop
ARG rustlibp2prepo=https://github.com/shahankhatch/rust-libp2p.git
ARG rustlibp2pbranch=artemis-interop

## source of interop w/o customizations - not usable
#ARG rustlibp2prepo=https://github.com/sigp/rust-libp2p.git
#ARG rustlibp2pbranch=master
#ARG rustlibp2prevision=cdd5251d29e21a01aa2ffed8cb577a37a0f9e2eb
#ARG rustlibp2pbranch=discv5 ##actual discv5 branch for rust-libp2p

RUN git clone --branch $rustlibp2pbranch $rustlibp2prepo
RUN cd /rust-libp2p && git checkout $rustlibp2prevision && cargo build --example discv5
#RUN cd /rust-libp2p/examples && cargo build

ADD test.sh /test.sh
RUN chmod +x /test.sh

EXPOSE 9003/udp
ENTRYPOINT ["/test.sh"]