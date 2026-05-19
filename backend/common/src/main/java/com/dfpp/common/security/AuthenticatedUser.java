package com.dfpp.common.security;

import java.util.List;

/** Immutable principal reconstructed from a verified JWT. */
public record AuthenticatedUser(long userId, String username, List<String> roles) {

    public boolean hasRole(String role) {
        return roles.contains(role) || roles.contains("ROLE_" + role);
    }
}
