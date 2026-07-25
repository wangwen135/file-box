package com.wwh.filebox.util;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class CountingOutputStreamTest {

    @Test
    void countsAllBytesWrittenAndDelegates() throws IOException {
        ByteArrayOutputStream target = new ByteArrayOutputStream();
        CountingOutputStream cos = new CountingOutputStream(target);

        cos.write("hello".getBytes(StandardCharsets.UTF_8));
        cos.write(65); // 'A'
        byte[] b = ", world".getBytes(StandardCharsets.UTF_8);
        cos.write(b, 0, b.length);
        cos.flush();

        int expected = "hello".length() + 1 + ", world".length();
        assertThat(cos.getCount()).isEqualTo(expected);
        assertThat(target.toByteArray()).isEqualTo("helloA, world".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void startsAtZero() {
        assertThat(new CountingOutputStream(new ByteArrayOutputStream()).getCount()).isZero();
    }
}
