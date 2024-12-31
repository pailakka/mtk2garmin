FROM ghcr.io/osgeo/gdal:ubuntu-full-3.10.0

RUN apt update && apt install -y curl

RUN curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.1/install.sh | bash && \
    . /root/.bashrc && nvm install 22 && apt update && apt upgrade -y && \
    apt install -y git p7zip-full nodejs default-jdk maven&& \
    npm install -g svgo &&\
    apt clean
