package com.market.pos.service;

import com.market.pos.entity.Kullanici;
import com.market.pos.repository.KullaniciRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * SecurityContext üzerinden aktif kullanıcıyı ve market yetkisini merkezi olarak sağlar.
 *
 * <p>Bu servis önceden {@link com.market.pos.controller.KullaniciController},
 * {@link com.market.pos.controller.UrunController} ve {@link com.market.pos.controller.SatisController}
 * içinde ayrı ayrı tanımlanan {@code getAktifKullanici()} yardımcılarının tek bir noktada
 * toplanmasıyla oluşturulmuştur.</p>
 */
@Service
public class KullaniciGuvenlikServisi {

    @Autowired
    private KullaniciRepository kullaniciRepository;

    /**
     * O an oturum açmış kullanıcıyı veritabanından getirir.
     *
     * @return aktif {@link Kullanici}; SecurityContext boşsa {@code null}
     */
    public Kullanici getAktifKullanici() {
        String kullaniciAdi = SecurityContextHolder.getContext().getAuthentication().getName();
        return kullaniciRepository.findByKullaniciAdi(kullaniciAdi);
    }

    /**
     * Aktif kullanıcının talep edilen market'e ait olup olmadığını doğrular.
     *
     * @param talepEdilenMarketId istekte geçen market ID'si
     * @throws SecurityException kullanıcı bu market'e erişemiyorsa
     */
    public void marketYetkiKontrolu(Long talepEdilenMarketId) {
        Kullanici aktif = getAktifKullanici();
        if (aktif == null || !aktif.getMarket().getId().equals(talepEdilenMarketId)) {
            throw new SecurityException("Yetkisiz Market Erişimi!");
        }
    }
}
