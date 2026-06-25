package com.smartfridge.service;

import com.smartfridge.dto.RegistrationDTO;
import com.smartfridge.entity.Role;
import com.smartfridge.entity.User;
import com.smartfridge.repository.RoleRepository;
import com.smartfridge.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public RegistrationService(UserRepository userRepository,
                               RoleRepository roleRepository,
                               PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User register(RegistrationDTO dto) {
        // Check username not already taken
        if (userRepository.existsByUsername(dto.getUsername()))
            throw new IllegalArgumentException("Username already taken");

        // Save user
        User user = new User(
                dto.getUsername(),
                passwordEncoder.encode(dto.getPassword()),
                true,
                dto.getFirstName(),
                dto.getLastName(),
                dto.getEmail()
        );
        userRepository.save(user);

        // Assign role
        roleRepository.save(new Role(user, "ROLE_CUSTOMER"));
        return user;
    }
}