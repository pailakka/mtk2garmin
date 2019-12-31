FROM ubuntu

WORKDIR /opt/publisher
ADD . .

RUN apt-get update && apt-get upgrade -y && apt-get install -y python3 python3-pip

RUN pip3 install -r requirements.txt


RUN chmod +x publish.sh && chmod +x publish.sh

