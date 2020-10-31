FROM openjdk:11-jre-slim

WORKDIR /opt/mkgmap

RUN apt update && apt install -y curl

RUN curl --fail --verbose --retry 5 --retry-delay 30 -o mkgmap-latest.tar.gz -L http://www.mkgmap.org.uk/download/mkgmap-latest.tar.gz && \
    tar --extract --verbose --gzip --strip-components=1 --file=mkgmap-latest.tar.gz && \
    curl --fail --verbose --retry 5 --retry-delay 30 -o splitter-latest.tar.gz -L http://www.mkgmap.org.uk/download/splitter-latest.tar.gz && \
    tar --extract --verbose --gzip --strip-components=1 --file=splitter-latest.tar.gz

ADD mkgmap_mtk2garmin.args .
ADD mkgmap_mtk2garmin_noparcel.args .
ADD peruskartta_garmin.txt .
ADD run_mkgmap.sh .

RUN chmod +x run_mkgmap.sh
RUN java -jar -Xmx30G mkgmap.jar --family-id=2501 peruskartta_garmin.txt && mv peruskartta_garmin.typ perus.typ
