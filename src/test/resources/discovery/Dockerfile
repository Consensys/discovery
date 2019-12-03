FROM openjdk:11.0.2-jre-slim-stretch


# github source
#ARG repo=https://github.com/pegasyseng/discovery
#ARG branch=master
#ARG revision=NA
#RUN git clone --branch $branch $repo

# copy source (requirement since repo is currently private)
COPY discovery /discovery

ADD test.sh /test.sh
RUN chmod +x /test.sh

EXPOSE 9005/udp

ENTRYPOINT ["/test.sh"]