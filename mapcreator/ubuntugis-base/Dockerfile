FROM ghcr.io/osgeo/gdal:ubuntu-full-3.6.3

RUN apt update && apt install -y curl

RUN curl -sL https://deb.nodesource.com/setup_16.x | bash - && \
    apt update && \
    apt install -y git p7zip-full nodejs default-jdk maven&& \
    npm install -g svgo &&\
    apt clean
