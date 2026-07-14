package com.project.taskmanager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.project.taskmanager.entity.Task;
import com.project.taskmanager.enums.Priority;
import com.project.taskmanager.enums.TaskStatus;
import com.project.taskmanager.exception.TaskNotFoundException;
import com.project.taskmanager.repository.TaskRepository;
import com.project.taskmanager.service.impl.TaskServiceImpl;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class TaskServiceImplUnitTest {

    private static final String USERNAME = "username";

    @Mock
    private TaskRepository taskRepository;

    @InjectMocks
    private TaskServiceImpl taskService;

    @Test
    void testGetAllTasks() {
        final var tasks = List.of(
                new Task("Task 1", "Task 1 description", LocalDate.now(), TaskStatus.PENDING, USERNAME),
                new Task("Task 2", "Task 2 description", LocalDate.now(), TaskStatus.PENDING, USERNAME));
        final var pageable = PageRequest.of(0, 5);
        final var tasksPage = new PageImpl<>(tasks, pageable, 2);

        // `findByUsername` is gone: an unfiltered listing is search(...) with every filter null.
        when(taskRepository.search(USERNAME, null, null, null, null, pageable)).thenReturn(tasksPage);

        final var result = taskService.getAllTasks(USERNAME, null, null, null, null, pageable);
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(2);
        verify(taskRepository, times(1)).search(USERNAME, null, null, null, null, pageable);
    }

    @Test
    void shouldPassEveryFilterThroughToTheRepository() {
        final var pageable = PageRequest.of(0, 5);
        final var dueBefore = LocalDate.now().plusDays(7);
        when(taskRepository.search(USERNAME, TaskStatus.IN_PROGRESS, Priority.HIGH, "report", dueBefore, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        taskService.getAllTasks(USERNAME, TaskStatus.IN_PROGRESS, Priority.HIGH, "report", dueBefore, pageable);

        verify(taskRepository).search(USERNAME, TaskStatus.IN_PROGRESS, Priority.HIGH, "report", dueBefore, pageable);
    }

    @Test
    void testCreateTask() {
        final var task = new Task("Test Task", "Test Description", LocalDate.now(), TaskStatus.PENDING, USERNAME);

        when(taskRepository.save(task)).thenReturn(task);

        final var createdTask = taskService.createTask(task);
        assertNotNull(createdTask);
        assertEquals("Test Task", createdTask.getTitle());

        verify(taskRepository, times(1)).save(task);
    }

    @Test
    void testGetTaskByIdSuccessful() {
        final var task = new Task("Test Task", "Test Description", LocalDate.now(), TaskStatus.PENDING, USERNAME);
        when(taskRepository.findById("1")).thenReturn(Optional.of(task));

        final var foundTask = taskService.getTaskById(USERNAME, "1");
        assertNotNull(foundTask);
        assertEquals("Test Task", foundTask.getTitle());

        verify(taskRepository, times(1)).findById("1");
    }

    @Test
    void testGetTaskByIdFailed() {
        when(taskRepository.findById("1")).thenReturn(Optional.empty());

        assertThrows(TaskNotFoundException.class, () -> taskService.getTaskById(USERNAME, "1"));

        verify(taskRepository, times(1)).findById("1");
    }

    @Test
    void testUpdateTaskSuccessful() {
        final var taskId = "1";
        final var existingTask =
                new Task("Old Title", "Old Description", LocalDate.now(), TaskStatus.PENDING, USERNAME);
        final var updatedTask =
                new Task("New Title", "New Description", LocalDate.now(), TaskStatus.COMPLETED, USERNAME);

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(existingTask));
        when(taskRepository.save(existingTask)).thenReturn(existingTask);

        final var result = taskService.updateTask(USERNAME, taskId, updatedTask);

        assertNotNull(result);
        assertEquals("New Title", result.getTitle());
        assertEquals("New Description", result.getDescription());
        assertSame(TaskStatus.COMPLETED, result.getStatus());

        verify(taskRepository, times(1)).save(existingTask);
    }

    @Test
    void testUpdateTaskFailed() {
        final var taskId = "1";
        final var updatedTask =
                new Task("New Title", "New Description", LocalDate.now(), TaskStatus.COMPLETED, USERNAME);

        when(taskRepository.findById(taskId)).thenReturn(Optional.empty());

        assertThrows(TaskNotFoundException.class, () -> taskService.updateTask(USERNAME, taskId, updatedTask));

        verify(taskRepository, never()).save(any());
    }

    @Test
    void testDeleteTask() {
        final var task = new Task("Test Task", "Test Description", LocalDate.now(), TaskStatus.PENDING, USERNAME);
        when(taskRepository.findById("1")).thenReturn(Optional.of(task));
        doNothing().when(taskRepository).deleteById("1");

        taskService.deleteTask(USERNAME, "1");

        verify(taskRepository, times(1)).deleteById("1");
    }
}
