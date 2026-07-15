package com.project.taskmanager.repository;

import java.time.LocalDate;

import com.project.taskmanager.entity.Task;
import com.project.taskmanager.enums.Priority;
import com.project.taskmanager.enums.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Derived query methods cannot compose optional filters — you would need one method per
 * combination. This fragment builds the query with {@code Criteria} instead.
 */
public interface TaskRepositoryCustom {

    /**
     * Every filter except {@code username} is optional; a null means "do not constrain on this".
     * Results are always scoped to {@code username}.
     *
     * @param q         matched case-insensitively against title OR description
     * @param dueBefore exclusive upper bound on {@code dueDate}
     */
    Page<Task> search(String username, TaskStatus status, Priority priority, String q, LocalDate dueBefore,
            Pageable pageable);
}
