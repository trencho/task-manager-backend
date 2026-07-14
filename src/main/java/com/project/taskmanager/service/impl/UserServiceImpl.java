package com.project.taskmanager.service.impl;

import com.project.taskmanager.entity.User;
import com.project.taskmanager.repository.UserRepository;
import com.project.taskmanager.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void registerUser(final User user) {
        final var existingUser = userRepository.findByUsername(user.getUsername());
        if (existingUser.isPresent()) {
            throw new IllegalArgumentException("User with the same username already exists");
        }

        user.setPassword(passwordEncoder.encode(user.getPassword()));
        userRepository.save(user);
    }
}
