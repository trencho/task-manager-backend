package com.project.taskmanager.dto;

import com.project.taskmanager.enums.Priority;
import com.project.taskmanager.enums.TaskStatus;
import java.time.LocalDate;

/**
 * The read side of a task. Carries {@code id} — the client edits and deletes by it — and
 * deliberately has no {@code username}: a caller can only ever see its own tasks, so the owner is
 * redundant, and omitting the field makes leaking it structurally impossible.
 * <p>
 * {@link TaskDTO} remains the write side, where the bean-validation constraints live.
 */
public record TaskResponseDTO(
        String id, String title, String description, LocalDate dueDate, TaskStatus status, Priority priority) {}
