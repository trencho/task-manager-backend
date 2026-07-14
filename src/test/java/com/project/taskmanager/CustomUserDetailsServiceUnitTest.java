package com.project.taskmanager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.project.taskmanager.entity.User;
import com.project.taskmanager.repository.UserRepository;
import com.project.taskmanager.security.CustomUserDetailsService;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceUnitTest {

    private static final String USERNAME = "username";

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void shouldLoadAnExistingUser() {
        final var user = new User();
        user.setUsername(USERNAME);
        user.setPassword("hashed");
        user.setRoles(Set.of("USER"));
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));

        final var details = customUserDetailsService.loadUserByUsername(USERNAME);

        assertThat(details.getUsername()).isEqualTo(USERNAME);
        assertThat(details.getPassword()).isEqualTo("hashed");
    }

    /**
     * Spring Security relies on this exception to produce a generic authentication failure. It
     * must not be swallowed into a null UserDetails, which would NPE deep inside the provider.
     */
    @Test
    void shouldThrowWhenTheUserDoesNotExist() {
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());

        final var thrown = assertThrows(
                UsernameNotFoundException.class, () -> customUserDetailsService.loadUserByUsername(USERNAME));

        assertThat(thrown.getMessage()).contains(USERNAME);
    }
}
