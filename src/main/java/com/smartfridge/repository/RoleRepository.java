package com.smartfridge.repository;

import com.smartfridge.entity.Role;
import com.smartfridge.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoleRepository extends JpaRepository<Role, Integer> {
    List<Role> findByUser(User user);
}