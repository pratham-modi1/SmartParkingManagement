package com.pratham.smartparkingmanagement.dao;

import com.pratham.smartparkingmanagement.config.DBConfig;
import com.pratham.smartparkingmanagement.model.entities.Slot;
import com.pratham.smartparkingmanagement.model.enums.SlotStatus;
import com.pratham.smartparkingmanagement.model.enums.VehicleType;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class SlotDaoImpl implements SlotDao {

    @Override
    public void create(Slot slot) {
        String sql = "INSERT INTO Slot (slot_label, vehicle_type, status, parking_lot_id) VALUES (?, ?, ?, ?)";

        try (Connection connection = DBConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            statement.setString(1, slot.getSlotLabel());
            statement.setString(2, slot.getVehicleType().getDbCode());
            statement.setString(3, slot.getStatus().name());
            statement.setInt(4, slot.getParkingLotId());
            statement.executeUpdate();

            try (ResultSet rs = statement.getGeneratedKeys()) {
                if (rs.next()) slot.setSlotId(rs.getInt(1));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create slot", e);
        }
    }

    @Override
    public Slot findById(int slotId) {
        String sql = "SELECT * FROM Slot WHERE slot_id = ?";

        try (Connection connection = DBConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, slotId);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find slot", e);
        }
        return null;
    }

    @Override
    public List<Slot> findAll() {
        String sql = "SELECT * FROM Slot";
        List<Slot> slots = new ArrayList<>();

        try (Connection connection = DBConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {

            while (rs.next()) slots.add(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch slots", e);
        }
        return slots;
    }

    @Override
    public void update(Slot slot) {
        String sql = "UPDATE Slot SET slot_label=?, vehicle_type=?, status=?, parking_lot_id=? WHERE slot_id=?";

        try (Connection connection = DBConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, slot.getSlotLabel());
            statement.setString(2, slot.getVehicleType().getDbCode());
            statement.setString(3, slot.getStatus().name());
            statement.setInt(4, slot.getParkingLotId());
            statement.setInt(5, slot.getSlotId());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update slot", e);
        }
    }

    @Override
    public void update(Connection conn, Slot slot) throws SQLException {

    String sql = "UPDATE Slot SET slot_label=?, vehicle_type=?, status=?, parking_lot_id=? WHERE slot_id=?";

    try (PreparedStatement statement = conn.prepareStatement(sql)) {

        statement.setString(1, slot.getSlotLabel());
        statement.setString(2, slot.getVehicleType().getDbCode());
        statement.setString(3, slot.getStatus().name());
        statement.setInt(4, slot.getParkingLotId());
        statement.setInt(5, slot.getSlotId());

        statement.executeUpdate();
    }
}


    @Override
    public void delete(int slotId) {
        String sql = "DELETE FROM Slot WHERE slot_id = ?";

        try (Connection connection = DBConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, slotId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete slot", e);
        }
    }

    @Override
    public List<Slot> findAvailableByLotAndType(int lotId, VehicleType type) {
        String sql = "SELECT * FROM Slot WHERE parking_lot_id=? AND vehicle_type=? AND status='AVAILABLE'";
        List<Slot> slots = new ArrayList<>();

        try (Connection connection = DBConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, lotId);
            statement.setString(2, type.getDbCode());

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) slots.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch available slots", e);
        }
        return slots;
    }

    @Override
    public Slot findFirstAvailableForUpdate(Connection conn, int lotId, VehicleType type) throws SQLException {
        // FOR UPDATE locks the matching row(s) until the caller commits/rolls back —
        // this is what prevents two concurrent transactions from both grabbing the same slot.
        String sql = "SELECT * FROM Slot WHERE parking_lot_id=? AND vehicle_type=? AND status='AVAILABLE' " +
                     "LIMIT 1 FOR UPDATE";

        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setInt(1, lotId);
            statement.setString(2, type.getDbCode());

            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    private Slot mapRow(ResultSet rs) throws SQLException {
        return new Slot(
                rs.getInt("slot_id"),
                rs.getString("slot_label"),
                VehicleType.fromDbCode(rs.getString("vehicle_type")),
                SlotStatus.valueOf(rs.getString("status")),
                rs.getInt("parking_lot_id")
        );
    }
}