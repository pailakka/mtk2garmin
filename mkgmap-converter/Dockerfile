FROM openjdk:11-jre-slim

WORKDIR /opt/mkgmap

ADD mkgmap_mtk2garmin.args .
ADD mkgmap_mtk2garmin_noparcel.args .
ADD peruskartta_garmin.txt .
ADD run_mkgmap.sh .

RUN apt update && apt install -y curl && \
    chmod +x run_mkgmap.sh

RUN curl --fail --verbose --retry 5 --retry-delay 30 -o mkgmap-latest.tar.gz -L http://www.mkgmap.org.uk/download/mkgmap-latest.tar.gz && \
    tar --extract --verbose --gzip --strip-components=1 --file=mkgmap-latest.tar.gz && \
    curl --fail --verbose --retry 5 --retry-delay 30 -o splitter-latest.tar.gz -L http://www.mkgmap.org.uk/download/splitter-latest.tar.gz && \
    tar --extract --verbose --gzip --strip-components=1 --file=splitter-latest.tar.gz && \
    java -cp "mkgmap.jar:lib/*jar" uk.me.parabola.mkgmap.main.TypCompiler peruskartta_garmin.txt peruskartta.typ
