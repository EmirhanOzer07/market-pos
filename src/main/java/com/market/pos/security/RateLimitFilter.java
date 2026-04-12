package com.market.pos.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final Map<String, Bucket> kovalar = new ConcurrentHashMap<>();
    private final Map<String, Long> sonErisim = new ConcurrentHashMap<>();

    private static final long TEMIZLEME_SURESI = 3_600_000L;

    
    private Bucket kovaOlustur() {
        // Bucket4j 8.x API
        Bandwidth limit = Bandwidth.builder()
                .capacity(10)
                .refillGreedy(10, Duration.ofMinutes(1))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private void eskiKovalariTemizle() {
        long simdi = System.currentTimeMillis();
        sonErisim.entrySet().removeIf(e -> {
            if (simdi - e.getValue() > TEMIZLEME_SURESI) {
                kovalar.remove(e.getKey());
                return true;
            }
            return false;
        });
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String uri = request.getRequestURI();

        boolean isProtectedUri = uri.equals("/api/auth/giris") ||
                uri.equals("/api/auth/kayit") ||
                uri.equals("/api/superadmin/davetiye-uret");

        if (!isProtectedUri) {
            filterChain.doFilter(request, response);
            return;
        }

        eskiKovalariTemizle();

        // X-Forwarded-For kullanılmıyor — header sahteciliğiyle rate limit atlanmasını önler
        // Saldırgan artık header sahteciliğiyle rate limit'i atlayamaz
        String ip = request.getRemoteAddr();

        sonErisim.put(ip, System.currentTimeMillis());
        Bucket kova = kovalar.computeIfAbsent(ip, k -> kovaOlustur());

        if (kova.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"hata\":\"Çok fazla giriş denemesi. 1 dakika bekleyin.\"}");
        }
    }
}