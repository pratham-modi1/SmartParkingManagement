package com.pratham.smartparkingmanagement.service;

import com.pratham.smartparkingmanagement.config.DBConfig;
import com.pratham.smartparkingmanagement.dao.PaymentDao;
import com.pratham.smartparkingmanagement.dao.PricingConfigDao;
import com.pratham.smartparkingmanagement.dao.SlotDao;
import com.pratham.smartparkingmanagement.dao.TicketDao;
import com.pratham.smartparkingmanagement.dto.ExitResponse;
import com.pratham.smartparkingmanagement.model.entities.Payment;
import com.pratham.smartparkingmanagement.model.entities.PricingConfig;
import com.pratham.smartparkingmanagement.model.entities.Slot;
import com.pratham.smartparkingmanagement.model.entities.Ticket;
import com.pratham.smartparkingmanagement.model.enums.PaymentStatus;
import com.pratham.smartparkingmanagement.model.enums.SlotStatus;
import com.pratham.smartparkingmanagement.model.enums.TicketStatus;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;

public class ParkingExitService {

    private final SlotDao slotDao;
    private final TicketDao ticketDao;
    private final PaymentDao paymentDao;
    private final PricingConfigDao pricingConfigDao;

    public ParkingExitService(
            SlotDao slotDao,
            TicketDao ticketDao,
            PaymentDao paymentDao,
            PricingConfigDao pricingConfigDao) {

        this.slotDao = slotDao;
        this.ticketDao = ticketDao;
        this.paymentDao = paymentDao;
        this.pricingConfigDao = pricingConfigDao;
    }

    public ExitResponse exitVehicle(
            int ticketId,
            boolean lostTicket) {

        Connection conn = null;

        try {
            conn = DBConfig.getConnection();
            conn.setAutoCommit(false);

            // 1. Find ticket
            Ticket ticket = ticketDao.findById(conn, ticketId);

            if (ticket == null) {
                throw new IllegalStateException(
                        "Ticket not found"
                );
            }

            // 2. Ticket must be ACTIVE
            if (ticket.getStatus() != TicketStatus.ACTIVE) {
                throw new IllegalStateException(
                        "Ticket is not active"
                );
            }

            // 3. Get slot
            Slot slot = slotDao.findById(ticket.getSlotId());

            if (slot == null) {
                throw new IllegalStateException(
                        "Slot not found"
                );
            }

            // 4. Get pricing configuration
            PricingConfig pricingConfig =
                    pricingConfigDao.getCurrentConfig();

            // 5. Get exit time once
            LocalDateTime exitTime = LocalDateTime.now();

            // 6. Calculate fee
            BigDecimal fee = FeeCalculator.calculateFee(
                    ticket.getEntryTime(),
                    exitTime,
                    pricingConfig,
                    lostTicket
            );

            // 7. Create payment
            Payment payment = new Payment(
                    0,
                    ticketId,
                    fee,
                    PaymentStatus.SUCCESS,
                    exitTime
            );

            paymentDao.create(conn, payment);

            // 8. Complete ticket
            ticket.setExitTime(exitTime);
            ticket.setStatus(TicketStatus.COMPLETED);
            ticket.setLostTicketFlag(lostTicket);

            ticketDao.update(conn, ticket);

            // 9. Free slot
            slot.setStatus(SlotStatus.AVAILABLE);

            slotDao.update(conn, slot);

            // 10. Commit everything
            conn.commit();

            // Return BOTH ticket and calculated fee
            return new ExitResponse(ticket, fee);

        } catch (Exception e) {

            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackException) {
                    rollbackException.printStackTrace();
                }
            }

            throw new RuntimeException(
                    "Failed to exit vehicle",
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