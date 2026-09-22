package com.pratham.smartparkingmanagement.dao;

import com.pratham.smartparkingmanagement.config.DBConfig;
import com.pratham.smartparkingmanagement.model.entities.Ticket;
import com.pratham.smartparkingmanagement.model.enums.TicketStatus;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TicketDaoImpl implements TicketDao {

    @Override
    public void create(Ticket ticket) {
        try (Connection connection = DBConfig.getConnection()) {
            create(connection, ticket);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create ticket", e);
        }
    }

    @Override
    public void create(Connection conn, Ticket ticket) throws SQLException {
        // entryTime is set here in Java, not left to the DB default —
        // keeps the in-memory object and the DB row guaranteed in sync.
        if (ticket.getEntryTime() == null) {
            ticket.setEntryTime(LocalDateTime.now());
        }

        String sql = "INSERT INTO Ticket (vehicle_id, slot_id, entry_time, status, created_by, overstayed_flag, lost_ticket_flag) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement statement = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, ticket.getVehicleId());
            statement.setInt(2, ticket.getSlotId());
            statement.setTimestamp(3, Timestamp.valueOf(ticket.getEntryTime()));
            statement.setString(4, ticket.getStatus().name());
            statement.setInt(5, ticket.getCreatedByUserId());
            statement.setBoolean(6, ticket.isOverstayedFlag());
            statement.setBoolean(7, ticket.isLostTicketFlag());
            statement.executeUpdate();

            try (ResultSet rs = statement.getGeneratedKeys()) {
                if (rs.next()) ticket.setTicketId(rs.getInt(1));
            }
        }
    }

    @Override
    public Ticket findById(int ticketId) {
        try (Connection connection = DBConfig.getConnection()) {
            return findById(connection, ticketId);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find ticket", e);
        }
    }

    @Override
    public Ticket findById(Connection conn, int ticketId) throws SQLException {
        String sql = "SELECT * FROM Ticket WHERE ticket_id = ?";
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setInt(1, ticketId);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    @Override
    public List<Ticket> findAll() {
        String sql = "SELECT * FROM Ticket";
        List<Ticket> tickets = new ArrayList<>();
        try (Connection connection = DBConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) tickets.add(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch tickets", e);
        }
        return tickets;
    }

    @Override
    public void update(Ticket ticket) {
        try (Connection connection = DBConfig.getConnection()) {
            update(connection, ticket);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update ticket", e);
        }
    }

    @Override
    public void update(Connection conn, Ticket ticket) throws SQLException {
        String sql = "UPDATE Ticket SET exit_time=?, status=?, overstayed_flag=?, lost_ticket_flag=? WHERE ticket_id=?";
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            if (ticket.getExitTime() != null) {
                statement.setTimestamp(1, Timestamp.valueOf(ticket.getExitTime()));
            } else {
                statement.setNull(1, Types.TIMESTAMP);
            }
            statement.setString(2, ticket.getStatus().name());
            statement.setBoolean(3, ticket.isOverstayedFlag());
            statement.setBoolean(4, ticket.isLostTicketFlag());
            statement.setInt(5, ticket.getTicketId());
            statement.executeUpdate();
        }
    }

    @Override
    public void delete(int ticketId) {
        String sql = "DELETE FROM Ticket WHERE ticket_id = ?";
        try (Connection connection = DBConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, ticketId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete ticket", e);
        }
    }

    @Override
    public Optional<Ticket> findActiveByVehicle(int vehicleId) {
        String sql = "SELECT * FROM Ticket WHERE vehicle_id = ? AND status = 'ACTIVE'";
        try (Connection connection = DBConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, vehicleId);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find active ticket", e);
        }
        return Optional.empty();
    }

    @Override
    public List<Ticket> findByVehicleId(int vehicleId) {
        String sql = "SELECT * FROM Ticket WHERE vehicle_id = ?";
        List<Ticket> tickets = new ArrayList<>();
        try (Connection connection = DBConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, vehicleId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) tickets.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch tickets by vehicle", e);
        }
        return tickets;
    }

    private Ticket mapRow(ResultSet rs) throws SQLException {
        Timestamp entry = rs.getTimestamp("entry_time");
        Timestamp exit = rs.getTimestamp("exit_time");
        return new Ticket(
                rs.getInt("ticket_id"),
                rs.getInt("vehicle_id"),
                rs.getInt("slot_id"),
                entry != null ? entry.toLocalDateTime() : null,
                exit != null ? exit.toLocalDateTime() : null,
                TicketStatus.valueOf(rs.getString("status")),
                rs.getInt("created_by"),
                rs.getBoolean("overstayed_flag"),
                rs.getBoolean("lost_ticket_flag")
        );
    }
}