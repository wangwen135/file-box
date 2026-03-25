package com.wwh.filebox.model;

import java.util.HashMap;
import java.util.Map;

/**
 * 统一的API响应类
 * 用于规范所有API接口的返回格式
 */
public class ApiResponse {

    private boolean success;
    private String message;
    private Object data;
    private String error;

    /**
     * 创建成功响应（无数据）
     */
    public static ApiResponse ok() {
        return ok(null, null);
    }

    /**
     * 创建成功响应（带数据）
     */
    public static ApiResponse ok(Object data) {
        return ok(null, data);
    }

    /**
     * 创建成功响应（带消息和数据）
     */
    public static ApiResponse ok(String message, Object data) {
        ApiResponse response = new ApiResponse();
        response.success = true;
        response.message = message;
        response.data = data;
        return response;
    }

    /**
     * 创建错误响应
     */
    public static ApiResponse error(String error) {
        return error(null, error);
    }

    /**
     * 创建错误响应（带消息）
     */
    public static ApiResponse error(String message, String error) {
        ApiResponse response = new ApiResponse();
        response.success = false;
        response.message = message;
        response.error = error;
        return response;
    }

    /**
     * 转换为Map格式（用于现有的JSON序列化）
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("success", success);
        if (message != null) {
            map.put("message", message);
        }
        if (data != null) {
            map.put("data", data);
        }
        if (error != null) {
            map.put("error", error);
        }
        return map;
    }

    // Getters and Setters
    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
