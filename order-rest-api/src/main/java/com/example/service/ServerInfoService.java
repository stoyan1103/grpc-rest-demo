package com.example.service;

import org.springframework.stereotype.Service;

import com.example.grpcdemo.rest.generated.model.ServerInfo;

@Service
public class ServerInfoService {

    private static final ServerInfo SERVER_INFO = new ServerInfo()
        .name("Order REST API")
        .version("1.0.0")
        .status("UP");

    public ServerInfo getServerInfo() {
        return SERVER_INFO;
    }
}
