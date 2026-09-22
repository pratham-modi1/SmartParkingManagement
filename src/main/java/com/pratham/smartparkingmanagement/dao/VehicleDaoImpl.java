package com.pratham.smartparkingmanagement.dao;

import com.pratham.smartparkingmanagement.config.DBConfig;
import com.pratham.smartparkingmanagement.model.entities.Vehicle;
import com.pratham.smartparkingmanagement.model.enums.VehicleType;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class VehicleDaoImpl implements VehicleDao {

    @Override
    public void create(Vehicle vehicle) {
        String sql = "INSERT INTO Vehicle (plate_number, vehicle_type, owner_id) VALUES (?, ?, ?)";

        try (Connection connection = DBConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            statement.setString(1, vehicle.getPlateNumber());
            statement.setString(2, vehicle.getVehicleType().getDbCode());

            if (vehicle.getOwnerId() != null) {
                statement.setInt(3, vehicle.getOwnerId());
            } else {
                statement.setNull(3, Types.INTEGER);
            }

            statement.executeUpdate();

            try (ResultSet rs = statement.getGeneratedKeys()) {
                if (rs.next()) vehicle.setVehicleId(rs.getInt(1));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create vehicle", e);
        }
    }

    @Override
    public Vehicle findById(int vehicleId) {
        String sql = "SELECT * FROM Vehicle WHERE vehicle_id = ?";

        try (Connection connection = DBConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, vehicleId);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find vehicle", e);
        }
        return null;
    }

    @Override
    public List<Vehicle> findAll() {
        String sql = "SELECT * FROM Vehicle";
        List<Vehicle> vehicles = new ArrayList<>();

        try (Connection connection = DBConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {

            while (rs.next()) vehicles.add(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch vehicles", e);
        }
        return vehicles;
    }

    @Override
    public void update(Vehicle vehicle) {
        String sql = "UPDATE Vehicle SET plate_number=?, vehicle_type=?, owner_id=? WHERE vehicle_id=?";

        try (Connection connection = DBConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, vehicle.getPlateNumber());
            statement.setString(2, vehicle.getVehicleType().getDbCode());

            if (vehicle.getOwnerId() != null) {
                statement.setInt(3, vehicle.getOwnerId());
            } else {
                statement.setNull(3, Types.INTEGER);
            }

            statement.setInt(4, vehicle.getVehicleId());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update vehicle", e);
        }
    }

    @Override
    public void delete(int vehicleId) {
        String sql = "DELETE FROM Vehicle WHERE vehicle_id = ?";

        try (Connection connection = DBConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, vehicleId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete vehicle", e);
        }
    }

    @Override
    public Optional<Vehicle> findByPlateNumber(String plateNumber) {
        String sql = "SELECT * FROM Vehicle WHERE plate_number = ?";

        try (Connection connection = DBConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, plateNumber);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find vehicle by plate", e);
        }
        return Optional.empty();
    }

    private Vehicle mapRow(ResultSet rs) throws SQLException {
        int ownerIdRaw = rs.getInt("owner_id");
        Integer ownerId = rs.wasNull() ? null : ownerIdRaw; // critical: don't let NULL become 0

        return new Vehicle(
                rs.getInt("vehicle_id"),
                rs.getString("plate_number"),
                VehicleType.fromDbCode(rs.getString("vehicle_type")),
                ownerId
        );
    }
}