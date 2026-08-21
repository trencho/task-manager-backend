package com.project.taskmanager.repository;

import java.time.LocalDate;
import java.util.List;

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
     * @param tag       exact tag the task must carry
     */
    Page<Task> search(String username, TaskStatus status, Priority priority, String q, LocalDate dueBefore, String tag,
            Pageable pageable);

    /**
     * Tasks whose deadline is approaching or already passed — the reminder list.
     *
     * <p>Excludes COMPLETED: a finished task is not a reminder, and including it would bury the ones
     * that still need doing. Tasks with no {@code dueDate} are excluded too — there is nothing to be
     * late for.
     *
     * @param dueOnOrBefore inclusive upper bound; callers pass today + the lead time, so overdue tasks
     *                      (already before today) fall inside the window rather than dropping out of it
     */
    List<Task> findDueReminders(String username, LocalDate dueOnOrBefore);
}
