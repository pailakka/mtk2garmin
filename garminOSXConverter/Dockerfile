FROM i386/ubuntu:latest

RUN apt-get update && apt-get install -y p7zip-full curl

WORKDIR /converter/
ADD convert_mac.sh ./


CMD /converter/convert_mac.sh
