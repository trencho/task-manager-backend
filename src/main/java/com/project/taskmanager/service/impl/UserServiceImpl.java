package com.project.taskmanager.service.impl;

import com.project.taskmanager.entity.User;
import com.project.taskmanager.repository.UserRepository;
import com.project.taskmanager.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class UserServiceImpl implements UserService {

    private static final String USERNAME_TAKEN = "User with the same username already exists";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void registerUser(final User user) {
        final var existingUser = userRepository.findByUsername(user.getUsername());
        if (existingUser.isPresent()) {
            throw new IllegalArgumentException(USERNAME_TAKEN);
        }

        user.setPassword(passwordEncoder.encode(user.getPassword()));
        try {
            userRepository.save(user);
        } catch (DuplicateKeyException e) {
            // The lookup above is a courtesy, not a guarantee: check-then-save is a race, and two
            // concurrent signups for one username both read "absent" and both proceed. The unique
            // index on users.username (MongoIndexInitializer) is what settles it, and this is the
            // loser of that race being told the same thing the early check would have told it.
            throw new IllegalArgumentException(USERNAME_TAKEN, e);
        }
    }
}
