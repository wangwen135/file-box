package com.wwh.filebox.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wwh.filebox.config.FileBoxPaths;
import com.wwh.filebox.model.AccessStat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/**
 * 访问统计的持久化:启动时读回快照,每 5 分钟 + 关停时写一次全量快照到单个 JSON 文件,
 * 并按保留天数裁剪旧数据。
 * Access-stats persistence: load the snapshot on startup, write a full snapshot every 5 min + on
 * shutdown to a single JSON file, and trim data older than the retention window.
 */
@Component
public class AccessStatsStore {

    private static final Logger logger = LoggerFactory.getLogger(AccessStatsStore.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<AccessStat>> LIST_TYPE = new TypeReference<List<AccessStat>>() {};

    private final AccessStatsRegistry registry;
    private final Path file;
    private final int retentionDays;

    @Autowired
    public AccessStatsStore(
            AccessStatsRegistry registry,
            @Value("${filebox.audit.access-stats-snapshot:logs/access-stats.json}") String snapshotPath,
            @Value("${filebox.audit.access-stats-retention-days:30}") int retentionDays) {
        this.registry = registry;
        this.file = FileBoxPaths.resolveRelOrAbs(snapshotPath);
        this.retentionDays = retentionDays;
    }

    @PostConstruct
    void loadOnStartup() {
        load();
        trim();
    }

    /** 每 5 分钟:落盘 + 裁剪旧数据。/ every 5 min: persist + trim. */
    @Scheduled(fixedDelay = 5 * 60 * 1000L)
    public void scheduledSnapshot() {
        save();
        trim();
    }

    @PreDestroy
    void onShutdown() {
        save();
    }

    private void save() {
        try {
            List<AccessStat> data = registry.snapshotData();
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            byte[] json = MAPPER.writeValueAsBytes(data);
            // 先写临时文件再原子移动,避免写一半被读到 / write temp then atomic-move to avoid partial reads
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.write(tmp, json);
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            logger.warn("Failed to persist access-stats snapshot: {}", e.getMessage());
        }
    }

    private void load() {
        if (!Files.exists(file)) {
            return;
        }
        try {
            List<AccessStat> data = MAPPER.readValue(file.toFile(), LIST_TYPE);
            registry.restoreData(data);
            logger.info("Loaded {} access-stat record(s) from {}", data.size(), file);
        } catch (IOException e) {
            logger.warn("Failed to read access-stats snapshot: {}", e.getMessage());
        }
    }

    private void trim() {
        String keepFrom = LocalDate.now().minusDays(retentionDays).toString();
        registry.trimBefore(keepFrom);
    }
}
