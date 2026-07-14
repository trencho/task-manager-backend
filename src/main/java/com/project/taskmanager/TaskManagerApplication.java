package com.project.taskmanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.web.config.EnableSpringDataWebSupport;

@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
@SpringBootApplication
public class TaskManagerApplication {

    public static void main(final String[] args) {
        SpringApplication.run(TaskManagerApplication.class, args);
    }
}
