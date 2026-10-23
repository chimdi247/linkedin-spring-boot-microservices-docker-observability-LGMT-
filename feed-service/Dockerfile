# syntax=docker/dockerfile:1.7
# ---------- build ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
# cached dependency layer (re-used until pom.xml changes)
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -Dmaven.test.skip=true dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -Dmaven.test.skip=true package \
 && cp target/*.jar /build/app.jar

# ---------- OpenTelemetry Java agent (auto-instrumentation) ----------
FROM alpine:3.20 AS otel
ARG OTEL_AGENT_VERSION=2.12.0
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_AGENT_VERSION}/opentelemetry-javaagent.jar /opentelemetry-javaagent.jar

# ---------- runtime ----------
FROM eclipse-temurin:17-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* \
 && useradd --system --uid 10001 --create-home app
WORKDIR /app
COPY --from=build /build/app.jar app.jar
COPY --from=otel /opentelemetry-javaagent.jar /otel/opentelemetry-javaagent.jar
USER app
EXPOSE 8083
# Heap is capped low on purpose: the containers are limited in docker-compose and the CPU/memory
# alerts fire at 40 % of the limit. The OpenTelemetry agent is attached with -javaagent below
# and configured with OTEL_* environment variables (see docker-compose.yml).
ENV JAVA_OPTS="-XX:MaxRAMPercentage=20 -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=48m -XX:+UseSerialGC -Xss512k -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=10 \
  CMD curl -fsS http://localhost:8083/actuator/health/readiness || exit 1
ENTRYPOINT ["sh","-c","exec java -javaagent:/otel/opentelemetry-javaagent.jar $JAVA_OPTS -jar /app/app.jar"]
