package com.wwh.filebox.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 系统信息接口 / System info endpoint
 * 暴露版本号等不敏感信息，版本号由 pom.xml 经 Maven filtering 注入到 application.yml。
 * Exposes non-sensitive info such as the version, which is injected from pom.xml
 * into application.yml via Maven resource filtering.
 */
@RestController
@RequestMapping("/api/system")
public class SystemController {

    @Value("${filebox.version:dev}")
    private String version;

    /**
     * 返回系统信息（版本号单一来源）/ Returns system info (single source of the version)
     */
    @GetMapping("/info")
    public Map<String, String> info() {
        Map<String, String> info = new HashMap<>();
        info.put("name", "File Box");
        info.put("version", version);
        return info;
    }
}
