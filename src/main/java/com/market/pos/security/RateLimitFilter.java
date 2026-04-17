package com.market.pos.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IP başına istek hızını sınırlayan filtre (Bucket4j tabanlı token bucket algoritması).
 *
 * <p>Her IP adresi için ayrı bir kova tutulur; kota aşılınca {@code 429 Too Many Requests}
 * döner. Sadece giriş, kayıt ve davetiye üretme uç noktalarına uygulanır.</p>
 */
@Component
@Profile("!test")
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int DAKIKA_BASI_ISTEK = 10;
    private static final long KOVA_TEMIZLEME_SURESI_MS = 3_600_000L;

    private final Map<String, Bucket> kovalar = new ConcurrentHashMap<>();
    private final Map<String, Long> sonErisim = new ConcurrentHashMap<>();

    private Bucket kovaOlustur() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(DAKIKA_BASI_ISTEK)
                .refillGreedy(DAKIKA_BASI_ISTEK, Duration.ofMinutes(1))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private void eskiKovalariTemizle() {
        long simdi = System.currentTimeMillis();
        sonErisim.entrySet().removeIf(e -> {
            if (simdi - e.getValue() > KOVA_TEMIZLEME_SURESI_MS) {
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
                uri.equals("/api/superadmin/davetiye-uret") ||
                uri.equals("/api/superadmin/dogrula");

        if (!isProtectedUri) {
            filterChain.doFilter(request, response);
            return;
        }

        eskiKovalariTemizle();

        // X-Forwarded-For kullanılmıyor — header sahteciliğiyle rate limit atlanmasını önler
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
