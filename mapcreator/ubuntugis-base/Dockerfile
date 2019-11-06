FROM ubuntu:bionic

RUN apt update && apt install -y software-properties-common

RUN add-apt-repository ppa:ubuntugis/ubuntugis-unstable && \
    apt update && \
    apt upgrade -y && \
    apt install -y gdal-bin curl git maven openjdk-11-jre openjdk-11-jre libgdal-java