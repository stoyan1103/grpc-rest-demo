# syntax=docker/dockerfile:1

FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-25 AS build

WORKDIR /workspace

COPY . .

RUN --mount=type=cache,id=grpc-rest-demo-maven,target=/root/.m2,sharing=locked \
    mvn -B -ntp -pl order-grpc-server -am package -DskipTests

FROM sapmachine:25-jre-headless-ubuntu AS runtime

WORKDIR /app

COPY --from=build \
    /workspace/order-grpc-server/target/runtime-dependencies/ \
    /app/lib/

COPY --from=build \
    /workspace/order-grpc-server/target/order-grpc-server-1.0-SNAPSHOT.jar \
    /app/app.jar

USER 10001:10001

EXPOSE 9090

ENTRYPOINT ["java", "-cp", "/app/app.jar:/app/lib/*", "com.example.OrderGrpcServerApplication"]
