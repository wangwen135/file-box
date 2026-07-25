package com.wwh.filebox.controller;

import com.wwh.filebox.model.TransferDirection;
import com.wwh.filebox.model.TransferRecord;
import com.wwh.filebox.service.AccessStatsRegistry;
import com.wwh.filebox.service.ActiveTransferRegistry;
import com.wwh.filebox.service.LoginLogReader;
import com.wwh.filebox.service.TransferLogReader;
import com.wwh.filebox.service.TransferStatsCalculator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 审计日志相关接口(传输日志、登录日志、当前活跃传输),均在 /api/admin/** 下,拦截器强制 ADMIN。
 * Audit-log endpoints (transfer log, login log, active transfers), all under /api/admin/** which
 * the interceptor enforces ADMIN on.
 */
@RestController
@RequestMapping("/api/admin")
public class AuditController {

    @Autowired
    private TransferLogReader transferLogReader;

    @Autowired
    private LoginLogReader loginLogReader;

    @Autowired
    private TransferStatsCalculator transferStatsCalculator;

    @Autowired
    private ActiveTransferRegistry activeTransferRegistry;

    @Autowired
    private AccessStatsRegistry accessStatsRegistry;

    /**
     * 传输日志(最新在前,分页)。stats 汇总基于整个筛选集(不受分页影响)。
     * Transfer log (newest first, paginated). stats summarize the whole filtered set (unaffected by paging).
     */
    @GetMapping("/transfer-log")
    public ResponseEntity<?> transferLog(
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String user,
            @RequestParam(required = false) String space,
            @RequestParam(required = false) Long from,
            @RequestParam(required = false) Long to,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "50") int limit) {

        TransferDirection dir = parseDirection(direction);
        TransferLogReader.LogFilter filter = new TransferLogReader.LogFilter(dir, user, space, from, to);
        List<TransferRecord> all = transferLogReader.readAll(filter);

        int total = all.size();
        int fromIdx = Math.min(Math.max(offset, 0), total);
        int toIdx = Math.min(fromIdx + Math.max(limit, 0), total);

        Map<String, Object> resp = new HashMap<>();
        resp.put("records", new ArrayList<>(all.subList(fromIdx, toIdx)));
        resp.put("total", total);
        resp.put("stats", transferStatsCalculator.compute(all));
        return ResponseEntity.ok(resp);
    }

    /**
     * 登录日志(最新在前,分页)。/ Login log (newest first, paginated).
     */
    @GetMapping("/login-log")
    public ResponseEntity<?> loginLog(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Boolean success,
            @RequestParam(required = false) Long from,
            @RequestParam(required = false) Long to,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "50") int limit) {

        LoginLogReader.LoginFilter filter = new LoginLogReader.LoginFilter(username, success, from, to);
        LoginLogReader.LoginPage page = loginLogReader.read(filter, offset, limit);

        Map<String, Object> resp = new HashMap<>();
        resp.put("records", page.records);
        resp.put("total", page.total);
        return ResponseEntity.ok(resp);
    }

    /**
     * 当前活跃传输快照(浮窗的初始加载/轮询兜底,实时更新走 WebSocket)。/ Active transfers snapshot
     * (initial load / polling fallback for the widget; live updates go over WebSocket).
     */
    @GetMapping("/active-transfers")
    public ResponseEntity<?> activeTransfers() {
        Map<String, Object> resp = new HashMap<>();
        resp.put("transfers", activeTransferRegistry.snapshot());
        return ResponseEntity.ok(resp);
    }

    /**
     * 访问统计:不传 ip 返回某天各 IP 汇总(按请求数倒序);传 ip 返回该 IP 当天按小时的分布(24 桶)。
     * Access stats: without ip, per-IP day totals (sorted by requests desc); with ip, the IP's
     * 24-hour breakdown for that day.
     */
    @GetMapping("/access-stats")
    public ResponseEntity<?> accessStats(
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String ip) {
        String day = (date == null || date.isEmpty()) ? LocalDate.now().toString() : date;
        Map<String, Object> resp = new HashMap<>();
        resp.put("date", day);
        if (ip != null && !ip.isEmpty()) {
            resp.put("ip", ip);
            resp.put("hours", accessStatsRegistry.hourlyForIp(day, ip));
        } else {
            resp.put("records", accessStatsRegistry.forDate(day));
        }
        return ResponseEntity.ok(resp);
    }

    private TransferDirection parseDirection(String direction) {
        if (direction == null || direction.isEmpty()) {
            return null;
        }
        try {
            return TransferDirection.valueOf(direction.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
