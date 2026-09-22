package com.pratham.smartparkingmanagement.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

public class DBConfig {

    private static final HikariDataSource dataSource;

    static {

        HikariConfig config = new HikariConfig();

        config.setJdbcUrl(DatabaseProperties.getUrl());
        config.setUsername(DatabaseProperties.getUsername());
        config.setPassword(DatabaseProperties.getPassword());

        config.setMaximumPoolSize(DatabaseProperties.getPoolSize());

        dataSource = new HikariDataSource(config);
    }

    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }
}