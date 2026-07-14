package com.project.taskmanager.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final String UNAUTHORIZED_MESSAGE = "Unauthorized: You need to provide a valid JWT token.";

    @Override
    public void commence(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final AuthenticationException authException)
            throws IOException {
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, UNAUTHORIZED_MESSAGE);
    }
}
