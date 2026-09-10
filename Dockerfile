# One Dockerfile for all three services — the module is a build argument.
#
# Built for Phase 8 (load testing). Running the services from the IDE is fine for
# development, but it is the wrong instrument for measuring throughput: IntelliJ attaches
# -javaagent:idea_rt.jar and runs with -XX:TieredStopAtLevel=1, which caps JIT at C1 and
# suppresses the optimisations that matter under sustained load. A throughput number
# measured that way is not the service's number.
#
# Build:  docker compose -f docker-compose.yml -f docker-compose.load.yml build
# The build stage compiles the whole reactor once per module (-am builds dependencies).

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY wallet-service/pom.xml wallet-service/
COPY bill-service/pom.xml bill-service/
COPY notification-service/pom.xml notification-service/
COPY scaffolding/gateway-simulator/pom.xml scaffolding/gateway-simulator/
COPY scaffolding/biller-simulator/pom.xml scaffolding/biller-simulator/
COPY scaffolding/provider-simulator/pom.xml scaffolding/provider-simulator/

ARG MODULE
# Resolve dependencies against the poms alone, so a source-only change does not
# re-download the world. Failures here are non-fatal — the package step will fetch
# whatever is missing.
RUN mvn -B -q -pl ${MODULE} -am dependency:go-offline || true

COPY . .
RUN mvn -B -pl ${MODULE} -am -DskipTests package

# ---- runtime ----
FROM eclipse-temurin:21-jre
ARG MODULE
WORKDIR /app
COPY --from=build /build/${MODULE}/target/*.jar app.jar

# MaxRAMPercentage, not -Xmx: the JVM must size its heap from the CONTAINER limit, not
# from the Docker VM's 7.65 GiB. Without this, three services each size a heap off the
# whole VM and the machine swaps under load — which would look like a service bottleneck.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]