ARG BASE_IMAGE=teemupel/mtk2garmin-ubuntugis-base
FROM ${BASE_IMAGE}

WORKDIR /opt/get_additional_data
COPY get_additional_data.sh grid.zip ./
RUN chmod +x /opt/get_additional_data/get_additional_data.sh

ENTRYPOINT ["/opt/get_additional_data/get_additional_data.sh"]
