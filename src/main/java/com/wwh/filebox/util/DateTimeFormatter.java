package com.wwh.filebox.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * 线程安全的日期格式化工具类
 * 使用ThreadLocal确保每个线程有自己的SimpleDateFormat实例
 */
public class DateTimeFormatter {

    /** 年份格式化器 */
    private static final ThreadLocal<SimpleDateFormat> YEAR_FORMATTER =
        ThreadLocal.withInitial(() -> {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy");
            sdf.setTimeZone(TimeZone.getDefault());
            return sdf;
        });

    /** 月份格式化器 */
    private static final ThreadLocal<SimpleDateFormat> MONTH_FORMATTER =
        ThreadLocal.withInitial(() -> {
            SimpleDateFormat sdf = new SimpleDateFormat("MM");
            sdf.setTimeZone(TimeZone.getDefault());
            return sdf;
        });

    /** 时间戳格式化器 */
    private static final ThreadLocal<SimpleDateFormat> TIMESTAMP_FORMATTER =
        ThreadLocal.withInitial(() -> {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmssSSS");
            sdf.setTimeZone(TimeZone.getDefault());
            return sdf;
        });

    /** 文件名时间格式化器 */
    private static final ThreadLocal<SimpleDateFormat> FILETIME_FORMATTER =
        ThreadLocal.withInitial(() -> {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            sdf.setTimeZone(TimeZone.getDefault());
            return sdf;
        });

    /**
     * 格式化年份
     * @param date 日期
     * @return 年份字符串
     */
    public static String formatYear(Date date) {
        if (date == null) {
            return YEAR_FORMATTER.get().format(new Date());
        }
        return YEAR_FORMATTER.get().format(date);
    }

    /**
     * 格式化月份
     * @param date 日期
     * @return 月份字符串
     */
    public static String formatMonth(Date date) {
        if (date == null) {
            return MONTH_FORMATTER.get().format(new Date());
        }
        return MONTH_FORMATTER.get().format(date);
    }

    /**
     * 格式化时间戳
     * @param date 日期
     * @return 时间戳字符串
     */
    public static String formatTimestamp(Date date) {
        if (date == null) {
            return TIMESTAMP_FORMATTER.get().format(new Date());
        }
        return TIMESTAMP_FORMATTER.get().format(date);
    }

    /**
     * 格式化文件时间
     * @param date 日期
     * @return 文件时间字符串
     */
    public static String formatFileTime(Date date) {
        if (date == null) {
            return FILETIME_FORMATTER.get().format(new Date());
        }
        return FILETIME_FORMATTER.get().format(date);
    }

    /**
     * 获取当前时间戳
     * @return 时间戳字符串
     */
    public static String getCurrentTimestamp() {
        return formatTimestamp(null);
    }

}
