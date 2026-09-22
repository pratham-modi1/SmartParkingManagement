package com.pratham.smartparkingmanagement;

import com.google.gson.Gson;
import com.pratham.smartparkingmanagement.api.ApiResponse;
import com.pratham.smartparkingmanagement.api.ResponseUtil;
import com.pratham.smartparkingmanagement.api.Router;
import com.pratham.smartparkingmanagement.config.DatabaseProperties;
import com.pratham.smartparkingmanagement.dao.PaymentDao;
import com.pratham.smartparkingmanagement.dao.PaymentDaoImpl;
import com.pratham.smartparkingmanagement.dao.ParkingLotDao;
import com.pratham.smartparkingmanagement.dao.ParkingLotDaoImpl;
import com.pratham.smartparkingmanagement.dao.PricingConfigDao;
import com.pratham.smartparkingmanagement.dao.PricingConfigDaoImpl;
import com.pratham.smartparkingmanagement.dao.SlotDao;
import com.pratham.smartparkingmanagement.dao.SlotDaoImpl;
import com.pratham.smartparkingmanagement.dao.TicketDao;
import com.pratham.smartparkingmanagement.dao.TicketDaoImpl;
import com.pratham.smartparkingmanagement.dao.UserDao;
import com.pratham.smartparkingmanagement.dao.UserDaoImpl;
import com.pratham.smartparkingmanagement.dao.VehicleDao;
import com.pratham.smartparkingmanagement.dao.VehicleDaoImpl;

import com.pratham.smartparkingmanagement.dto.EntryRequest;
import com.pratham.smartparkingmanagement.dto.ExitResponse;
import com.pratham.smartparkingmanagement.dto.LoginRequest;
import com.pratham.smartparkingmanagement.dto.ParkingLotRequest;
import com.pratham.smartparkingmanagement.dto.RegisterRequest;
import com.pratham.smartparkingmanagement.dto.SlotRequest;
import com.pratham.smartparkingmanagement.dto.UserResponse;
import com.pratham.smartparkingmanagement.model.entities.ParkingLot;
import com.pratham.smartparkingmanagement.model.entities.Slot;
import com.pratham.smartparkingmanagement.model.entities.Ticket;

import com.pratham.smartparkingmanagement.model.enums.LotStatus;
import com.pratham.smartparkingmanagement.model.enums.SlotStatus;
import com.pratham.smartparkingmanagement.model.enums.VehicleType;
import com.pratham.smartparkingmanagement.security.JwtUtil;
import com.pratham.smartparkingmanagement.service.AuthService;
import com.pratham.smartparkingmanagement.service.ParkingEntryService;
import com.pratham.smartparkingmanagement.service.ParkingExitService;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mindrot.jbcrypt.BCrypt;
//seed admin creds : admin@parking.com  password is password
public class Main {

