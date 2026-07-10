package com.project.taskmanager.service.impl;

import com.project.taskmanager.entity.Task;
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
    public Page<Task> getAllTasks(final String username, final Pageable pageable) {
        return taskRepository.findByUsername(username, pageable);
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

            return taskRepository.save(existingTask);
        }
        throw new TaskNotFoundException(TASK_NOT_FOUND_FOR_USER + username);
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
