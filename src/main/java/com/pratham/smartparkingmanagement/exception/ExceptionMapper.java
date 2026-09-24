package com.pratham.smartparkingmanagement.exception;

import com.pratham.smartparkingmanagement.api.ApiResponse;
import com.pratham.smartparkingmanagement.api.ResponseUtil;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;

public class ExceptionMapper {

    public static void handle(
            HttpExchange exchange,
            Exception e
    ) throws IOException {

        int statusCode;

        if (e instanceof ValidationException) {
            statusCode = 400;

        } else if (e instanceof NotFoundException) {
            statusCode = 404;

        } else if (e instanceof ConflictException) {
            statusCode = 409;

        } else if (e instanceof UnauthorizedException) {
            statusCode = 401;

        } else {
            statusCode = 500;
        }

        ResponseUtil.sendJson(
                exchange,
                statusCode,
                ApiResponse.failure(e.getMessage())
        );
    }
}