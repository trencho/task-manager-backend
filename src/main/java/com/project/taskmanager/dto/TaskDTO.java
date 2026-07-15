package com.project.taskmanager.dto;

import java.time.LocalDate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.project.taskmanager.enums.Priority;
import com.project.taskmanager.enums.TaskStatus;

public record TaskDTO(
        @NotBlank(message = "Title is required") @Size(min = 3, max = 50, message = "Title must be between 3 and 50 characters") String title,
        @Size(max = 200, message = "Description must be less than 200 characters") String description,
        LocalDate dueDate, TaskStatus status, Priority priority) {
}
