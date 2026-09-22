package com.pratham.smartparkingmanagement.dao;

import com.pratham.smartparkingmanagement.config.DBConfig;
import com.pratham.smartparkingmanagement.model.entities.Payment;
import com.pratham.smartparkingmanagement.model.enums.PaymentStatus;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class PaymentDaoImpl implements PaymentDao {

    @Override
    public void create(Payment payment) {
        String sql = "INSERT INTO Payment (ticket_id, amount, status, paid_at) VALUES (?, ?, ?, ?)";

        try (Connection connection = DBConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            statement.setInt(1, payment.getTicketId());
            statement.setBigDecimal(2, payment.getAmount());
            statement.setString(3, payment.getStatus().name());

            if (payment.getPaidAt() != null) {
                statement.setTimestamp(4, Timestamp.valueOf(payment.getPaidAt()));
            } else {
                statement.setNull(4, Types.TIMESTAMP);
            }

            statement.executeUpdate();

            try (ResultSet rs = statement.getGeneratedKeys()) {
                if (rs.next()) payment.setPaymentId(rs.getInt(1));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create payment", e);
        }
    }


    @Override
    public Payment findById(int paymentId) {
        String sql = "SELECT * FROM Payment WHERE payment_id = ?";

        try (Connection connection = DBConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, paymentId);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find payment", e);
        }
        return null;
    }

    
    @Override
    public List<Payment> findAll() {
        String sql = "SELECT * FROM Payment";
        List<Payment> payments = new ArrayList<>();

        try (Connection connection = DBConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {

            while (rs.next()) payments.add(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch payments", e);
        }
        return payments;
    }

    
    
    @Override
    public void update(Payment payment) {
        String sql = "UPDATE Payment SET amount=?, status=?, paid_at=? WHERE payment_id=?";

        try (Connection connection = DBConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setBigDecimal(1, payment.getAmount());
            statement.setString(2, payment.getStatus().name());

            if (payment.getPaidAt() != null) {
                statement.setTimestamp(3, Timestamp.valueOf(payment.getPaidAt()));
            } else {
                statement.setNull(3, Types.TIMESTAMP);
            }

            statement.setInt(4, payment.getPaymentId());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update payment", e);
        }
    }

    @Override
    public void delete(int paymentId) {
        String sql = "DELETE FROM Payment WHERE payment_id = ?";

        try (Connection connection = DBConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, paymentId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete payment", e);
        }
    }

    @Override
    public List<Payment> findByTicketId(int ticketId) {
        String sql = "SELECT * FROM Payment WHERE ticket_id = ?";
        List<Payment> payments = new ArrayList<>();

        try (Connection connection = DBConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, ticketId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) payments.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch payments by ticket", e);
        }
        return payments;
    }

    private Payment mapRow(ResultSet rs) throws SQLException {
        Timestamp paidAt = rs.getTimestamp("paid_at");
        return new Payment(
                rs.getInt("payment_id"),
                rs.getInt("ticket_id"),
                rs.getBigDecimal("amount"),
                PaymentStatus.valueOf(rs.getString("status")),
                paidAt != null ? paidAt.toLocalDateTime() : null
        );
    }



    @Override
public void create(Connection conn, Payment payment) throws SQLException {

    String sql = "INSERT INTO Payment (ticket_id, amount, status, paid_at) VALUES (?, ?, ?, ?)";

    try (PreparedStatement statement =
                 conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

        statement.setInt(1, payment.getTicketId());
        statement.setBigDecimal(2, payment.getAmount());
        statement.setString(3, payment.getStatus().name());

        if (payment.getPaidAt() != null) {
            statement.setTimestamp(4, Timestamp.valueOf(payment.getPaidAt()));
        } else {
            statement.setNull(4, Types.TIMESTAMP);
        }

        statement.executeUpdate();

        try (ResultSet rs = statement.getGeneratedKeys()) {
            if (rs.next()) {
                payment.setPaymentId(rs.getInt(1));
            }
        }
    }
}
}