FROM i386/ubuntu:latest

RUN apt-get update && apt-get install -y p7zip-full curl

WORKDIR /converter/

COPY ./jmc_cli_linux.tar.gz ./jmc_cli_linux.tar.gz
ADD convert_mac.sh ./


CMD /converter/convert_mac.sh
