package com.project.taskmanager.repository;

import com.project.taskmanager.entity.User;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserRepository extends MongoRepository<User, String> {

    Optional<User> findByUsername(String username);
}
