package com.market.pos.service;

import com.market.pos.entity.Kullanici;
import com.market.pos.repository.KullaniciRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Her HTTP isteğinde MarketFilterAspect tarafından çağrılan Kullanici bilgisi önbelleği.
 *
 * <p>Caffeine önbelleği (30 sn TTL) sayesinde aynı kullanıcı için aynı süre içinde
 * gelen isteklerde veritabanına gidilmez; dönen kayıt Hibernate session'ına bağlı
 * değildir (detached entity sorunu yoktur).</p>
 */
@Service
public class KullaniciOnbellekServisi {

    @Autowired
    private KullaniciRepository kullaniciRepository;

    /**
     * Kullanıcının market bilgisini getirir — Caffeine ile 30 sn önbelleğe alınır.
     *
     * @param kullaniciAdi JWT'den gelen kullanıcı adı
     * @return market bilgisi, kullanıcı bulunamazsa veya marketi yoksa {@code null}
     */
    @Cacheable(value = "kullaniciBilgisi", key = "#kullaniciAdi")
    @Transactional(readOnly = true)
    public MarketBilgisi marketBilgisiniGetir(String kullaniciAdi) {
        Kullanici k = kullaniciRepository.findByKullaniciAdiWithMarket(kullaniciAdi);
        if (k == null || k.getMarket() == null) return null;
        return new MarketBilgisi(k.getMarket().getId(), k.getMarket().getLisansBitisTarihi(), k.isAktif());
    }

    /**
     * Kullanıcı güncellenince önbellek kaydını siler.
     *
     * @param kullaniciAdi güncellenen kullanıcı adı
     */
    @CacheEvict(value = "kullaniciBilgisi", key = "#kullaniciAdi")
    public void onbellekTemizle(String kullaniciAdi) {
        // Spring Cache @CacheEvict — metot gövdesi boş olabilir
    }

    /** Yalnızca MarketFilterAspect'in kullandığı basit veri taşıyıcısı. */
    public record MarketBilgisi(Long marketId, LocalDate lisansBitisTarihi, boolean aktif) {}
}
