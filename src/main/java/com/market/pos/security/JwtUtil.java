package com.market.pos.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT token üretimi ve doğrulaması için yardımcı sınıf.
 *
 * <p>Token içine kullanıcı adı, rol ve market ID eklenir; imzalama HMAC-SHA256 ile yapılır.
 * Gizli anahtar {@code application.properties} dosyasındaki {@code jwt.secret} değerinden okunur.</p>
 */
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String gizliMetin;

    @Value("${jwt.expiration}")
    private long expirationTime;

    private SecretKey secretKey;

    @PostConstruct
    public void init() {
        this.secretKey = Keys.hmacShaKeyFor(gizliMetin.getBytes(StandardCharsets.UTF_8));
    }

    // Token oluştur
    public String tokenOlustur(String kullaniciAdi, String rol, Long marketId) {
        return Jwts.builder()
                .subject(kullaniciAdi)
                .claim("rol", rol)
                .claim("marketId", marketId)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expirationTime))
                .signWith(secretKey)
                .compact();
    }

    // Token içini oku
    public Claims tokenCoz(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // Token geçerli mi?
    public boolean tokenGecerliMi(String token) {
        try {
            tokenCoz(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
