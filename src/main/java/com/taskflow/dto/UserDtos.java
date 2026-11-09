package com.taskflow.dto;

import com.taskflow.entity.Role;
import com.taskflow.entity.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class UserDtos {
    private UserDtos() {}

    public record UserDto(Long id, String fullName, String email, Role role, boolean active, Instant createdAt) {
        public static UserDto from(User u) {
            return new UserDto(u.getId(), u.getFullName(), u.getEmail(), u.getRole(), u.isActive(), u.getCreatedAt());
        }
    }

    public record UserSummary(Long id, String fullName, String email) {
        public static UserSummary from(User u) {
            return u == null ? null : new UserSummary(u.getId(), u.getFullName(), u.getEmail());
        }
    }

    /** Actualización administrativa (solo ADMIN). */
    public record AdminUpdateUserRequest(
            @Size(max = 120) String fullName,
            Role role,
            Boolean active) {}

    /** Actualización del propio perfil. */
    public record UpdateProfileRequest(
            @NotBlank(message = "El nombre es obligatorio") @Size(max = 120) String fullName,
            String currentPassword,
            @Size(min = 8, max = 100, message = "La nueva contraseña debe tener entre 8 y 100 caracteres") String newPassword) {}
}
