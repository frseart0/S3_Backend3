FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY . .
ARG MODULE
RUN --mount=type=cache,target=/root/.m2 mvn -pl ${MODULE} -am package -DskipTests -B

FROM eclipse-temurin:21-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
ARG MODULE
COPY --from=build /src/${MODULE}/target/${MODULE}.jar /app/app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
