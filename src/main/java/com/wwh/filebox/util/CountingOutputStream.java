package com.wwh.filebox.util;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import java.io.IOException;
import java.io.OutputStream;

/**
 * 包住真实输出流、只统计写出字节数(不缓冲,适合大文件下载)。
 * Wraps the real output stream and only counts bytes written (no buffering, safe for large downloads).
 */
public class CountingOutputStream extends ServletOutputStream {

    private final OutputStream delegate;
    private long count = 0;

    public CountingOutputStream(OutputStream delegate) {
        this.delegate = delegate;
    }

    @Override
    public void write(int b) throws IOException {
        delegate.write(b);
        count++;
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        delegate.write(b, off, len);
        count += len;
    }

    @Override
    public void flush() throws IOException {
        delegate.flush();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    public long getCount() {
        return count;
    }

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public void setWriteListener(WriteListener writeListener) {
        // 同步写出,不需要 write listener / synchronous writes; listener not used
    }
}
