#FROM openjdk:11.0.2-jre-slim-stretch
FROM gradle:jdk11

## add ip tools
RUN apt-get update && apt-get install -y net-tools

## add gradle (not needed for gradle base image)
#RUN apt-get install -y gradle

## github source
#ARG repo=https://github.com/pegasyseng/discovery
#ARG branch=master
#ARG revision=NA
#RUN git clone --branch $branch $repo

## copy source (requirement since repo is currently private)
ADD . /discovery

## bake dev environment
#RUN cd /discovery && gradle build -x test -x checkLicenses -x spotlessCheck

## set the startup script
ADD src/test/resources/discovery/test.sh /test.sh
RUN chmod +x /test.sh

## TODO make this dynamic
EXPOSE 9002/udp

ENTRYPOINT ["/test.sh"]


## use the following docker command on the host to start the dockerized server
# docker run --rm -ti -p 9002:9002/udp discovery