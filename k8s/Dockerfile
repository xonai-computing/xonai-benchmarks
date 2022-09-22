ARG BASE_IMAGE=755674844232.dkr.ecr.us-east-1.amazonaws.com/spark/emr-6.5.0:latest

FROM amazonlinux:2 as tpc-toolkit

RUN yum group install -y "Development Tools" && \
    git clone https://github.com/databricks/tpch-dbgen.git /tmp/tpch-dbgen && \
    cd /tmp/tpch-dbgen && \
    git checkout 0469309147b42abac8857fa61b4cf69a6d3128a8 -- bm_utils.c  && \
    make OS=LINUX

FROM ${BASE_IMAGE}

COPY --from=tpc-toolkit /tmp/tpch-dbgen /opt/tpch-dbgen
