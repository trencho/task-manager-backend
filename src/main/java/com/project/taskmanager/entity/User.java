package com.project.taskmanager.entity;

import java.util.HashSet;
import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    private String id;

    // Indexed, uniquely, by MongoIndexInitializer. It carried an @Indexed annotation here, which
    // created nothing: auto-index-creation is off by default and this repository never turns it on.
    // Do not add one back — an annotation that reads as a constraint and enforces nothing is worse
    // than a bare field.
    private String username;

    private String email;

    private String password;

    @Builder.Default
    private Set<String> roles = new HashSet<>();
}
