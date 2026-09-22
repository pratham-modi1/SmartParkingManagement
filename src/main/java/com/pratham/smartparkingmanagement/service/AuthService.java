package com.pratham.smartparkingmanagement.service;

import com.pratham.smartparkingmanagement.dao.UserDao;
import com.pratham.smartparkingmanagement.dto.UserResponse;
import com.pratham.smartparkingmanagement.model.entities.User;
import com.pratham.smartparkingmanagement.model.enums.Role;
import com.pratham.smartparkingmanagement.security.JwtUtil;
import org.mindrot.jbcrypt.BCrypt;

public class AuthService {

    private final UserDao userDao;
    private final JwtUtil jwtUtil;

    public AuthService(UserDao userDao, JwtUtil jwtUtil) {
        this.userDao = userDao;
        this.jwtUtil = jwtUtil;
    }

    public UserResponse register(String name, String email,
                                 String rawPassword, Role role) {

        if (userDao.findByEmail(email).isPresent()) {
            throw new RuntimeException("Email already registered");
        }

        String passwordHash =
                BCrypt.hashpw(rawPassword, BCrypt.gensalt());

        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPasswordHash(passwordHash);
        user.setRole(role);

        userDao.create(user);

        return UserResponse.fromUser(user);
    }

    public String login(String email, String rawPassword) {

        User user = userDao.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));

        if (!BCrypt.checkpw(rawPassword, user.getPasswordHash())) {
            throw new RuntimeException("Invalid credentials");
        }

        return jwtUtil.generateToken(user);
    }
}