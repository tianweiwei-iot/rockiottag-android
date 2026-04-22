package com.rockiot.tag.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import java.util.Date;

public class JwtUtil {
    private static final String SECRET = "RockiotTagSecretKey2026RockiotTagSecretKey2026ExtraKey";
    private static final SecretKey SECRET_KEY = Keys.hmacShaKeyFor(SECRET.getBytes());
    private static final long EXPIRATION_TIME = 86400000;

    public static String generateToken(int userId) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(SECRET_KEY)
                .compact();
    }

    public static int getUserIdFromToken(String token) {
        return Integer.parseInt(Jwts.parser()
                .verifyWith(SECRET_KEY)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject());
    }
}