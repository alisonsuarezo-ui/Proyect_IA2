package com.taskflow.service;

import com.taskflow.dto.UserDtos.AdminUpdateUserRequest;
import com.taskflow.dto.UserDtos.UpdateProfileRequest;
import com.taskflow.dto.UserDtos.UserDto;
import com.taskflow.dto.UserDtos.UserSummary;
import com.taskflow.entity.Role;
import com.taskflow.entity.User;
import com.taskflow.exception.ApiException;
import com.taskflow.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public UserDto me(User current) {
        return UserDto.from(load(current.getId()));
    }

    /** Directorio de usuarios activos (para asignar tareas y añadir miembros). */
    @Transactional(readOnly = true)
    public List<UserSummary> directory() {
        return userRepository.findByActiveTrueOrderByFullNameAsc().stream().map(UserSummary::from).toList();
    }

    @Transactional
    public UserDto updateProfile(User current, UpdateProfileRequest req) {
        User user = load(current.getId());
        user.setFullName(req.fullName().trim());
        if (req.newPassword() != null && !req.newPassword().isBlank()) {
            if (req.currentPassword() == null || !passwordEncoder.matches(req.currentPassword(), user.getPassword())) {
                throw ApiException.badRequest("La contraseña actual no es correcta");
            }
            user.setPassword(passwordEncoder.encode(req.newPassword()));
        }
        return UserDto.from(user);
    }

    // ---------- Administración ----------

    @Transactional(readOnly = true)
    public List<UserDto> listAll() {
        return userRepository.findAllByOrderByFullNameAsc().stream().map(UserDto::from).toList();
    }

    @Transactional
    public UserDto adminUpdate(Long id, AdminUpdateUserRequest req, User admin) {
        User user = load(id);
        boolean self = user.getId().equals(admin.getId());
        if (self && (req.role() == Role.USER || Boolean.FALSE.equals(req.active()))) {
            throw ApiException.badRequest("No puedes quitarte el rol de administrador ni desactivar tu propia cuenta");
        }
        if (req.fullName() != null && !req.fullName().isBlank()) user.setFullName(req.fullName().trim());
        if (req.role() != null) user.setRole(req.role());
        if (req.active() != null) user.setActive(req.active());
        return UserDto.from(user);
    }

    private User load(Long id) {
        return userRepository.findById(id).orElseThrow(() -> ApiException.notFound("Usuario no encontrado"));
    }
}
