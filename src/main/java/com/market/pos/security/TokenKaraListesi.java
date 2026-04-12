package com.market.pos.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TokenKaraListesi {

    private final Map<String, Long> gecersizTokenlar = new ConcurrentHashMap<>();

    
    // Artık jwt süresi değişirse burası da otomatik güncellenir
    @Value("${jwt.expiration}")
    private long tokenOmru;

    public void ekle(String token) {
        temizle();
        gecersizTokenlar.put(token, System.currentTimeMillis());
    }

    public boolean gecersizMi(String token) {
        return gecersizTokenlar.containsKey(token);
    }

    private void temizle() {
        long simdi = System.currentTimeMillis();
        gecersizTokenlar.entrySet()
                .removeIf(giris -> (simdi - giris.getValue()) > tokenOmru);
    }

    // Her saat başı otomatik temizle — logout olmayan oturumların süresi dolan
    // tokenları bellekte birikmeden periyodik olarak silinir
    @Scheduled(fixedDelay = 3_600_000)
    public void periyodikTemizle() {
        int onceki = gecersizTokenlar.size();
        temizle();
        int silinen = onceki - gecersizTokenlar.size();
        if (silinen > 0) {
            org.slf4j.LoggerFactory.getLogger(TokenKaraListesi.class)
                    .info("[TOKEN] Periyodik temizleme: {} süresi dolmuş token silindi", silinen);
        }
    }
}