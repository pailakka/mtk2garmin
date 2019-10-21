FROM ubuntu:bionic

RUN apt update && apt install -y software-properties-common

RUN add-apt-repository ppa:ubuntugis/ubuntugis-unstable && \
    apt update && \
    apt upgrade -y && \
    apt install -y git maven openjdk-11-jre openjdk-11-jre libgdal-java


RUN cd /opt && git clone https://github.com/pailakka/mtk2garmin.git && cd mtk2garmin && \
    git checkout dockerize_converter && cd mtk2garmin-converter && mvn clean package
