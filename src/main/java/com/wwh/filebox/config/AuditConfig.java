package com.wwh.filebox.config;

import com.wwh.filebox.service.AccessStatsRegistry;
import com.wwh.filebox.service.ActiveTransferRegistry;
import com.wwh.filebox.service.JsonlLog;
import com.wwh.filebox.service.LoginLogReader;
import com.wwh.filebox.service.LoginRecorder;
import com.wwh.filebox.service.TransferLogReader;
import com.wwh.filebox.service.TransferRecorder;
import com.wwh.filebox.service.TransferStatsCalculator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 审计日志(传输 + 登录)的 Spring 装配:文件路径与"保留最近 N 条"上限来自 application.yml(filebox.audit.*)。
 * Spring wiring for the audit logs (transfers + logins): paths and "keep last N" cap come from
 * application.yml (filebox.audit.*).
 */
@Configuration
@EnableScheduling
public class AuditConfig {

    @Value("${filebox.audit.transfer-log:logs/transfer-history.jsonl}")
    private String transferLogPath;

    @Value("${filebox.audit.login-log:logs/login-history.jsonl}")
    private String loginLogPath;

    @Value("${filebox.audit.max-records:100000}")
    private int maxRecords;

    @Value("${filebox.audit.access-stats-max-entries:500000}")
    private int accessStatsMaxEntries;

    @Bean
    public ActiveTransferRegistry activeTransferRegistry() {
        return new ActiveTransferRegistry();
    }

    @Bean
    public AccessStatsRegistry accessStatsRegistry() {
        return new AccessStatsRegistry(accessStatsMaxEntries);
    }

    /**
     * 显式提供一个 TaskScheduler:@EnableScheduling 与 WebSocket 自动配置都会要 TaskScheduler,
     * 不显式给的话,WebSocket 那边的 defaultSockJsTaskScheduler(NullBean)会让调度器解析失败。
     * Provide an explicit TaskScheduler: both @EnableScheduling and WebSocket auto-config need one;
     * without this, the WebSocket defaultSockJsTaskScheduler (NullBean) breaks scheduler resolution.
     */
    @Bean(destroyMethod = "shutdown")
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("audit-sched-");
        return scheduler;
    }

    @Bean
    public JsonlLog transferLog() {
        return new JsonlLog(absolute(transferLogPath), maxRecords);
    }

    @Bean
    public JsonlLog loginLog() {
        return new JsonlLog(absolute(loginLogPath), maxRecords);
    }

    @Bean
    public TransferLogReader transferLogReader(JsonlLog transferLog) {
        return new TransferLogReader(transferLog.getFile());
    }

    @Bean
    public LoginLogReader loginLogReader(JsonlLog loginLog) {
        return new LoginLogReader(loginLog.getFile());
    }

    @Bean
    public TransferStatsCalculator transferStatsCalculator() {
        return new TransferStatsCalculator();
    }

    @Bean
    public TransferRecorder transferRecorder(ActiveTransferRegistry registry, JsonlLog transferLog) {
        return new TransferRecorder(registry, transferLog);
    }

    @Bean
    public LoginRecorder loginRecorder(JsonlLog loginLog) {
        return new LoginRecorder(loginLog);
    }

    private static Path absolute(String path) {
        return Paths.get(path).toAbsolutePath().normalize();
    }
}
