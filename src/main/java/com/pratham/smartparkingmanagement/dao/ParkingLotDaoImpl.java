package com.pratham.smartparkingmanagement.dao;

import com.pratham.smartparkingmanagement.config.DBConfig;
import com.pratham.smartparkingmanagement.model.entities.ParkingLot;
import com.pratham.smartparkingmanagement.model.enums.LotStatus;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ParkingLotDaoImpl implements ParkingLotDao {

    @Override
    public void create(ParkingLot parkingLot) {
        String sql = "INSERT INTO ParkingLot (name, location, status) VALUES (?, ?, ?)";

        try (Connection connection = DBConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            statement.setString(1, parkingLot.getName());
            statement.setString(2, parkingLot.getLocation());
            statement.setString(3, parkingLot.getStatus().name());
            statement.executeUpdate();

            try (ResultSet rs = statement.getGeneratedKeys()) {
                if (rs.next()) parkingLot.setParkingLotId(rs.getInt(1));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create parking lot", e);
        }
    }

    @Override
    public ParkingLot findById(int parkingLotId) {
        String sql = "SELECT * FROM ParkingLot WHERE parking_lot_id = ?";

        try (Connection connection = DBConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, parkingLotId);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find parking lot", e);
        }
        return null;
    }

    @Override
    public List<ParkingLot> findAll() {
        String sql = "SELECT * FROM ParkingLot";
        List<ParkingLot> parkingLots = new ArrayList<>();

        try (Connection connection = DBConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {

            while (rs.next()) parkingLots.add(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch parking lots", e);
        }
        return parkingLots;
    }

    @Override
    public void update(ParkingLot parkingLot) {
        String sql = "UPDATE ParkingLot SET name = ?, location = ?, status = ? WHERE parking_lot_id = ?";

        try (Connection connection = DBConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, parkingLot.getName());
            statement.setString(2, parkingLot.getLocation());
            statement.setString(3, parkingLot.getStatus().name());
            statement.setInt(4, parkingLot.getParkingLotId());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update parking lot", e);
        }
    }

    @Override
    public void delete(int parkingLotId) {
        String sql = "DELETE FROM ParkingLot WHERE parking_lot_id = ?";

        try (Connection connection = DBConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, parkingLotId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete parking lot", e);
        }
    }

    private ParkingLot mapRow(ResultSet rs) throws SQLException {
        Timestamp createdAt = rs.getTimestamp("created_at");
        return new ParkingLot(
                rs.getInt("parking_lot_id"),
                rs.getString("name"),
                rs.getString("location"),
                LotStatus.valueOf(rs.getString("status")),
                createdAt != null ? createdAt.toLocalDateTime() : null
        );
    }
}