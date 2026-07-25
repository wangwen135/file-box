package com.wwh.filebox.service;

import com.wwh.filebox.model.ActiveTransfer;
import com.wwh.filebox.model.TransferDirection;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ActiveTransferRegistryTest {

    @Test
    void startAddsEntryAndEndRemovesIt() {
        ActiveTransferRegistry registry = new ActiveTransferRegistry();
        assertThat(registry.snapshot()).isEmpty();
        assertThat(registry.isEmpty()).isTrue();

        long id = registry.start(TransferDirection.UPLOAD, "admin", "默认", "dir/a.txt");
        List<ActiveTransfer> snap = registry.snapshot();

        assertThat(snap).hasSize(1);
        ActiveTransfer only = snap.get(0);
        assertThat(only.getId()).isEqualTo(id);
        assertThat(only.getDirection()).isEqualTo(TransferDirection.UPLOAD);
        assertThat(only.getUser()).isEqualTo("admin");
        assertThat(only.getSpace()).isEqualTo("默认");
        assertThat(only.getFile()).isEqualTo("dir/a.txt");
        assertThat(only.getStartedAtMillis()).isGreaterThan(0L);
        assertThat(registry.isEmpty()).isFalse();

        registry.end(id);
        assertThat(registry.snapshot()).isEmpty();
        assertThat(registry.isEmpty()).isTrue();
    }

    @Test
    void assignsDistinctIdsAndListsAllConcurrentTransfers() {
        ActiveTransferRegistry registry = new ActiveTransferRegistry();
        long a = registry.start(TransferDirection.UPLOAD, "admin", "s1", "a.txt");
        long b = registry.start(TransferDirection.DOWNLOAD, "alice", "s2", "b.txt");

        assertThat(a).isNotEqualTo(b);
        assertThat(registry.snapshot()).extracting(ActiveTransfer::getUser)
                .containsExactlyInAnyOrder("admin", "alice");
    }

    @Test
    void snapshotIsADefensiveCopy() {
        ActiveTransferRegistry registry = new ActiveTransferRegistry();
        registry.start(TransferDirection.UPLOAD, "admin", "s1", "a.txt");

        List<ActiveTransfer> first = registry.snapshot();
        first.clear(); // 不应影响注册表本身 / must not mutate the registry's internal state

        assertThat(registry.snapshot()).hasSize(1);
    }

    @Test
    void notifiesChangeListenersOnStartAndEnd() {
        ActiveTransferRegistry registry = new ActiveTransferRegistry();
        AtomicInteger calls = new AtomicInteger();
        registry.addChangeListener(calls::incrementAndGet);

        long id = registry.start(TransferDirection.UPLOAD, "admin", "s1", "a.txt");
        assertThat(calls.get()).isEqualTo(1); // 开始即通知一次 / notified once on start

        registry.end(id);
        assertThat(calls.get()).isEqualTo(2); // 结束再通知一次 / notified again on end
    }
}
