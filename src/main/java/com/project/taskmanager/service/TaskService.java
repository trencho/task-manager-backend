package com.project.taskmanager.service;

import java.time.LocalDate;
import java.util.List;

import com.project.taskmanager.entity.Task;
import com.project.taskmanager.enums.Priority;
import com.project.taskmanager.enums.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TaskService {

    /**
     * Every filter is optional; null means "do not constrain". Always scoped to {@code username}.
     */
    Page<Task> getAllTasks(String username, TaskStatus status, Priority priority, String q, LocalDate dueBefore,
            String tag, Pageable pageable);

    Task createTask(Task task);

    Task getTaskById(String username, String id);

    Task updateTask(String username, String id, Task task);

    /**
     * Tasks that are due within {@code withinDays} or already overdue, soonest first. Owner-scoped.
     */
    List<Task> getDueReminders(String username, int withinDays);

    /**
     * Applies {@code status} and/or {@code priority} to many tasks at once, owner-scoped.
     *
     * <p>Silently skips ids the caller does not own or that do not exist, and returns how many rows
     * it actually changed. A bulk call that failed the whole batch on one stale id would be unusable
     * from a list UI, where a concurrent delete elsewhere is ordinary; the count is how the caller
     * learns the batch was partial.
     *
     * @return the number of tasks updated
     */
    int bulkUpdate(String username, List<String> ids, TaskStatus status, Priority priority);

    void deleteTask(String username, String id);
}
