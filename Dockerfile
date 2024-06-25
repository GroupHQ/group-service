FROM ghcr.io/grouphq/group-service

ARG cert=https://truststore.pki.rds.amazonaws.com/us-east-1/us-east-1-bundle.pem

ADD $cert /home/cnb/.postgresql/root.crt