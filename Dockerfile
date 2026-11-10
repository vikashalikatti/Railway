# Railway API (Spring Boot). Builds the jar inside Docker, so you don't need Java or Maven installed.
#   docker build -t railway-backend:1.13.0 .
# Run it with docker-compose.yml (MySQL + API + web app) or Kubernetes (k8s/).

# ---- 1. build: compile, run the unit tests, package the jar
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY src ./src
# Maven's downloads are cached between builds; RailwayApplicationTests needs a database, so only the unit tests run
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q package -Dtest='*RulesTest,TrainImportTest,*CasesTest' -Dsurefire.failIfNoSpecifiedTests=false

# ---- 2. run: Java runtime only, as a normal user
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S -g 10001 railway && adduser -S -u 10001 -G railway railway
WORKDIR /app
COPY --from=build /src/target/*.jar app.jar
# numeric, so Kubernetes can check runAsNonRoot
USER 10001
ENV TZ=Asia/Kolkata \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -Duser.timezone=Asia/Kolkata"
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health/liveness || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
