package com.project.taskmanager.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserRegistrationDTO(
        @NotBlank(message = "Username is required")
                @Size(min = 3, max = 30, message = "Username must be between 3 and 30 characters")
                String username,
        @NotBlank(message = "Email is required") @Email(message = "Email should be valid") String email,
        @NotBlank(message = "Password is required")
                @Size(min = 8, message = "Password must be at least 8 characters long")
                String password) {}
