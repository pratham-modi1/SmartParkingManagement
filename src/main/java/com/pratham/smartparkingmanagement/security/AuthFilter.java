package com.pratham.smartparkingmanagement.security;

import com.pratham.smartparkingmanagement.api.ApiResponse;
import com.pratham.smartparkingmanagement.api.ResponseUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;

public class AuthFilter {

    private final JwtUtil jwtUtil;

    public AuthFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    public HttpHandler requireAuth(HttpHandler handler) {

        return exchange -> {

            String authHeader =
                    exchange.getRequestHeaders()
                            .getFirst("Authorization");

            if (authHeader == null
                    || !authHeader.startsWith("Bearer ")) {

                ResponseUtil.sendJson(
                        exchange,
                        401,
                        ApiResponse.failure(
                                "Missing or invalid Authorization header"
                        )
                );
                return;
            }

            String token = authHeader.substring(7);

            try {

                jwtUtil.validateToken(token);

                int userId = jwtUtil.extractUserId(token);
                String role = jwtUtil.extractRole(token);

                exchange.setAttribute("userId", userId);
                exchange.setAttribute("role", role);

                handler.handle(exchange);

            } catch (Exception e) {

                ResponseUtil.sendJson(
                        exchange,
                        401,
                        ApiResponse.failure(
                                "Invalid or expired token"
                        )
                );
            }
        };
    }

    public HttpHandler requireRole(
            String requiredRole,
            HttpHandler handler) {

        return exchange -> {

            String role =
                    (String) exchange.getAttribute("role");

            if (role == null
                    || !role.equals(requiredRole)) {

                ResponseUtil.sendJson(
                        exchange,
                        403,
                        ApiResponse.failure("Forbidden")
                );
                return;
            }

            handler.handle(exchange);
        };
    }
}