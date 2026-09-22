package com.pratham.smartparkingmanagement.dto;

import com.pratham.smartparkingmanagement.model.entities.User;
import com.pratham.smartparkingmanagement.model.enums.Role;

import java.time.LocalDateTime;

public class UserResponse {

    private int userId;
    private String name;
    private String email;
    private Role role;
    private LocalDateTime createdAt;

    public UserResponse() {
    }

    public UserResponse(int userId, String name, String email,
                        Role role, LocalDateTime createdAt) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.role = role;
        this.createdAt = createdAt;
    }

    public static UserResponse fromUser(User user) {
        return new UserResponse(
                user.getUserId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getCreatedAt()
        );
    }

    public int getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public Role getRole() {
        return role;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}