    public static void main(String[] args) throws IOException {

        // =====================================================
        // SERVER
        // =====================================================

        HttpServer server = HttpServer.create(
                new InetSocketAddress(8080),
                0
        );

        Router router = new Router();

        Gson gson = new Gson();

        JwtUtil jwtUtil = new JwtUtil(
                DatabaseProperties.getJwtSecret(),
                DatabaseProperties.getJwtExpiryMinutes()
        );

        // =====================================================
        // DAOs
        // =====================================================

        ParkingLotDao parkingLotDao =
                new ParkingLotDaoImpl();

        SlotDao slotDao =
                new SlotDaoImpl();

        VehicleDao vehicleDao =
                new VehicleDaoImpl();

        TicketDao ticketDao =
                new TicketDaoImpl();

        PaymentDao paymentDao =
                new PaymentDaoImpl();

        PricingConfigDao pricingConfigDao =
                new PricingConfigDaoImpl();

        UserDao userDao = new UserDaoImpl();

        AuthService authService = new AuthService(userDao, jwtUtil);


        // =====================================================
        // SERVICES
        // =====================================================

        ParkingEntryService parkingEntryService =
                new ParkingEntryService(
                        vehicleDao,
                        slotDao,
                        ticketDao
                );

        ParkingExitService parkingExitService =
                new ParkingExitService(
                        slotDao,
                        ticketDao,
                        paymentDao,
                        pricingConfigDao
                );

        router.addRoute(
        "POST",
        "/auth/register",
        exchange -> {

            try {

                String body = new String(
                        exchange.getRequestBody().readAllBytes(),
                        StandardCharsets.UTF_8
                );

                RegisterRequest request =
                        gson.fromJson(body, RegisterRequest.class);

                UserResponse userResponse =
                        authService.register(
                                request.getName(),
                                request.getEmail(),
                                request.getPassword(),
                                request.getRole()
                        );

                ResponseUtil.sendJson(
                        exchange,
                        201,
                        ApiResponse.success(userResponse)
                );

            } catch (Exception e) {

                ResponseUtil.sendJson(
                        exchange,
                        400,
                        ApiResponse.failure(e.getMessage())
                );
            }
        }
);


        // =====================================================
        // 1. GET TICKET BY ID
        // =====================================================

        router.addRoute(
                "GET",
                "/tickets/{id}",
                exchange -> {

                    try {

                        String ticketId =
                                (String) exchange.getAttribute("id");

                        ResponseUtil.sendJson(
                                exchange,
                                200,
                                ApiResponse.success(
                                        "Ticket ID = " + ticketId
                                )
                        );

                    } catch (Exception e) {

                        ResponseUtil.sendJson(
                                exchange,
                                400,
                                ApiResponse.failure(
                                        e.getMessage()
                                )
                        );
                    }
                }
        );


                router.addRoute(
                "POST",
                "/auth/login",
                exchange -> {

                try {

                        String body = new String(
                                exchange.getRequestBody().readAllBytes(),
                                StandardCharsets.UTF_8
                        );

                        LoginRequest request =
                                gson.fromJson(body, LoginRequest.class);

                        String token =
                                authService.login(
                                        request.getEmail(),
                                        request.getPassword()
                                );

                        ResponseUtil.sendJson(
                                exchange,
                                200,
                                ApiResponse.success(token)
                        );

                } catch (Exception e) {

                        ResponseUtil.sendJson(
                                exchange,
                                401,
                                ApiResponse.failure(e.getMessage())
                        );
                }
                }
        );


        // =====================================================
        // 2. GET ALL TICKETS OF A VEHICLE
        // =====================================================

        router.addRoute(
                "GET",
                "/vehicles/{vehicleId}/tickets",
                exchange -> {

                    try {

                        String vehicleIdString =
                                (String) exchange.getAttribute(
                                        "vehicleId"
                                );

                        int vehicleId =
                                Integer.parseInt(
                                        vehicleIdString
                                );

                        List<Ticket> tickets =
                                ticketDao.findByVehicleId(
                                        vehicleId
                                );

                        ResponseUtil.sendJson(
                                exchange,
                                200,
                                ApiResponse.success(
                                        tickets
                                )
                        );

                    } catch (Exception e) {

                        ResponseUtil.sendJson(
                                exchange,
                                400,
                                ApiResponse.failure(
                                        e.getMessage()
                                )
                        );
                    }
                }
        );


        // =====================================================
        // 3. GET ALL PARKING LOTS
        // =====================================================

        router.addRoute(
                "GET",
                "/parking-lots",
                exchange -> {

                    try {

                        List<ParkingLot> parkingLots =
                                parkingLotDao.findAll();

                        ResponseUtil.sendJson(
                                exchange,
                                200,
                                ApiResponse.success(
                                        parkingLots
                                )
                        );

                    } catch (Exception e) {

                        ResponseUtil.sendJson(
                                exchange,
                                500,
                                ApiResponse.failure(
                                        e.getMessage()
                                )
                        );
                    }
                }
        );


        // =====================================================
        // 4. CREATE PARKING LOT
        // =====================================================

        router.addRoute(
                "POST",
                "/parking-lots",
                exchange -> {

                    try {

                        String body =
                                new String(
                                        exchange.getRequestBody()
                                                .readAllBytes(),
                                        StandardCharsets.UTF_8
                                );

                        ParkingLotRequest request =
                                gson.fromJson(
                                        body,
                                        ParkingLotRequest.class
                                );

                        ParkingLot parkingLot =
                                new ParkingLot(
                                        0,
                                        request.name,
                                        request.location,
                                        LotStatus.ENABLED,
                                        null
                                );

                        parkingLotDao.create(
                                parkingLot
                        );

                        ResponseUtil.sendJson(
                                exchange,
                                201,
                                ApiResponse.success(
                                        parkingLot
                                )
                        );

                    } catch (Exception e) {

                        ResponseUtil.sendJson(
                                exchange,
                                400,
                                ApiResponse.failure(
                                        e.getMessage()
                                )
                        );
                    }
                }
        );


        // =====================================================
        // 5. CREATE SLOT
        // =====================================================

        router.addRoute(
                "POST",
                "/parking-lots/{lotId}/slots",
                exchange -> {

                    try {

                        String lotIdString =
                                (String) exchange.getAttribute(
                                        "lotId"
                                );

                        int lotId =
                                Integer.parseInt(
                                        lotIdString
                                );

                        String body =
                                new String(
                                        exchange.getRequestBody()
                                                .readAllBytes(),
                                        StandardCharsets.UTF_8
                                );

                        SlotRequest request =
                                gson.fromJson(
                                        body,
                                        SlotRequest.class
                                );

                        VehicleType vehicleType =
                                VehicleType.valueOf(
                                        request.vehicleType
                                );

                        Slot slot =
                                new Slot(
                                        0,
                                        request.slotLabel,
                                        vehicleType,
                                        SlotStatus.AVAILABLE,
                                        lotId
                                );

                        slotDao.create(slot);

                        ResponseUtil.sendJson(
                                exchange,
                                201,
                                ApiResponse.success(
                                        slot
                                )
                        );

                    } catch (Exception e) {

                        ResponseUtil.sendJson(
                                exchange,
                                400,
                                ApiResponse.failure(
                                        e.getMessage()
                                )
                        );
                    }
                }
        );


        // =====================================================
        // 6. GET AVAILABLE SLOTS
        // =====================================================

        router.addRoute(
                "GET",
                "/slots/available",
                exchange -> {

                    try {

                        String query =
                                exchange.getRequestURI()
                                        .getQuery();

                        Map<String, String> params =
                                new HashMap<>();

                        if (query != null) {

                            for (String pair :
                                    query.split("&")) {

                                String[] keyValue =
                                        pair.split("=", 2);

                                if (keyValue.length == 2) {

                                    params.put(
                                            keyValue[0],
                                            keyValue[1]
                                    );
                                }
                            }
                        }

                        int lotId =
                                Integer.parseInt(
                                        params.get("lotId")
                                );

                        VehicleType vehicleType =
                                VehicleType.valueOf(
                                        params.get("vehicleType")
                                );

                        List<Slot> slots =
                                slotDao.findAvailableByLotAndType(
                                        lotId,
                                        vehicleType
                                );

                        ResponseUtil.sendJson(
                                exchange,
                                200,
                                ApiResponse.success(
                                        slots
                                )
                        );

                    } catch (Exception e) {

                        ResponseUtil.sendJson(
                                exchange,
                                400,
                                ApiResponse.failure(
                                        e.getMessage()
                                )
                        );
                    }
                }
        );


        // =====================================================
        // 7. VEHICLE ENTRY
        // =====================================================

        router.addRoute(
                "POST",
                "/vehicles/entry",
                exchange -> {

                    try {

                        String body =
                                new String(
                                        exchange.getRequestBody()
                                                .readAllBytes(),
                                        StandardCharsets.UTF_8
                                );

                        EntryRequest request =
                                gson.fromJson(
                                        body,
                                        EntryRequest.class
                                );

                        VehicleType vehicleType =
                                VehicleType.valueOf(
                                        request.vehicleType
                                );

                        Ticket ticket =
                                parkingEntryService.parkVehicle(
                                        request.plateNumber,
                                        vehicleType,
                                        request.lotId,
                                        1
                                );

                        ResponseUtil.sendJson(
                                exchange,
                                200,
                                ApiResponse.success(
                                        ticket
                                )
                        );

                    } catch (Exception e) {

                        ResponseUtil.sendJson(
                                exchange,
                                400,
                                ApiResponse.failure(
                                        e.getMessage()
                                )
                        );
                    }
                }
        );


        // =====================================================
        // 8. VEHICLE EXIT
        // =====================================================

        router.addRoute(
                "POST",
                "/vehicles/exit/{ticketId}",
                exchange -> {

                    try {

                        String ticketIdString =
                                (String) exchange.getAttribute(
                                        "ticketId"
                                );

                        int ticketId =
                                Integer.parseInt(
                                        ticketIdString
                                );

                        String query =
                                exchange.getRequestURI()
                                        .getQuery();

                        boolean lostTicket = false;

                        if (query != null) {

                            Map<String, String> params =
                                    new HashMap<>();

                            for (String pair :
                                    query.split("&")) {

                                String[] keyValue =
                                        pair.split("=", 2);

                                if (keyValue.length == 2) {

                                    params.put(
                                            keyValue[0],
                                            keyValue[1]
                                    );
                                }
                            }

                            lostTicket =
                                    Boolean.parseBoolean(
                                            params.getOrDefault(
                                                    "lostTicket",
                                                    "false"
                                            )
                                    );
                        }

                        ExitResponse exitResponse =
                                parkingExitService.exitVehicle(
                                        ticketId,
                                        lostTicket
                                );

                        ResponseUtil.sendJson(
                                exchange,
                                200,
                                ApiResponse.success(
                                        exitResponse
                                )
                        );

                    } catch (Exception e) {

                        ResponseUtil.sendJson(
                                exchange,
                                400,
                                ApiResponse.failure(
                                        e.getMessage()
                                )
                        );
                    }
                }
        );


        // =====================================================
        // CONNECT ROUTER TO SERVER
        // =====================================================

        server.createContext(
                "/",
                router
        );

        server.start();

        System.out.println(
                "Server started on port 8080"
        );
    }
}