package com.rhitam.jobtracker.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Kills sessions that were issued before the user's last password reset. A session remembers the user's
 * sessionVersion when it is created; a reset bumps the number in the database, so every older session stops
 * matching and the request is treated as anonymous (401). One indexed lookup per API request.
 */
final class SessionVersionFilter extends OncePerRequestFilter {

    private final UserRepository users;

    SessionVersionFilter(UserRepository users) {
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser principal) {
            Integer current = users.findSessionVersion(principal.id()).orElse(null);
            if (current == null || current != principal.sessionVersion()) {
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
