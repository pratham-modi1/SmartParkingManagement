package com.pratham.smartparkingmanagement.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Router implements HttpHandler {

    private final List<Route> routes = new ArrayList<>();

    public void addRoute(String method, String path, HttpHandler handler) {
        routes.add(new Route(method, path, handler));
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {

        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        for (Route route : routes) {

            if (!route.method.equalsIgnoreCase(method)) {
                continue;
            }

            Map<String, String> pathParams =
                    matchPath(route.path, path);

            if (pathParams != null) {

                for (Map.Entry<String, String> entry : pathParams.entrySet()) {
                    exchange.setAttribute(entry.getKey(), entry.getValue());
                }

                route.handler.handle(exchange);
                return;
            }
        }

        String response = "404 - Route not found";

        exchange.sendResponseHeaders(404, response.length());

        exchange.getResponseBody().write(response.getBytes());
        exchange.getResponseBody().close();
    }

    private Map<String, String> matchPath(
            String pattern,
            String actualPath) {

        String[] patternParts = pattern.split("/");
        String[] actualParts = actualPath.split("/");

        if (patternParts.length != actualParts.length) {
            return null;
        }

        Map<String, String> pathParams = new HashMap<>();

        for (int i = 0; i < patternParts.length; i++) {

            String patternPart = patternParts[i];
            String actualPart = actualParts[i];

            if (patternPart.startsWith("{")
                    && patternPart.endsWith("}")) {

                String parameterName =
                        patternPart.substring(
                                1,
                                patternPart.length() - 1
                        );

                pathParams.put(parameterName, actualPart);

            } else if (!patternPart.equals(actualPart)) {

                return null;
            }
        }

        return pathParams;
    }

    private static class Route {

        String method;
        String path;
        HttpHandler handler;

        Route(String method, String path, HttpHandler handler) {
            this.method = method;
            this.path = path;
            this.handler = handler;
        }
    }
}