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
import java.sql.SQLIntegrityConstraintViolationException;

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

            // 1. Find vehicle by plate
            Vehicle vehicle =
                    vehicleDao.findByPlateNumber(
                            plateNumber
                    ).orElse(null);

            // 2. Vehicle doesn't exist -> create it
            if (vehicle == null) {

                Vehicle newVehicle =
                        new Vehicle(
                                0,
                                plateNumber,
                                type,
                                null
                        );

                try {

                    vehicleDao.create(
                            newVehicle
                    );

                    vehicle = newVehicle;

                } catch (RuntimeException e) {

                    /*
                     * Flaw #1 fix:
                     * Another concurrent request may have
                     * inserted the same plate first.
                     *
                     * Re-read the existing vehicle instead
                     * of treating it as a fatal error.
                     */
                    if (e.getCause()
                            instanceof SQLIntegrityConstraintViolationException) {

                        vehicle =
                                vehicleDao
                                        .findByPlateNumber(
                                                plateNumber
                                        )
                                        .orElseThrow(() ->
                                                new IllegalStateException(
                                                        "Vehicle creation race occurred, " +
                                                        "but vehicle could not be found"
                                                )
                                        );

                    } else {

                        throw e;
                    }
                }
            }

            /*
             * 3. Flaw #2 fix:
             * Lock the vehicle row.
             *
             * Only one transaction can hold this lock
             * for this vehicle at a time.
             */
            vehicle =
                    vehicleDao.findByIdForUpdate(
                            conn,
                            vehicle.getVehicleId()
                    );

            if (vehicle == null) {

                throw new IllegalStateException(
                        "Vehicle not found"
                );
            }

            /*
             * 4. Check active ticket using the SAME
             * transaction connection.
             *
             * Because the vehicle is already locked,
             * concurrent requests for this vehicle
             * cannot pass this check simultaneously.
             */
            if (ticketDao.findActiveByVehicle(
                    conn,
                    vehicle.getVehicleId()
            ).isPresent()) {

                throw new IllegalStateException(
                        "Vehicle already has an active ticket"
                );
            }

            // 5. Lock first available slot
            Slot slot =
                    slotDao.findFirstAvailableForUpdate(
                            conn,
                            lotId,
                            type
                    );

            if (slot == null) {

                throw new IllegalStateException(
                        "No available slot"
                );
            }

            // 6. Create active ticket
            Ticket ticket =
                    new Ticket(
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

            ticketDao.create(
                    conn,
                    ticket
            );

            // 7. Occupy slot
            slot.setStatus(
                    SlotStatus.OCCUPIED
            );

            slotDao.update(
                    conn,
                    slot
            );

            // 8. Commit entire transaction
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
        } catch (SQLException e) {
            e.printStackTrace();
        }

        try {
            conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
    }
}