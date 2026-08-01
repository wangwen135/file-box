package com.wwh.filebox.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wwh.filebox.model.StorageSpace;
import com.wwh.filebox.model.SystemConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 存储空间「列表接口」的容量返回是否正确。
 * 回归:list 里容量一直显示 10GB —— getAllStorageSpaces() 漏了 setMaxSize(返回构造函数默认值),
 * 且 JSON 没有 maxSize 字段(前端读 space.maxSize 取不到)。
 */
class StorageServiceListTest {

    @TempDir
    Path tempDir;

    private StorageService storageService;
    private ConfigService configService;

    @BeforeEach
    void setUp() {
        // 把业务配置指向临时文件,避免读到仓库里的 dev config
        System.setProperty(FileBoxConfigStore.CONFIG_PROPERTY, tempDir.resolve("filebox.yml").toString());
        configService = new ConfigService(new BCryptPasswordEncoder());
        configService.init();
        storageService = new StorageService();
        ReflectionTestUtils.setField(storageService, "configService", configService);
    }

    @Test
    void listReturnsConfiguredMaxSizeAndJsonExposesIt() throws Exception {
        // 构造一个 maxSize=50GB 的存储空间并保存
        SystemConfig config = FileBoxConfigStore.createDefaultConfig("admin", "irrelevant-hash");
        config.getStorageSpaces().get(0).setMaxSize("50GB");
        configService.saveConfig(config);

        // 列表接口应返回配置的 50GB,而不是 StorageSpace 构造函数默认的 10GB
        StorageSpace listed = storageService.getAllStorageSpaces().get(0);
        assertThat(listed.getMaxSizeStr()).isEqualTo("50GB");
        assertThat(listed.getMaxSizeInBytes()).isEqualTo(50L * 1024 * 1024 * 1024);

        // JSON 必须暴露 maxSize 字段(前端 storage.html 读的就是 space.maxSize),
        // 而不只是 maxSizeStr —— 否则前端拿到 undefined、编辑框 parseInt()||10 永远是 10
        String json = new ObjectMapper().writeValueAsString(listed);
        assertThat(json).contains("\"maxSize\":\"50GB\"");
    }
}
