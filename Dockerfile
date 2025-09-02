FROM maven:3.9.8-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -q -U -e -DskipTests dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -q -U -DskipTests package \
    && JAR_PATH=$(ls target/*-shaded.jar 2>/dev/null || ls target/*.jar | head -n1) \
    && mkdir -p /out \
    && cp "$JAR_PATH" /out/app.jar

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /out/app.jar /app/app.jar
ENTRYPOINT ["java","-jar","/app/app.jar"]


