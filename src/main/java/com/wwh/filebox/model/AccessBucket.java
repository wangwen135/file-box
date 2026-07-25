package com.wwh.filebox.model;

/** 访问请求的分类桶(用于访问统计的"在打哪些接口"分布)。/ Request bucket for access stats. */
public enum AccessBucket {
    /** 列表/目录浏览(/list_files、/list_dir、/list_periods)——遍历文件的信号 / list/browse */
    LIST,
    /** 下载/取文件(/api/file) / download / serve file */
    DOWNLOAD,
    /** 其它(上传、登录、页面等均归此类) / everything else */
    OTHER
}
