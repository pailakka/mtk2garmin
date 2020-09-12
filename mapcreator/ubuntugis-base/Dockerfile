FROM quay.io/azavea/openjdk-gdal:3.1-jdk11-slim

RUN apt update && apt install -y curl

RUN curl -sL https://deb.nodesource.com/setup_12.x | bash - && \
    apt update && \
    apt install -y git p7zip-full nodejs default-jdk maven&& \
    npm install -g svgo &&\
    apt clean