package com.pratham.smartparkingmanagement.dao;

import com.pratham.smartparkingmanagement.model.entities.User;
import java.util.List;
import java.util.Optional;

public interface UserDao {
    void create(User user);
    User findById(int userId);
    List<User> findAll();
    void update(User user);
    void delete(int userId);

    Optional<User> findByEmail(String email);
}