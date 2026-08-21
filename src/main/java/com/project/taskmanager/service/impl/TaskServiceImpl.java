package com.project.taskmanager.service.impl;

import java.time.LocalDate;
import java.util.List;

import com.project.taskmanager.entity.Task;
import com.project.taskmanager.enums.Priority;
import com.project.taskmanager.enums.TaskStatus;
import com.project.taskmanager.exception.TaskNotFoundException;
import com.project.taskmanager.repository.TaskRepository;
import com.project.taskmanager.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class TaskServiceImpl implements TaskService {

    private static final String TASK_NOT_FOUND_WITH_ID = "Task not found with id: ";
    private static final String TASK_NOT_FOUND_FOR_USER = "Task not found for user: ";

    private final TaskRepository taskRepository;

    @Override
    public Page<Task> getAllTasks(final String username, final TaskStatus status, final Priority priority,
            final String q, final LocalDate dueBefore, final String tag, final Pageable pageable) {
        return taskRepository.search(username, status, priority, q, dueBefore, tag, pageable);
    }

    @Override
    public Task createTask(final Task task) {
        return taskRepository.save(task);
    }

    @Override
    public Task getTaskById(final String username, final String id) {
        final var task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(TASK_NOT_FOUND_WITH_ID + id));
        if (task.getUsername().equals(username)) {
            return task;
        }
        throw new TaskNotFoundException(TASK_NOT_FOUND_FOR_USER + username);
    }

    @Override
    public Task updateTask(final String username, final String id, final Task task) {
        final var existingTask = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(TASK_NOT_FOUND_WITH_ID + id));

        if (existingTask.getUsername().equals(username)) {
            existingTask.setTitle(task.getTitle());
            existingTask.setDescription(task.getDescription());
            existingTask.setDueDate(task.getDueDate());
            // An update that omits `status` leaves it alone. Copying unconditionally nulled the
            // field out for any client that sent only the editable text fields.
            if (task.getStatus() != null) {
                existingTask.setStatus(task.getStatus());
            }
            if (task.getPriority() != null) {
                existingTask.setPriority(task.getPriority());
            }
            // Same omit-means-leave-alone contract as status and priority. An empty set is NOT the
            // same as an absent one -- it is how a client clears every tag, so only null skips.
            if (task.getTags() != null) {
                existingTask.setTags(task.getTags());
            }

            return taskRepository.save(existingTask);
        }
        throw new TaskNotFoundException(TASK_NOT_FOUND_FOR_USER + username);
    }

    @Override
    public List<Task> getDueReminders(final String username, final int withinDays) {
        return taskRepository.findDueReminders(username, LocalDate.now().plusDays(withinDays));
    }

    @Override
    public int bulkUpdate(final String username, final List<String> ids, final TaskStatus status,
            final Priority priority) {
        if (status == null && priority == null) {
            // Nothing to apply. Returning 0 rather than saving every task unchanged keeps the count
            // honest -- "updated 12 tasks" for a no-op request is a lie the caller would act on.
            return 0;
        }

        // findAllById skips ids that do not exist; the ownership filter skips other people's tasks.
        // Both are silent by design (see the interface), and the returned count is how the caller
        // learns its batch was partial.
        final var owned = taskRepository.findAllById(ids).stream().filter(task -> username.equals(task.getUsername()))
                .toList();
        for (final var task : owned) {
            if (status != null) {
                task.setStatus(status);
            }
            if (priority != null) {
                task.setPriority(priority);
            }
        }
        taskRepository.saveAll(owned);
        return owned.size();
    }

    @Override
    public void deleteTask(final String username, final String id) {
        final var existingTask = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(TASK_NOT_FOUND_WITH_ID + id));
        if (existingTask.getUsername().equals(username)) {
            taskRepository.deleteById(id);
            return;
        }
        throw new TaskNotFoundException(TASK_NOT_FOUND_FOR_USER + username);
    }
}
