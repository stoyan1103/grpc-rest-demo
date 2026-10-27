package com.example.config;

import java.util.Map;

public record GrpcServerProperties(int port) {

    private static final String GRPC_SERVER_PORT_ENV = "GRPC_SERVER_PORT";
    private static final String DEFAULT_PORT = "9090";

    public static GrpcServerProperties fromEnvironment(Map<String, String> environment) {
        return new GrpcServerProperties(Integer.parseInt(environment.getOrDefault(GRPC_SERVER_PORT_ENV, DEFAULT_PORT)));
    }
}
