FROM ubuntu

WORKDIR /opt/mapmerge
RUN apt-get update && apt-get upgrade -y && apt-get install -y build-essential curl zlib1g-dev

RUN curl --fail --retry 5 --retry-delay 30 http://m.m.i24.cc/osmconvert.c | cc -x c - -lz -O3 -o osmconvert && \
    curl --fail --retry 5 --retry-delay 30 http://m.m.i24.cc/osmfilter.c |cc -x c - -O3 -o osmfilter && \
    chmod +x osmconvert && \
    chmod +x osmfilter


