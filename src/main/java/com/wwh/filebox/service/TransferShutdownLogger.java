package com.wwh.filebox.service;

import com.wwh.filebox.model.ActiveTransfer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.List;

/**
 * 关停时若仍有活跃传输,把它们记进 logs/filebox.log(不阻塞关停)。
 * On shutdown, log any still-active transfers to logs/filebox.log (does not block shutdown).
 */
@Component
public class TransferShutdownLogger {

    private static final Logger logger = LoggerFactory.getLogger(TransferShutdownLogger.class);

    @Autowired
    private ActiveTransferRegistry registry;

    @PreDestroy
    void onShutdown() {
        List<ActiveTransfer> active = registry.snapshot();
        if (active.isEmpty()) {
            return;
        }
        logger.warn("关停时仍有 {} 个活跃传输将被中断 / {} active transfer(s) interrupted at shutdown:",
                active.size(), active.size());
        for (ActiveTransfer t : active) {
            logger.warn("  {} user={} space={} file={} / direction={} user={} space={} file={}",
                    t.getDirection(), t.getUser(), t.getSpace(), t.getFile(),
                    t.getDirection(), t.getUser(), t.getSpace(), t.getFile());
        }
    }
}
