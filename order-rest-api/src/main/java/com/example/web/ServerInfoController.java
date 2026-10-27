package com.example.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.example.grpcdemo.rest.generated.api.ServerInfoApi;
import com.example.grpcdemo.rest.generated.model.ServerInfo;
import com.example.service.ServerInfoService;

import static org.springframework.http.ResponseEntity.ok;

@RestController
public class ServerInfoController implements ServerInfoApi {

    private final ServerInfoService serverInfoService;

    @Autowired
    public ServerInfoController(ServerInfoService serverInfoService) {
        this.serverInfoService = serverInfoService;
    }

    @Override
    public ResponseEntity<ServerInfo> getServerInfo() {
        return ok(serverInfoService.getServerInfo());
    }
}
