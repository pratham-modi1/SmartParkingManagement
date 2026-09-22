package com.pratham.smartparkingmanagement.model.entities;

import java.time.LocalDateTime;
import com.pratham.smartparkingmanagement.model.enums.Role;

public class User {
    private int userId;
    private String name;
    private String email;
    private String passwordHash;
    private Role role;
    private LocalDateTime createdAt;

    public User() {}

    public User(int userId, String name, String email, String passwordHash,
                 Role role, LocalDateTime createdAt) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.createdAt = createdAt;
    }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}