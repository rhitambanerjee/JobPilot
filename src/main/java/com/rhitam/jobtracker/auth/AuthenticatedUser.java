package com.rhitam.jobtracker.auth;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The logged-in user as Spring Security sees it, and what controllers receive via @AuthenticationPrincipal.
 * The copy kept in the session never contains the password hash (see withoutPassword()).
 */
public record AuthenticatedUser(UUID id, String email, String passwordHash, String displayName) implements UserDetails {

    public AuthenticatedUser withoutPassword() {
        return new AuthenticatedUser(id, email, "", displayName);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }
}
