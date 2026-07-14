package com.project.taskmanager;

import static org.assertj.core.api.Assertions.assertThat;

import com.project.taskmanager.entity.User;
import com.project.taskmanager.security.CustomUserDetails;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CustomUserDetailsUnitTest {

    private static User user(final Set<String> roles) {
        final var user = new User();
        user.setUsername("username");
        user.setPassword("hashed-password");
        user.setRoles(roles);
        return user;
    }

    @Test
    void shouldMapEveryRoleToAGrantedAuthority() {
        final var details = new CustomUserDetails(user(Set.of("USER", "ADMIN")));

        assertThat(details.getAuthorities()).extracting(Object::toString).containsExactlyInAnyOrder("USER", "ADMIN");
    }

    @Test
    void shouldExposeNoAuthoritiesForAUserWithoutRoles() {
        assertThat(new CustomUserDetails(user(Set.of())).getAuthorities()).isEmpty();
    }

    @Test
    void shouldDelegateCredentialsToTheUser() {
        final var details = new CustomUserDetails(user(Set.of("USER")));

        assertThat(details.getUsername()).isEqualTo("username");
        assertThat(details.getPassword()).isEqualTo("hashed-password");
    }

    /**
     * The four account-state flags are hard-coded true. Pinning them makes it a deliberate,
     * visible act to ever introduce account locking or expiry.
     */
    @Test
    void shouldTreatEveryAccountAsActive() {
        final var details = new CustomUserDetails(user(Set.of("USER")));

        assertThat(details.isAccountNonExpired()).isTrue();
        assertThat(details.isAccountNonLocked()).isTrue();
        assertThat(details.isCredentialsNonExpired()).isTrue();
        assertThat(details.isEnabled()).isTrue();
    }
}
