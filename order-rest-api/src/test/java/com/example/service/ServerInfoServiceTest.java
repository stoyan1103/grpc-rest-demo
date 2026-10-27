package com.example.service;

import org.junit.jupiter.api.Test;

import com.example.grpcdemo.rest.generated.model.ServerInfo;

import static org.assertj.core.api.Assertions.assertThat;

class ServerInfoServiceTest {

    private final ServerInfoService classUnderTest = new ServerInfoService();

    @Test
    void whenGettingServerInfo_thenNameVersionAndStatusAreReturned() {
        assertThat(classUnderTest.getServerInfo()).isEqualTo(new ServerInfo()
            .name("Order REST API")
            .version("1.0.0")
            .status("UP"));
    }
}
