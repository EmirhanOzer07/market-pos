package com.market.pos.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Geçersiz kılınmış JWT token'larını bellekte tutan kara liste.
 *
 * <p>Çıkış yapılan token'lar bu sınıfa eklenir ve her istekte {@link #gecersizMi(String)}
 * ile kontrol edilir. Süresi dolmuş token'lar {@code jwt.expiration} değerine göre
 * periyodik olarak otomatik temizlenir.</p>
 */
@Component
public class TokenKaraListesi {

    private final Map<String, Long> gecersizTokenlar = new ConcurrentHashMap<>();

    @Value("${jwt.expiration}")
    private long tokenOmru;

    /**
     * Token'ı kara listeye ekler ve süresi dolmuş kayıtları temizler.
     *
     * @param token geçersiz kılınacak JWT token
     */
    public void ekle(String token) {
        temizle();
        gecersizTokenlar.put(token, System.currentTimeMillis());
    }

    /**
     * Token'ın kara listede olup olmadığını kontrol eder.
     *
     * @param token kontrol edilecek JWT token
     * @return kara listede ise {@code true}
     */
    public boolean gecersizMi(String token) {
        return gecersizTokenlar.containsKey(token);
    }

    private void temizle() {
        long simdi = System.currentTimeMillis();
        gecersizTokenlar.entrySet()
                .removeIf(giris -> (simdi - giris.getValue()) > tokenOmru);
    }

    /** Saatlik periyodik temizleme — süresi dolmuş token'ların bellekte birikmesini önler. */
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
