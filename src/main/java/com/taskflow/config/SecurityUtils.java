package com.taskflow.config;

import com.taskflow.entity.User;
import com.taskflow.exception.ApiException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {
    private SecurityUtils() {}

    /** Usuario autenticado de la petición actual (cargado por JwtAuthenticationFilter). */
    public static User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            throw ApiException.unauthorized("Autenticación requerida");
        }
        return user;
    }
}
