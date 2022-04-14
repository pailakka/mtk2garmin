FROM osgeo/gdal:ubuntu-small-3.4.2

RUN apt update && apt install -y curl

RUN curl -fsSL https://deb.nodesource.com/setup_16.x | bash - && \
    apt update && \
    apt install -y git p7zip-full nodejs default-jdk maven&& \
    npm install -g svgo &&\
    apt clean
