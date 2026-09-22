package com.pratham.smartparkingmanagement.api;


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

public class ResponseUtil {

    private static final Gson gson = new GsonBuilder()
        .registerTypeAdapter(
                LocalDateTime.class,
                (JsonSerializer<LocalDateTime>) (src, typeOfSrc, context) ->
                        new JsonPrimitive(src.toString())
        )
        .create();

    public static void sendJson(
            HttpExchange exchange,
            int statusCode,
            ApiResponse response
    ) throws IOException {

        String json = gson.toJson(response);

        byte[] responseBytes = json.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders()
                .set("Content-Type", "application/json");

        exchange.sendResponseHeaders(
                statusCode,
                responseBytes.length
        );

        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(responseBytes);
        }
    }
}