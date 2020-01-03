FROM ubuntu:bionic

RUN apt update && apt install -y software-properties-common curl

RUN add-apt-repository ppa:ubuntugis/ubuntugis-unstable && \
    curl -sL https://deb.nodesource.com/setup_12.x | bash - && \
    apt update && \
    apt upgrade -y && \
    apt install -y gdal-bin curl git p7zip-full nodejs && \
    npm install -g svgo