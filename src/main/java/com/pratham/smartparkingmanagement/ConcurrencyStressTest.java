package com.pratham.smartparkingmanagement;

import com.pratham.smartparkingmanagement.dao.*;
import com.pratham.smartparkingmanagement.model.entities.Ticket;
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
                new ParkingEntryService(
                        vehicleDao,
                        slotDao,
                        ticketDao
                );

        int numberOfRequests = 20;

        ExecutorService executor =
                Executors.newFixedThreadPool(numberOfRequests);

        CountDownLatch ready =
                new CountDownLatch(numberOfRequests);

        CountDownLatch start =
                new CountDownLatch(1);

        List<Future<Ticket>> results =
                new ArrayList<>();

        System.out.println("========================================");
        System.out.println("       CONCURRENCY STRESS TEST");
        System.out.println("========================================");

        System.out.println(
                numberOfRequests + " requests ready..."
        );

        for (int i = 0; i < numberOfRequests; i++) {

            final int requestNumber = i;

            results.add(
                    executor.submit(() -> {

                        ready.countDown();

                        // Wait until every thread is ready
                        start.await();

                        String plate =
                                "CONCURRENCY" + requestNumber;

                        return entryService.parkVehicle(
                                plate,
                                VehicleType.FOUR_WHEELER,
                                2,
                                1
                        );
                    })
            );
        }

        // Make sure all threads have reached the starting point
        ready.await();

        System.out.println("Starting concurrency test...");

        // Release all threads at approximately the same time
        start.countDown();

        int success = 0;
        int fail = 0;

        for (int i = 0; i < results.size(); i++) {

            try {

                Future<Ticket> future = results.get(i);

                Ticket ticket =
                        future.get(10, TimeUnit.SECONDS);

                success++;

                System.out.println(
                        "Request " + i +
                        " SUCCESS - Ticket " +
                        ticket.getTicketId() +
                        " - Slot " +
                        ticket.getSlotId()
                );

            } catch (Exception e) {

                fail++;

                System.out.println(
                        "Request " + i +
                        " FAILED"
                );
            }
        }

        executor.shutdown();

        executor.awaitTermination(
                10,
                TimeUnit.SECONDS
        );

        System.out.println();
        System.out.println("========== RESULT ==========");

        System.out.println("Success: " + success);
        System.out.println("Fail:    " + fail);

        System.out.println("=============================");

        System.out.println();
        System.out.println(
                "Concurrency test completed."
        );
    }
}