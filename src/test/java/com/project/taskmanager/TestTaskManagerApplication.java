package com.project.taskmanager;

import org.springframework.boot.SpringApplication;

public class TestTaskManagerApplication {

    public static void main(final String[] args) {
        SpringApplication.from(TaskManagerApplication::main).run(args);
    }
}
