package com.market.pos.security;

import com.market.pos.service.KullaniciOnbellekServisi;
import com.market.pos.service.KullaniciOnbellekServisi.MarketBilgisi;
import jakarta.persistence.EntityManager;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Çok kiracılı mimari için Hibernate {@code marketFilter} filtresi uygulayan AOP aspect.
 *
 * <p>Her repository çağrısından önce aktif kullanıcının market ID'sini Hibernate oturumuna
 * yerleştirir; böylece tüm sorgular otomatik olarak ilgili markete ait verilerle sınırlanır.
 * Tekrarlı çalışmayı önlemek için {@link ThreadLocal} kullanılır.</p>
 *
 * <p>Kullanici bilgisi {@link KullaniciOnbellekServisi} üzerinden Caffeine önbelleğinden alınır;
 * aynı kullanıcı için 30 sn içinde veritabanına gidilmez.</p>
 */
@Aspect
@Component
public class MarketFilterAspect {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private KullaniciOnbellekServisi kullaniciOnbellekServisi;

    /**
     * Aynı request içinde filtrenin birden fazla çalışmasını önleyen ThreadLocal bayrağı.
     */
    private static final ThreadLocal<Boolean> filtreAktif = ThreadLocal.withInitial(() -> false);

    @Before("execution(* com.market.pos.repository.*.*(..)) && " +
            "!execution(* com.market.pos.repository.KullaniciRepository.findByKullaniciAdi(..)) && " +
            "!execution(* com.market.pos.repository.KullaniciRepository.findByKullaniciAdiWithMarket(..))")
    public void marketFiltresiniAktifEt() {

        if (filtreAktif.get()) {
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser")) {

            // Önbellekten alınan basit kayıt — Hibernate session bağımlılığı yok
            MarketBilgisi bilgi = kullaniciOnbellekServisi.marketBilgisiniGetir(auth.getName());

            if (bilgi != null) {

                if (!bilgi.aktif()) {
                    throw new SecurityException("Hesabınız devre dışı bırakılmıştır!");
                }

                if (bilgi.lisansBitisTarihi() != null &&
                        bilgi.lisansBitisTarihi().isBefore(LocalDate.now())) {
                    throw new SecurityException("Lisans süreniz dolduğu için işlem yapamazsınız!");
                }

                Session session = entityManager.unwrap(Session.class);
                session.enableFilter("marketFilter")
                        .setParameter("marketId", bilgi.marketId());

                filtreAktif.set(true);
            }
        }
    }

    /**
     * Request sonunda ThreadLocal'ı temizler; bellek sızıntısını önler.
     */
    public static void filtreTemizle() {
        filtreAktif.remove();
    }
}
