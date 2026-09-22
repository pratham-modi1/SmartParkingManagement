package com.pratham.smartparkingmanagement.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.pratham.smartparkingmanagement.model.entities.User;

import java.util.Date;

public class JwtUtil {

    private final String secretKey;
    private final int expiryMinutes;

    public JwtUtil(String secretKey, int expiryMinutes) {
        this.secretKey = secretKey;
        this.expiryMinutes = expiryMinutes;
    }

    public String generateToken(User user) {

        Algorithm algorithm = Algorithm.HMAC256(secretKey);

        long expiryTime =
                System.currentTimeMillis()
                + (expiryMinutes * 60L * 1000L);

        return JWT.create()
                .withClaim("userId", user.getUserId())
                .withClaim("role", user.getRole().name())
                .withClaim("email", user.getEmail())
                .withExpiresAt(new Date(expiryTime))
                .sign(algorithm);
    }


    public void validateToken(String token) {

        Algorithm algorithm = Algorithm.HMAC256(secretKey);

        JWT.require(algorithm)
                .build()
                .verify(token);
    }

    public int extractUserId(String token) {
    return JWT.decode(token)
            .getClaim("userId")
            .asInt();
}

public String extractRole(String token) {
    return JWT.decode(token)
            .getClaim("role")
            .asString();
}

public String extractEmail(String token) {
    return JWT.decode(token)
            .getClaim("email")
            .asString();
}

}