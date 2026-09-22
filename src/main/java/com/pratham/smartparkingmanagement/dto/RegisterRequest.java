package com.pratham.smartparkingmanagement.dto;

import com.pratham.smartparkingmanagement.model.enums.Role;

public class RegisterRequest {

    private String name;
    private String email;
    private String password;
    private Role role;

    public RegisterRequest() {
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public Role getRole() {
        return role;
    }
}