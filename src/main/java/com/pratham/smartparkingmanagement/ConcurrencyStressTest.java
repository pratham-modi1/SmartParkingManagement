package com.pratham.smartparkingmanagement;

import com.pratham.smartparkingmanagement.dao.*;
import com.pratham.smartparkingmanagement.model.entities.Ticket;
import com.pratham.smartparkingmanagement.model.entities.Vehicle;
import com.pratham.smartparkingmanagement.model.enums.TicketStatus;
import com.pratham.smartparkingmanagement.model.enums.VehicleType;
import com.pratham.smartparkingmanagement.service.ParkingEntryService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class ConcurrencyStressTest {

    public static void main(String[] args) throws Exception {

        VehicleDao vehicleDao = new VehicleDaoImpl();
        SlotDao slotDao = new SlotDaoImpl();
        TicketDao ticketDao = new TicketDaoImpl();

        ParkingEntryService entryService =
                new ParkingEntryService(vehicleDao, slotDao, ticketDao);

        int requests = 5;

        // SHORT + UNIQUE plate
        String plate = "R" + (System.currentTimeMillis() % 100000000);

        // Create vehicle ONCE
        Vehicle vehicle = new Vehicle(
                0,
                plate,
                VehicleType.FOUR_WHEELER,
                null
        );

        vehicleDao.create(vehicle);

        System.out.println("================================");
        System.out.println(" SAME VEHICLE CONCURRENCY TEST");
        System.out.println("================================");
        System.out.println("Plate: " + plate);
        System.out.println("Vehicle ID: " + vehicle.getVehicleId());
        System.out.println("Requests: " + requests);
        System.out.println();

        ExecutorService executor =
                Executors.newFixedThreadPool(requests);

        CountDownLatch ready =
                new CountDownLatch(requests);

        CountDownLatch start =
                new CountDownLatch(1);

        List<Future<Ticket>> results = new ArrayList<>();

        for (int i = 0; i < requests; i++) {

            results.add(executor.submit(() -> {

                ready.countDown();

                start.await();

                return entryService.parkVehicle(
                        plate,
                        VehicleType.FOUR_WHEELER,
                        2,
                        1
                );
            }));
        }

        ready.await();

        System.out.println("Starting concurrent requests...");
        start.countDown();

        int success = 0;
        int failed = 0;

        for (int i = 0; i < results.size(); i++) {

            try {

                Ticket ticket =
                        results.get(i).get(15, TimeUnit.SECONDS);

                success++;

                System.out.println(
                        "Request " + i +
                        " SUCCESS | Ticket=" +
                        ticket.getTicketId() +
                        " | Slot=" +
                        ticket.getSlotId()
                );

            } catch (Exception e) {

                failed++;

                Throwable cause = e;

                while (cause.getCause() != null) {
                    cause = cause.getCause();
                }

                System.out.println(
                        "Request " + i +
                        " FAILED | " +
                        cause.getMessage()
                );
            }
        }

        executor.shutdown();

        // Check database
        List<Ticket> tickets =
                ticketDao.findByVehicleId(
                        vehicle.getVehicleId()
                );

        int activeTickets = 0;

        for (Ticket ticket : tickets) {
            if (ticket.getStatus() == TicketStatus.ACTIVE) {
                activeTickets++;
            }
        }

        System.out.println();
        System.out.println("========== RESULT ==========");
        System.out.println("Success        : " + success);
        System.out.println("Failed         : " + failed);
        System.out.println("Total tickets  : " + tickets.size());
        System.out.println("Active tickets : " + activeTickets);
        System.out.println("============================");

        if (success == 1 && activeTickets == 1) {
            System.out.println(
                    "PASS: Only ONE active ticket was created."
            );
        } else {
            System.out.println(
                    "FAIL: Duplicate active tickets detected."
            );
        }
    }
}