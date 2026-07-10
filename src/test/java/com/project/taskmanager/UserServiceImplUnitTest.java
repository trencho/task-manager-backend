package com.project.taskmanager;

import com.project.taskmanager.entity.User;
import com.project.taskmanager.repository.UserRepository;
import com.project.taskmanager.service.impl.UserServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplUnitTest {

    private static final String USERNAME = "username";
    private static final String RAW_PASSWORD = "raw-password";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl userService;

    private static User newUser() {
        return User.builder()
                .username(USERNAME)
                .email("user@mail.com")
                .password(RAW_PASSWORD)
                .build();
    }

    /**
     * The raw password must never reach the repository. This is the assertion that would
     * catch a regression where the encoder call is dropped or reordered after the save.
     */
    @Test
    void shouldHashThePasswordBeforeSaving() {
        final var user = newUser();
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn("hashed");

        userService.registerUser(user);

        final var saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getPassword()).isEqualTo("hashed");
        assertThat(saved.getValue().getPassword()).isNotEqualTo(RAW_PASSWORD);
    }

    @Test
    void shouldRejectDuplicateUsername() {
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(newUser()));

        final var thrown = assertThrows(IllegalArgumentException.class,
                () -> userService.registerUser(newUser()));

        assertThat(thrown.getMessage()).contains("already exists");
        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }
}
