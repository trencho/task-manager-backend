package com.project.taskmanager.service;

import com.project.taskmanager.entity.Task;
import com.project.taskmanager.enums.Priority;
import com.project.taskmanager.enums.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

public interface TaskService {

    /**
     * Every filter is optional; null means "do not constrain". Always scoped to {@code username}.
     */
    Page<Task> getAllTasks(String username, TaskStatus status, Priority priority, String q, LocalDate dueBefore,
                           Pageable pageable);

    Task createTask(Task task);

    Task getTaskById(String username, String id);

    Task updateTask(String username, String id, Task task);

    void deleteTask(String username, String id);

}
