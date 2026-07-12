package com.wwh.filebox.controller;

import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 自定义错误控制器
 */
@Controller
public class CustomErrorController implements ErrorController {

    /**
     * 处理所有错误请求
     * 当用户访问不存在的路径时，重定向到index页面
     */
    @RequestMapping("/error")
    public String handleError() {
        return "redirect:/index.html";
    }

    /**
     * 确保Spring Boot知道这是错误处理器
     */
    @RequestMapping("/error/**")
    public String handleNestedError() {
        return "redirect:/index.html";
    }

    /**
     * Spring Boot 2.3 的 ErrorController 仍要求实现该方法。
     */
    @Override
    public String getErrorPath() {
        return "/error";
    }
}
