package com.market.pos.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

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

    private Key secretKey;

    // @PostConstruct: Spring bean başlatıldıktan sonra JWT imzalama anahtarını hazırla
    @PostConstruct
    public void init() {
        this.secretKey = Keys.hmacShaKeyFor(gizliMetin.getBytes(StandardCharsets.UTF_8));
    }

    // Token oluştur
    public String tokenOlustur(String kullaniciAdi, String rol, Long marketId) {
        Map<String, Object> extraBilgiler = new HashMap<>();
        extraBilgiler.put("rol", rol);
        extraBilgiler.put("marketId", marketId);

        return Jwts.builder()
                .setClaims(extraBilgiler)
                .setSubject(kullaniciAdi)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expirationTime))
                .signWith(secretKey)
                .compact();
    }

    // Token içini oku
    public Claims tokenCoz(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
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