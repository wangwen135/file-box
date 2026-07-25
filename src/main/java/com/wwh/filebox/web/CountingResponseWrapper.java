package com.wwh.filebox.web;

import com.wwh.filebox.util.CountingOutputStream;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;

/**
 * 包住响应,懒创建一个"数字节"的输出流,用于统计本次响应发出的字节数。
 * Wraps the response, lazily substituting a byte-counting output stream to measure bytes served.
 */
public class CountingResponseWrapper extends HttpServletResponseWrapper {

    private CountingOutputStream countingStream;

    public CountingResponseWrapper(HttpServletResponse response) {
        super(response);
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        if (countingStream == null) {
            countingStream = new CountingOutputStream(super.getOutputStream());
        }
        return countingStream;
    }

    public long getByteCount() {
        return countingStream == null ? 0L : countingStream.getCount();
    }
}
