package com.example.config;

import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

class GrpcServerPropertiesTest {

    private static final String GRPC_SERVER_PORT_ENVIRONMENT_VARIABLE = "GRPC_SERVER_PORT";
    private static final int DEFAULT_PORT = 9090;
    private static final int CONFIGURED_PORT = 9191;

    @ParameterizedTest
    @MethodSource("environmentAndExpectedPortProvider")
    void whenCreatingPropertiesFromEnvironment_thenExpectedPortIsUsed(Map<String, String> environment, int expectedPort) {
        assertThat(GrpcServerProperties.fromEnvironment(environment))
            .isEqualTo(new GrpcServerProperties(expectedPort));
    }

    private static Stream<Arguments> environmentAndExpectedPortProvider() {
        return Stream.of(
            Arguments.of(Map.<String, String>of(), DEFAULT_PORT),
            Arguments.of(
                Map.of(GRPC_SERVER_PORT_ENVIRONMENT_VARIABLE, String.valueOf(CONFIGURED_PORT)),
                CONFIGURED_PORT
            )
        );
    }
}
