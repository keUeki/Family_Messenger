package com.shanyangcode.common.utils;

import java.security.Key;
import java.util.Date;
import java.util.concurrent.TimeUnit;


import com.shanyangcode.common.constant.CommonConstant;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * JWT helper (detailed exception logging, centralised expiry configuration)
 */
@Slf4j
public final class JwtUtil {

    /**
     * Generates a JWT with a custom lifetime
     */
    public static String generate(String userId, long timeout, TimeUnit unit) {
        Date now = new Date();
        // Set the expiry
        Date expiration = new Date(now.getTime() + unit.toMillis(timeout));
        System.out.println(expiration);
        return Jwts.builder()
                .setSubject(userId) // Stores the unique user identifier (phone number or user id)
                .setIssuedAt(now) // Issued-at time
                .setExpiration(expiration) // The JWT's own expiry is deliberately longer
                .signWith(getSignInKey(), SignatureAlgorithm.HS256) // Signing algorithm
                .compact();
    }

    /**
     * Returns the signing key, read from the constants class instead of being hard-coded
     */
    public static Key getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(CommonConstant.TOKEN_SECRET_KEY);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Parses a JWT, distinguishing the failure modes to ease troubleshooting
     */
    public static Claims parse(String token) {
        if (StringUtils.isEmpty(token)) {
            log.warn("Failed to parse JWT: token is empty");
            return null;
        }
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(getSignInKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (ExpiredJwtException e) {
            log.warn("JWT has expired: {}", e.getMessage());
            return null;
        } catch (JwtException e) {
            log.error("JWT signature is invalid or malformed: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("Unexpected error while parsing the JWT: ", e);
            return null;
        }
    }


}