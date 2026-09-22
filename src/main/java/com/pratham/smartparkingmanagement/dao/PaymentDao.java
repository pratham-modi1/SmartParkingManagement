package com.pratham.smartparkingmanagement.dao;

import com.pratham.smartparkingmanagement.model.entities.Payment;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public interface PaymentDao {

    void create(Payment payment);

    void create(Connection conn, Payment payment) throws SQLException;

    Payment findById(int paymentId);

    List<Payment> findAll();

    void update(Payment payment);

    void delete(int paymentId);

    List<Payment> findByTicketId(int ticketId);
}