FROM python:3


WORKDIR /opt/mtkgarmin-site

RUN apt update && apt install -y curl rsync && \
    curl "https://d1vvhvl2y92vvt.cloudfront.net/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip" && \
    unzip awscliv2.zip && \
    ./aws/install

COPY . .

RUN chmod +x ./generate_site.sh && pip install --no-cache-dir -r requirements.txt

CMD ./generate_site.sh

