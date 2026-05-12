FROM amazoncorretto:21

WORKDIR /opt/mkgmap

RUN yum install -y tar gzip

RUN curl --fail --verbose --retry 5 --retry-delay 30 -o mkgmap-latest.tar.gz -L https://www.mkgmap.org.uk/download/mkgmap-r4924.tar.gz && \
    tar --extract --verbose --gzip --strip-components=1 --file=mkgmap-latest.tar.gz && \
    curl --fail --verbose --retry 5 --retry-delay 30 -o splitter-latest.tar.gz -L https://www.mkgmap.org.uk/download/splitter-latest.tar.gz && \
    tar --extract --verbose --gzip --strip-components=1 --file=splitter-latest.tar.gz

ADD mkgmap_mtk2garmin.args .
ADD mkgmap_mtk2garmin_noparcel.args .
ADD mkgmap_mtk2garmin_amoled.args .
ADD mkgmap_mtk2garmin_amoled_noparcel.args .
ADD peruskartta_garmin.txt .
ADD peruskartta_garmin_amoled.txt .
ADD run_mkgmap.sh .

RUN chmod +x run_mkgmap.sh
RUN java -jar -Xmx30G mkgmap.jar --family-id=2501 peruskartta_garmin.txt && mv peruskartta_garmin.typ perus.typ
RUN java -jar -Xmx30G mkgmap.jar --family-id=2501 peruskartta_garmin_amoled.txt && mv peruskartta_garmin_amoled.typ perus_amoled.typ
