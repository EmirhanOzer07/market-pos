package com.market.pos.security;

import com.market.pos.entity.GecersizToken;
import com.market.pos.repository.GecersizTokenRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Geçersiz kılınmış JWT token'larını hibrit bellek+DB ile tutan kara liste.
 *
 * <p>Okuma (gecersizMi): O(1) — bellek ConcurrentHashMap'ten, DB'ye gitmiyor.
 * Yazma  (ekle):         belleğe + DB'ye (kalıcılık için).
 * Başlangıç:             DB'deki geçerli token'lar belleğe yüklenir (restart güvencesi).</p>
 */
@Component
public class TokenKaraListesi {

    private static final Logger log = LoggerFactory.getLogger(TokenKaraListesi.class);

    @Autowired
    private GecersizTokenRepository repository;

    @Value("${jwt.expiration}")
    private long tokenOmru;

    /** token → eklenmeSuresi (ms). Tüm kontroller buradan, DB'ye gitmiyor. */
    private final ConcurrentHashMap<String, Long> bellek = new ConcurrentHashMap<>();

    /** Başlangıçta DB'deki geçerli (süresi dolmamış) token'ları belleğe yükle. */
    @PostConstruct
    public void bellekIsinahndir() {
        long esik = System.currentTimeMillis() - tokenOmru;
        repository.findAll().forEach(t -> {
            if (t.getEklenmeSuresi() >= esik) {
                bellek.put(t.getToken(), t.getEklenmeSuresi());
            }
        });
        log.info("[TOKEN] Bellek ısındırıldı: {} aktif geçersiz token yüklendi", bellek.size());
    }

    /**
     * Token'ı kara listeye ekler.
     *
     * @param token geçersiz kılınacak JWT token
     */
    public void ekle(String token) {
        long su = System.currentTimeMillis();
        // DB'ye önce yaz — yeniden başlatmada kalıcılık güvencesi
        if (!repository.existsByToken(token)) {
            GecersizToken gecersiz = new GecersizToken();
            gecersiz.setToken(token);
            gecersiz.setEklenmeSuresi(su);
            repository.save(gecersiz);
        }
        // Sonra belleğe al — hızlı O(1) kontroller için
        bellek.put(token, su);
    }

    /**
     * Token'ın kara listede olup olmadığını kontrol eder — O(1), DB'ye gitmiyor.
     *
     * @param token kontrol edilecek JWT token
     * @return kara listede ise {@code true}
     */
    public boolean gecersizMi(String token) {
        return bellek.containsKey(token);
    }

    /** Saatlik periyodik temizleme — süresi dolmuş token'ları bellekten ve DB'den siler. */
    @Scheduled(fixedDelay = 3_600_000)
    public void periyodikTemizle() {
        long esik = System.currentTimeMillis() - tokenOmru;
        // Bellekten temizle
        int onceki = bellek.size();
        bellek.entrySet().removeIf(e -> e.getValue() < esik);
        int silinen = onceki - bellek.size();
        // DB'den temizle
        repository.deleteByEklenmeSuresiLessThan(esik);
        if (silinen > 0) {
            log.info("[TOKEN] Periyodik temizleme: {} süresi dolmuş token silindi", silinen);
        }
    }
}
