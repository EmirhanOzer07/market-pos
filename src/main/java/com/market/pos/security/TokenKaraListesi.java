package com.market.pos.security;

import com.market.pos.entity.GecersizToken;
import com.market.pos.repository.GecersizTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Geçersiz kılınmış JWT token'larını veritabanında kalıcı olarak tutan kara liste.
 *
 * <p>Uygulama yeniden başlatılsa bile çıkış yapılmış token'lar geçerli sayılmaz.
 * Süresi dolmuş kayıtlar saatlik periyodik temizleme ile otomatik silinir.</p>
 */
@Component
public class TokenKaraListesi {

    private static final Logger log = LoggerFactory.getLogger(TokenKaraListesi.class);

    @Autowired
    private GecersizTokenRepository repository;

    @Value("${jwt.expiration}")
    private long tokenOmru;

    /**
     * Token'ı kara listeye ekler ve süresi dolmuş kayıtları temizler.
     *
     * @param token geçersiz kılınacak JWT token
     */
    public void ekle(String token) {
        if (!repository.existsByToken(token)) {
            GecersizToken gecersiz = new GecersizToken();
            gecersiz.setToken(token);
            gecersiz.setEklenmeSuresi(System.currentTimeMillis());
            repository.save(gecersiz);
        }
        temizle();
    }

    /**
     * Token'ın kara listede olup olmadığını kontrol eder.
     *
     * @param token kontrol edilecek JWT token
     * @return kara listede ise {@code true}
     */
    public boolean gecersizMi(String token) {
        return repository.existsByToken(token);
    }

    private void temizle() {
        long esik = System.currentTimeMillis() - tokenOmru;
        repository.deleteByEklenmeSuresiLessThan(esik);
    }

    /** Saatlik periyodik temizleme — süresi dolmuş token'ların veritabanında birikmesini önler. */
    @Scheduled(fixedDelay = 3_600_000)
    public void periyodikTemizle() {
        long onceki = repository.count();
        temizle();
        long sonra = repository.count();
        long silinen = onceki - sonra;
        if (silinen > 0) {
            log.info("[TOKEN] Periyodik temizleme: {} süresi dolmuş token silindi", silinen);
        }
    }
}
