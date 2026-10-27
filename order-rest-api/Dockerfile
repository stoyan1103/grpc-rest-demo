# syntax=docker/dockerfile:1

FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-25 AS build

WORKDIR /workspace

COPY . .

RUN --mount=type=cache,id=grpc-rest-demo-maven,target=/root/.m2,sharing=locked \
    mvn -B -ntp -pl order-rest-api -am package -DskipTests

FROM sapmachine:25-jre-headless-ubuntu AS runtime

WORKDIR /app

COPY --from=build \
    /workspace/order-rest-api/target/order-rest-api-1.0-SNAPSHOT.jar \
    /app/app.jar

USER 10001:10001

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
