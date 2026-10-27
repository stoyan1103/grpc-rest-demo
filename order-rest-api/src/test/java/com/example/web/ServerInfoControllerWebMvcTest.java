package com.example.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.grpcdemo.rest.generated.model.ServerInfo;
import com.example.service.ServerInfoService;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ServerInfoController.class)
class ServerInfoControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ServerInfoService serverInfoService;

    @Test
    void whenGetServerInfo_thenServerInfoResponseIsReturned() throws Exception {
        when(serverInfoService.getServerInfo()).thenReturn(new ServerInfo()
            .name("Order REST API")
            .version("1.0.0")
            .status("UP"));

        mockMvc.perform(get("/server-info"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Order REST API"))
            .andExpect(jsonPath("$.version").value("1.0.0"))
            .andExpect(jsonPath("$.status").value("UP"));

        verify(serverInfoService).getServerInfo();
    }
}
