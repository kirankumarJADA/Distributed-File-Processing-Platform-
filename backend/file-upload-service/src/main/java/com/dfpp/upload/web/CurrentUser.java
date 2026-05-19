package com.dfpp.upload.web;

import com.dfpp.common.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUser {

    private CurrentUser() {
    }

    public static AuthenticatedUser require() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthenticatedUser u)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return u;
    }
}
