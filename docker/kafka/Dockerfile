# Confluent Kafka (KRaft) + Prometheus JMX exporter java agent (broker metrics on :7071/metrics)
FROM confluentinc/cp-kafka:7.4.0
USER root
ARG JMX_EXPORTER_VERSION=1.1.0
ADD https://repo1.maven.org/maven2/io/prometheus/jmx/jmx_prometheus_javaagent/${JMX_EXPORTER_VERSION}/jmx_prometheus_javaagent-${JMX_EXPORTER_VERSION}.jar /opt/jmx/jmx_prometheus_javaagent.jar
COPY jmx-exporter.yml /opt/jmx/jmx-exporter.yml
RUN chmod 644 /opt/jmx/*
USER appuser
