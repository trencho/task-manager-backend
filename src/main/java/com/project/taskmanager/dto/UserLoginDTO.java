package com.project.taskmanager.dto;

import jakarta.validation.constraints.NotBlank;

public record UserLoginDTO(
        @NotBlank(message = "Username is mandatory") String username,
        @NotBlank(message = "Password is mandatory") String password) {}
