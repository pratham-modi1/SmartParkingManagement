package com.pratham.smartparkingmanagement.dao;

import com.pratham.smartparkingmanagement.model.entities.Vehicle;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface VehicleDao {

    void create(Vehicle vehicle);

    Vehicle findById(int vehicleId);

    List<Vehicle> findAll();

    void update(Vehicle vehicle);

    void delete(int vehicleId);

    Optional<Vehicle> findByPlateNumber(String plateNumber);

    Vehicle findByIdForUpdate(
            Connection conn,
            int vehicleId
    ) throws SQLException;
}