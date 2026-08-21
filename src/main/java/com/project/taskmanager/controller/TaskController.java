package com.project.taskmanager.controller;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import com.project.taskmanager.dto.BulkTaskUpdateDTO;
import com.project.taskmanager.dto.BulkUpdateResultDTO;
import com.project.taskmanager.dto.TaskDTO;
import com.project.taskmanager.dto.TaskResponseDTO;
import com.project.taskmanager.enums.Priority;
import com.project.taskmanager.enums.TaskStatus;
import com.project.taskmanager.mapper.TaskMapper;
import com.project.taskmanager.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/tasks")
@RequiredArgsConstructor
@RestController
public class TaskController {

    private final TaskMapper taskMapper;
    private final TaskService taskService;

    /**
     * All filters are optional. {@code page}, {@code size} and {@code sort} are bound from the
     * {@link Pageable}, so {@code ?sort=dueDate,asc} works unchanged.
     */
    @GetMapping
    public ResponseEntity<Page<TaskResponseDTO>> getAllTasks(
            @AuthenticationPrincipal(expression = "username") final String username,
            @RequestParam(required = false) final TaskStatus status,
            @RequestParam(required = false) final Priority priority, @RequestParam(required = false) final String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate dueBefore,
            @RequestParam(required = false) final String tag, final Pageable pageable) {
        // Page.map preserves the pageable metadata, so the `page` block of the JSON is unchanged.
        return ResponseEntity.ok(taskService.getAllTasks(username, status, priority, q, dueBefore, tag, pageable)
                .map(taskMapper::toResponse));
    }

    /**
     * Tasks due within {@code withinDays} or already overdue, soonest first.
     *
     * <p>Mapped above {@code /{taskId}} deliberately: Spring matches the more specific literal path
     * first regardless of order, but keeping them adjacent makes the collision visible to the next
     * reader who adds a path variable here.
     */
    @GetMapping("/reminders")
    public ResponseEntity<List<TaskResponseDTO>> getDueReminders(
            @AuthenticationPrincipal(expression = "username") final String username,
            @RequestParam(defaultValue = "7") @Min(0) @Max(365) final int withinDays) {
        return ResponseEntity
                .ok(taskService.getDueReminders(username, withinDays).stream().map(taskMapper::toResponse).toList());
    }

    /**
     * Applies status and/or priority across many tasks at once. Ids the caller does not own are
     * skipped rather than rejected; the response says how many were actually updated.
     */
    @PatchMapping
    public ResponseEntity<BulkUpdateResultDTO> bulkUpdate(
            @AuthenticationPrincipal(expression = "username") final String username,
            @Valid @RequestBody final BulkTaskUpdateDTO request) {
        final var updated = taskService.bulkUpdate(username, request.ids(), request.status(), request.priority());
        return ResponseEntity.ok(new BulkUpdateResultDTO(updated));
    }

    @PostMapping
    public ResponseEntity<TaskResponseDTO> createTask(
            @AuthenticationPrincipal(expression = "username") final String username,
            @Valid @RequestBody final TaskDTO taskDTO) throws URISyntaxException {
        final var task = taskMapper.toEntity(taskDTO);
        task.setUsername(username);
        // A client may create a task in any state; PENDING is only the default. This used to
        // overwrite whatever the client sent, so a task could never be created as anything else.
        if (task.getStatus() == null) {
            task.setStatus(TaskStatus.PENDING);
        }
        // Same contract as status: the client may set it, MEDIUM when it doesn't.
        if (task.getPriority() == null) {
            task.setPriority(Priority.MEDIUM);
        }
        final var createdTask = taskService.createTask(task);
        final var location = new URI("/api/tasks/" + createdTask.getId());

        return ResponseEntity.created(location).body(taskMapper.toResponse(createdTask));
    }

    @GetMapping("/{taskId}")
    public ResponseEntity<TaskResponseDTO> getTask(
            @AuthenticationPrincipal(expression = "username") final String username,
            @PathVariable final String taskId) {
        final var task = taskService.getTaskById(username, taskId);
        return ResponseEntity.ok(taskMapper.toResponse(task));
    }

    @PutMapping("/{taskId}")
    public ResponseEntity<TaskResponseDTO> updateTask(
            @AuthenticationPrincipal(expression = "username") final String username, @PathVariable final String taskId,
            @Valid @RequestBody final TaskDTO taskDTO) {
        final var task = taskService.updateTask(username, taskId, taskMapper.toEntity(taskDTO));
        return ResponseEntity.ok(taskMapper.toResponse(task));
    }

    @DeleteMapping("/{taskId}")
    public ResponseEntity<Void> deleteTask(@AuthenticationPrincipal(expression = "username") final String username,
            @PathVariable final String taskId) {
        taskService.deleteTask(username, taskId);
        return ResponseEntity.noContent().build();
    }
}
