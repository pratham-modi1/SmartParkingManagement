package com.pratham.smartparkingmanagement.service;

import com.pratham.smartparkingmanagement.config.DBConfig;
import com.pratham.smartparkingmanagement.dao.SlotDao;
import com.pratham.smartparkingmanagement.dao.TicketDao;
import com.pratham.smartparkingmanagement.dao.VehicleDao;
import com.pratham.smartparkingmanagement.model.entities.Slot;
import com.pratham.smartparkingmanagement.model.entities.Ticket;
import com.pratham.smartparkingmanagement.model.entities.Vehicle;
import com.pratham.smartparkingmanagement.model.enums.SlotStatus;
import com.pratham.smartparkingmanagement.model.enums.TicketStatus;
import com.pratham.smartparkingmanagement.model.enums.VehicleType;

import java.sql.Connection;
import java.sql.SQLException;

public class ParkingEntryService {

    private final VehicleDao vehicleDao;
    private final SlotDao slotDao;
    private final TicketDao ticketDao;

    public ParkingEntryService(
            VehicleDao vehicleDao,
            SlotDao slotDao,
            TicketDao ticketDao) {

        this.vehicleDao = vehicleDao;
        this.slotDao = slotDao;
        this.ticketDao = ticketDao;
    }

    public Ticket parkVehicle(
            String plateNumber,
            VehicleType type,
            int lotId,
            int actingUserId) {

        Connection conn = null;

        try {
            conn = DBConfig.getConnection();
            conn.setAutoCommit(false);

            // 1. Find vehicle or create it
            Vehicle vehicle = vehicleDao.findByPlateNumber(plateNumber)
                    .orElseGet(() -> {

                        Vehicle newVehicle = new Vehicle(
                                0,
                                plateNumber,
                                type,
                                null
                        );

                        vehicleDao.create(newVehicle);

                        return newVehicle;
                    });

            // 2. Vehicle cannot have another active ticket
            if (ticketDao.findActiveByVehicle(
                    vehicle.getVehicleId()).isPresent()) {

                throw new IllegalStateException(
                        "Vehicle already has an active ticket"
                );
            }

            // 3. Find and lock available slot
            Slot slot = slotDao.findFirstAvailableForUpdate(
                    conn,
                    lotId,
                    type
            );

            if (slot == null) {

                throw new IllegalStateException(
                        "No available slot"
                );
            }

            // 4. Create active ticket
            Ticket ticket = new Ticket(
                    0,
                    vehicle.getVehicleId(),
                    slot.getSlotId(),
                    null,
                    null,
                    TicketStatus.ACTIVE,
                    actingUserId,
                    false,
                    false
            );

            ticketDao.create(conn, ticket);

            // 5. Occupy slot
            slot.setStatus(SlotStatus.OCCUPIED);

            slotDao.update(conn, slot);

            // 6. Commit everything
            conn.commit();

            return ticket;

        } catch (Exception e) {

            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackException) {
                    rollbackException.printStackTrace();
                }
            }

            throw new RuntimeException(
                    "Failed to park vehicle",
                    e
            );

        } finally {

            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}