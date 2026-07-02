package com.wwh.filebox.config;

import com.wwh.filebox.security.AuthInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web configuration
 * Web配置类
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private AuthInterceptor authInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Register authentication interceptor
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**") // Intercept all paths
                .excludePathPatterns("/favicon.ico", "/favicon.png") // Exclude favicon
                .excludePathPatterns("/images/**") // Exclude image resources
                .excludePathPatterns("/css/**") // Exclude CSS resources
                .excludePathPatterns("/js/**") // Exclude JavaScript resources
                .excludePathPatterns("/login", "/login.html", "/", "/index.html") // Exclude login and home pages
                .excludePathPatterns("/admin/**"); // Exclude admin pages
    }
}
