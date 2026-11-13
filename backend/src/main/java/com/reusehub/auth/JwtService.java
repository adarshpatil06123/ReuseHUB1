package com.reusehub.auth;

import com.reusehub.config.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
  private final JwtProperties jwtProperties;
  private final SecretKey key;

  public JwtService(JwtProperties jwtProperties) {
    this.jwtProperties = jwtProperties;
    this.key = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
  }

  public JwtToken createAccessToken(Long userId, String email) {
    Instant now = Instant.now();
    Instant expiresAt = now.plusSeconds(jwtProperties.getAccessTokenMinutes() * 60L);

    String token = Jwts.builder()
        .issuer(jwtProperties.getIssuer())
        .subject(String.valueOf(userId))
        .claim("email", email)
        .issuedAt(Date.from(now))
        .expiration(Date.from(expiresAt))
        .signWith(key)
        .compact();

    return new JwtToken(token, expiresAt);
  }

  public JwtClaims parse(String token) {
    var claims = Jwts.parser()
        .verifyWith(key)
        .build()
        .parseSignedClaims(token)
        .getPayload();

    Long userId = Long.parseLong(claims.getSubject());
    String email = claims.get("email", String.class);
    return new JwtClaims(userId, email);
  }
}
