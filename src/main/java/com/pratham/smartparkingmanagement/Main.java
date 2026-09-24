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
import com.pratham.smartparkingmanagement.model.entities.Vehicle;

import com.pratham.smartparkingmanagement.model.enums.LotStatus;
import com.pratham.smartparkingmanagement.model.enums.SlotStatus;
import com.pratham.smartparkingmanagement.model.enums.VehicleType;
import com.pratham.smartparkingmanagement.security.AuthFilter;
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

    // =====================================================
    // DAY 8 - STEP 2: VALIDATION HELPERS
    // (kept in Main.java only, no new classes/framework)
    // =====================================================

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static Integer parsePositiveInt(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed <= 0) {
                return null;
            }
            return parsed;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static VehicleType parseVehicleType(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return VehicleType.valueOf(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

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

        AuthFilter authFilter = new AuthFilter(jwtUtil);

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

        // =====================================================
        // PUBLIC — NO AUTH
        // =====================================================

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

                // ---- Day 8 Step 2: validation ----

                if (isBlank(request.getName())) {
                    ResponseUtil.sendJson(
                            exchange,
                            400,
                            ApiResponse.failure("name is required")
                    );
                    return;
                }

                if (isBlank(request.getEmail())) {
                    ResponseUtil.sendJson(
                            exchange,
                            400,
                            ApiResponse.failure("email is required")
                    );
                    return;
                }

                if (isBlank(request.getPassword())) {
                    ResponseUtil.sendJson(
                            exchange,
                            400,
                            ApiResponse.failure("password is required")
                    );
                    return;
                }

                if (request.getRole() == null) {
                    ResponseUtil.sendJson(
                            exchange,
                            400,
                            ApiResponse.failure("role is required")
                    );
                    return;
                }

                if ("ADMIN".equalsIgnoreCase(
                        request.getRole().toString())) {

                    ResponseUtil.sendJson(
                            exchange,
                            400,
                            ApiResponse.failure(
                                    "Public registration cannot create an ADMIN account"
                            )
                    );
                    return;
                }

                // ---- end validation ----

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
                authFilter.requireAuth(
                exchange -> {

                    try {

                        String ticketIdRaw =
                                (String) exchange.getAttribute("id");

                        // ---- Day 8 Step 2: validation ----

                        Integer id = parsePositiveInt(ticketIdRaw);

                        if (id == null) {
                            ResponseUtil.sendJson(
                                    exchange,
                                    400,
                                    ApiResponse.failure(
                                            "id must be a valid positive integer"
                                    )
                            );
                            return;
                        }

                        // ---- end validation ----

                        ResponseUtil.sendJson(
                                exchange,
                                200,
                                ApiResponse.success(
                                        "Ticket ID = " + id
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
                )
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

                        // ---- Day 8 Step 2: validation (400, not 401) ----

                        if (isBlank(request.getEmail())) {
                            ResponseUtil.sendJson(
                                    exchange,
                                    400,
                                    ApiResponse.failure("email is required")
                            );
                            return;
                        }

                        if (isBlank(request.getPassword())) {
                            ResponseUtil.sendJson(
                                    exchange,
                                    400,
                                    ApiResponse.failure("password is required")
                            );
                            return;
                        }

                        // ---- end validation ----

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
                authFilter.requireAuth(
                exchange -> {

                    try {

                        String vehicleIdString =
                                (String) exchange.getAttribute(
                                        "vehicleId"
                                );

                        // ---- Day 8 Step 2: validation ----

                        Integer vehicleId = parsePositiveInt(vehicleIdString);

                        if (vehicleId == null) {
                            ResponseUtil.sendJson(
                                    exchange,
                                    400,
                                    ApiResponse.failure(
                                            "vehicleId must be a valid positive integer"
                                    )
                            );
                            return;
                        }

                        // ---- end validation ----

                        String role =
                                (String) exchange.getAttribute("role");

                        if ("CUSTOMER".equals(role)) {

                            int userId =
                                    (int) exchange.getAttribute("userId");

                            Vehicle vehicle =
                                    vehicleDao.findById(vehicleId);

                            if (vehicle == null
                                    || vehicle.getOwnerId() != userId) {

                                ResponseUtil.sendJson(
                                        exchange,
                                        403,
                                        ApiResponse.failure("Forbidden")
                                );
                                return;
                            }
                        }

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
                )
        );


        // =====================================================
        // 3. GET ALL PARKING LOTS
        // =====================================================

        router.addRoute(
                "GET",
                "/parking-lots",
                authFilter.requireAuth(
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
                )
        );


        // =====================================================
        // 4. CREATE PARKING LOT
        // =====================================================

        router.addRoute(
                "POST",
                "/parking-lots",
                authFilter.requireAuth(
                authFilter.requireRole(
                "ADMIN",
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

                        // ---- Day 8 Step 2: validation ----

                        if (isBlank(request.name)) {
                            ResponseUtil.sendJson(
                                    exchange,
                                    400,
                                    ApiResponse.failure("name is required")
                            );
                            return;
                        }

                        if (isBlank(request.location)) {
                            ResponseUtil.sendJson(
                                    exchange,
                                    400,
                                    ApiResponse.failure("location is required")
                            );
                            return;
                        }

                        // ---- end validation ----

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
                )
                )
        );


        // =====================================================
        // 5. CREATE SLOT
        // =====================================================

        router.addRoute(
                "POST",
                "/parking-lots/{lotId}/slots",
                authFilter.requireAuth(
                authFilter.requireRole(
                "ADMIN",
                exchange -> {

                    try {

                        String lotIdString =
                                (String) exchange.getAttribute(
                                        "lotId"
                                );

                        // ---- Day 8 Step 2: validation ----

                        Integer lotId = parsePositiveInt(lotIdString);

                        if (lotId == null) {
                            ResponseUtil.sendJson(
                                    exchange,
                                    400,
                                    ApiResponse.failure(
                                            "lotId must be a valid positive integer"
                                    )
                            );
                            return;
                        }

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

                        if (isBlank(request.slotLabel)) {
                            ResponseUtil.sendJson(
                                    exchange,
                                    400,
                                    ApiResponse.failure("slotLabel is required")
                            );
                            return;
                        }

                        if (isBlank(request.vehicleType)) {
                            ResponseUtil.sendJson(
                                    exchange,
                                    400,
                                    ApiResponse.failure("vehicleType is required")
                            );
                            return;
                        }

                        VehicleType vehicleType =
                                parseVehicleType(request.vehicleType);

                        if (vehicleType == null) {
                            ResponseUtil.sendJson(
                                    exchange,
                                    400,
                                    ApiResponse.failure("vehicleType is invalid")
                            );
                            return;
                        }

                        // ---- end validation ----

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
                )
                )
        );


        // =====================================================
        // 6. GET AVAILABLE SLOTS
        // =====================================================

        router.addRoute(
                "GET",
                "/slots/available",
                authFilter.requireAuth(
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

                        // ---- Day 8 Step 2: validation ----

                        Integer lotId = parsePositiveInt(params.get("lotId"));

                        if (lotId == null) {
                            ResponseUtil.sendJson(
                                    exchange,
                                    400,
                                    ApiResponse.failure(
                                            "lotId must be a valid positive integer"
                                    )
                            );
                            return;
                        }

                        String vehicleTypeParam = params.get("vehicleType");

                        if (isBlank(vehicleTypeParam)) {
                            ResponseUtil.sendJson(
                                    exchange,
                                    400,
                                    ApiResponse.failure("vehicleType is required")
                            );
                            return;
                        }

                        VehicleType vehicleType =
                                parseVehicleType(vehicleTypeParam);

                        if (vehicleType == null) {
                            ResponseUtil.sendJson(
                                    exchange,
                                    400,
                                    ApiResponse.failure("vehicleType is invalid")
                            );
                            return;
                        }

                        // ---- end validation ----

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
                )
        );


        // =====================================================
        // 7. VEHICLE ENTRY
        // =====================================================

        router.addRoute(
                "POST",
                "/vehicles/entry",
                authFilter.requireAuth(
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

                        // ---- Day 8 Step 2: validation ----

                        if (isBlank(request.plateNumber)) {
                            ResponseUtil.sendJson(
                                    exchange,
                                    400,
                                    ApiResponse.failure("plateNumber is required")
                            );
                            return;
                        }

                        if (isBlank(request.vehicleType)) {
                            ResponseUtil.sendJson(
                                    exchange,
                                    400,
                                    ApiResponse.failure("vehicleType is required")
                            );
                            return;
                        }

                        VehicleType vehicleType =
                                parseVehicleType(request.vehicleType);

                        if (vehicleType == null) {
                            ResponseUtil.sendJson(
                                    exchange,
                                    400,
                                    ApiResponse.failure("vehicleType is invalid")
                            );
                            return;
                        }

                        if (request.lotId <= 0) {
                            ResponseUtil.sendJson(
                                    exchange,
                                    400,
                                    ApiResponse.failure(
                                            "lotId must be a positive integer"
                                    )
                            );
                            return;
                        }

                        // ---- end validation ----

                        Ticket ticket =
                                parkingEntryService.parkVehicle(
                                        request.plateNumber,
                                        vehicleType,
                                        request.lotId,
                                        (int) exchange.getAttribute("userId")
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
                )
        );


        // =====================================================
        // 8. VEHICLE EXIT
        // =====================================================

        router.addRoute(
                "POST",
                "/vehicles/exit/{ticketId}",
                authFilter.requireAuth(
                exchange -> {

                    try {

                        String ticketIdString =
                                (String) exchange.getAttribute(
                                        "ticketId"
                                );

                        // ---- Day 8 Step 2: validation ----

                        Integer ticketId = parsePositiveInt(ticketIdString);

                        if (ticketId == null) {
                            ResponseUtil.sendJson(
                                    exchange,
                                    400,
                                    ApiResponse.failure(
                                            "ticketId must be a valid positive integer"
                                    )
                            );
                            return;
                        }

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

                            String lostTicketRaw =
                                    params.get("lostTicket");

                            if (lostTicketRaw != null) {

                                if (!"true".equalsIgnoreCase(lostTicketRaw)
                                        && !"false".equalsIgnoreCase(lostTicketRaw)) {

                                    ResponseUtil.sendJson(
                                            exchange,
                                            400,
                                            ApiResponse.failure(
                                                    "lostTicket must be a valid boolean"
                                            )
                                    );
                                    return;
                                }

                                lostTicket =
                                        Boolean.parseBoolean(lostTicketRaw);
                            }
                        }

                        // ---- end validation ----

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
                )
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