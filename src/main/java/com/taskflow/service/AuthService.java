package com.taskflow.service;

import com.taskflow.config.JwtService;
import com.taskflow.dto.AuthDtos.AuthResponse;
import com.taskflow.dto.AuthDtos.LoginRequest;
import com.taskflow.dto.AuthDtos.RegisterRequest;
import com.taskflow.dto.UserDtos.UserDto;
import com.taskflow.entity.Role;
import com.taskflow.entity.User;
import com.taskflow.exception.ApiException;
import com.taskflow.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /** Registra un usuario. El primer usuario del sistema se convierte en ADMIN. */
    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("Ya existe una cuenta con ese correo");
        }
        User user = new User();
        user.setFullName(req.fullName().trim());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(req.password()));
        user.setRole(userRepository.count() == 0 ? Role.ADMIN : Role.USER);
        userRepository.save(user);
        return new AuthResponse(jwtService.generateToken(user), UserDto.from(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        User user = userRepository.findByEmailIgnoreCase(req.email().trim())
                .filter(u -> passwordEncoder.matches(req.password(), u.getPassword()))
                .orElseThrow(() -> ApiException.unauthorized("Correo o contraseña incorrectos"));
        if (!user.isActive()) {
            throw ApiException.forbidden("La cuenta está desactivada. Contacta con un administrador");
        }
        return new AuthResponse(jwtService.generateToken(user), UserDto.from(user));
    }
}
