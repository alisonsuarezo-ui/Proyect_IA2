package com.taskflow.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank(message = "El nombre es obligatorio") @Size(max = 120) String fullName,
            @NotBlank(message = "El correo es obligatorio") @Email(message = "Correo no válido") @Size(max = 160) String email,
            @NotBlank(message = "La contraseña es obligatoria")
            @Size(min = 8, max = 100, message = "La contraseña debe tener entre 8 y 100 caracteres") String password) {}

    public record LoginRequest(
            @NotBlank(message = "El correo es obligatorio") @Email String email,
            @NotBlank(message = "La contraseña es obligatoria") String password) {}

    public record AuthResponse(String token, UserDtos.UserDto user) {}
}
