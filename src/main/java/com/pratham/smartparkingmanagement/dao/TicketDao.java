package com.pratham.smartparkingmanagement.dao;

import com.pratham.smartparkingmanagement.model.entities.Ticket;
import java.sql.Connection;
import java.util.List;
import java.util.Optional;

public interface TicketDao {
    void create(Ticket ticket);
    Ticket findById(int ticketId);
    List<Ticket> findAll();
    void update(Ticket ticket);
    void delete(int ticketId);

    Optional<Ticket> findActiveByVehicle(int vehicleId);
    List<Ticket> findByVehicleId(int vehicleId);

    // transactional variants used inside ParkingService (Day 4)
    void create(Connection conn, Ticket ticket) throws java.sql.SQLException;
    void update(Connection conn, Ticket ticket) throws java.sql.SQLException;
    Ticket findById(Connection conn, int ticketId) throws java.sql.SQLException;
}