package com.project.taskmanager.repository;

import com.project.taskmanager.entity.RefreshToken;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface RefreshTokenRepository extends MongoRepository<RefreshToken, String> {

    Optional<RefreshToken> findByToken(String token);

    void deleteByToken(String token);

    void deleteByUsername(String username);
}
