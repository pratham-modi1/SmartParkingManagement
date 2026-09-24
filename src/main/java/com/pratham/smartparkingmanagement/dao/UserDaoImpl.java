package com.pratham.smartparkingmanagement.dao;

import com.pratham.smartparkingmanagement.config.DBConfig;
import com.pratham.smartparkingmanagement.model.entities.User;
import com.pratham.smartparkingmanagement.model.enums.Role;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class UserDaoImpl implements UserDao {

    @Override
    public void create(User user) {

        String sql =
                "INSERT INTO User " +
                "(name, email, password_hash, role) " +
                "VALUES (?, ?, ?, ?)";

        try (Connection connection =
                     DBConfig.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(
                             sql,
                             Statement.RETURN_GENERATED_KEYS
                     )) {

            statement.setString(
                    1,
                    user.getName()
            );

            statement.setString(
                    2,
                    user.getEmail()
            );

            statement.setString(
                    3,
                    user.getPasswordHash()
            );

            statement.setString(
                    4,
                    user.getRole().name()
            );

            statement.executeUpdate();

            try (ResultSet rs =
                         statement.getGeneratedKeys()) {

                if (rs.next()) {
                    user.setUserId(
                            rs.getInt(1)
                    );
                }
            }

        } catch (SQLIntegrityConstraintViolationException e) {

            throw new RuntimeException(
                    "User with email "
                            + user.getEmail()
                            + " already exists",
                    e
            );

        } catch (SQLException e) {

            throw new RuntimeException(
                    "Failed to create user",
                    e
            );
        }
    }

    @Override
    public User findById(int userId) {

        String sql =
                "SELECT * FROM User " +
                "WHERE user_id = ?";

        try (Connection connection =
                     DBConfig.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, userId);

            try (ResultSet rs =
                         statement.executeQuery()) {

                if (rs.next()) {
                    return mapRow(rs);
                }
            }

        } catch (SQLException e) {

            throw new RuntimeException(
                    "Failed to find user",
                    e
            );
        }

        return null;
    }

    @Override
    public List<User> findAll() {

        String sql = "SELECT * FROM User";

        List<User> users =
                new ArrayList<>();

        try (Connection connection =
                     DBConfig.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql);
             ResultSet rs =
                     statement.executeQuery()) {

            while (rs.next()) {
                users.add(
                        mapRow(rs)
                );
            }

        } catch (SQLException e) {

            throw new RuntimeException(
                    "Failed to fetch users",
                    e
            );
        }

        return users;
    }

    @Override
    public void update(User user) {

        String sql =
                "UPDATE User " +
                "SET name=?, email=?, password_hash=?, role=? " +
                "WHERE user_id=?";

        try (Connection connection =
                     DBConfig.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(
                    1,
                    user.getName()
            );

            statement.setString(
                    2,
                    user.getEmail()
            );

            statement.setString(
                    3,
                    user.getPasswordHash()
            );

            statement.setString(
                    4,
                    user.getRole().name()
            );

            statement.setInt(
                    5,
                    user.getUserId()
            );

            statement.executeUpdate();

        } catch (SQLIntegrityConstraintViolationException e) {

            throw new RuntimeException(
                    "User with email "
                            + user.getEmail()
                            + " already exists",
                    e
            );

        } catch (SQLException e) {

            throw new RuntimeException(
                    "Failed to update user",
                    e
            );
        }
    }

    @Override
    public void delete(int userId) {

        String sql =
                "DELETE FROM User " +
                "WHERE user_id = ?";

        try (Connection connection =
                     DBConfig.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(
                    1,
                    userId
            );

            statement.executeUpdate();

        } catch (SQLException e) {

            throw new RuntimeException(
                    "Failed to delete user",
                    e
            );
        }
    }

    @Override
    public Optional<User> findByEmail(
            String email) {

        String sql =
                "SELECT * FROM User " +
                "WHERE email = ?";

        try (Connection connection =
                     DBConfig.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(
                    1,
                    email
            );

            try (ResultSet rs =
                         statement.executeQuery()) {

                if (rs.next()) {
                    return Optional.of(
                            mapRow(rs)
                    );
                }
            }

        } catch (SQLException e) {

            throw new RuntimeException(
                    "Failed to find user by email",
                    e
            );
        }

        return Optional.empty();
    }

    private User mapRow(
            ResultSet rs
    ) throws SQLException {

        Timestamp createdAt =
                rs.getTimestamp("created_at");

        return new User(
                rs.getInt("user_id"),
                rs.getString("name"),
                rs.getString("email"),
                rs.getString("password_hash"),
                Role.valueOf(
                        rs.getString("role")
                ),
                createdAt != null
                        ? createdAt.toLocalDateTime()
                        : null
        );
    }
}