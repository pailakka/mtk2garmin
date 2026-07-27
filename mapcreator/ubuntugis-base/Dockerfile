ARG GDAL_BASE_IMAGE=ghcr.io/osgeo/gdal:ubuntu-full-3.13.1
FROM ${GDAL_BASE_IMAGE}

RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        ca-certificates \
        curl \
        git \
        maven \
        nodejs \
        npm \
        openjdk-21-jdk \
        osmium-tool \
        p7zip-full \
    && npm install --global svgo \
    && rm -rf /var/lib/apt/lists/*
