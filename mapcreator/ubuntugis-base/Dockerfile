FROM osgeo/gdal:ubuntu-full-3.1.3

RUN apt update && apt install -y curl

RUN curl -sL https://deb.nodesource.com/setup_12.x | bash - && \
    apt update && \
    apt install -y libgdal-java git p7zip-full nodejs default-jdk maven&& \
    npm install -g svgo &&\
    apt clean