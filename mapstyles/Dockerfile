FROM teemupel/mtk2garmin-ubuntugis-base as builder

WORKDIR /opt/mapstyles
ADD . .
#ADD mtk2garmin_style /mapstyles/mtk2garmin_style
#ADD mtk2garmin_style_noparcel /mapstyles/mtk2garmin_style_noparcel
RUN chmod +x /opt/mapstyles/build_mapstyles.sh && /opt/mapstyles/build_mapstyles.sh

RUN find /mapstyles

FROM alpine:latest as container
WORKDIR /mapstyles
COPY --from=builder /mapstyles /mapstyles
RUN find /mapstyles
