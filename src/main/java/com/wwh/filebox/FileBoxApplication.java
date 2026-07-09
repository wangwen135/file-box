package com.wwh.filebox;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan("com.wwh.filebox.config")
public class FileBoxApplication {
    public static void main(String[] args) {
        if (MaintenanceCommand.runIfRequested(args)) {
            return;
        }
        SpringApplication.run(FileBoxApplication.class, args);
    }
}
