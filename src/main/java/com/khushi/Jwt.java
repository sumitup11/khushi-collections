package com.khushi;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class Jwt {
  private final SecretKey key;

  public Jwt(@Value("${app.jwt.secret}") String secret) throws Exception {
    // any secret length works: it is hashed to a 256-bit key
    key = Keys.hmacShaKeyFor(MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8)));
  }

  public String make(String email) {
    return Jwts.builder().subject(email).issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + 7L * 24 * 3600 * 1000)).signWith(key).compact();
  }

  public String email(String token) {
    try { return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject(); }
    catch (Exception e) { return null; }
  }
}
