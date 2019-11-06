FROM teemupel/mtk2garmin-ubuntugis-base

RUN apt install -y maven openjdk-11-jre openjdk-11-jre

RUN cd /opt && git clone https://github.com/pailakka/mtk2garmin.git && cd mtk2garmin && \
    git checkout dockerize_converter && cd mtk2garmin-converter && mvn clean package install
