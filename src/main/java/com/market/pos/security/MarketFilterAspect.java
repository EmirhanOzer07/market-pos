package com.market.pos.security;

import com.market.pos.entity.Kullanici;
import com.market.pos.repository.KullaniciRepository;
import jakarta.persistence.EntityManager;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Aspect
@Component
public class MarketFilterAspect {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private KullaniciRepository kullaniciRepository;

    // ✅ DÜZELTİLDİ: Aynı request içinde filtre zaten set edildiyse DB'ye tekrar gitme
    // Her repository çağrısında 1 ekstra sorgu yapılıyordu — bu ThreadLocal ile engellendi
    private static final ThreadLocal<Boolean> filtreAktif = ThreadLocal.withInitial(() -> false);

    @Before("execution(* com.market.pos.repository.*.*(..)) && " +
            "!execution(* com.market.pos.repository.KullaniciRepository.findByKullaniciAdi(..)) && " +
            "!execution(* com.market.pos.repository.KullaniciRepository.findByKullaniciAdiWithMarket(..))")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public void marketFiltresiniAktifEt() {

        // ✅ Bu request'te filtre zaten aktifleştirildiyse tekrar yapma
        if (filtreAktif.get()) {
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser")) {

            // JOIN FETCH ile Market aynı sorguda yüklenir — open-in-view=false altında lazy proxy hatası olmaz
            Kullanici aktifKullanici = kullaniciRepository.findByKullaniciAdiWithMarket(auth.getName());

            if (aktifKullanici != null && aktifKullanici.getMarket() != null) {

                if (aktifKullanici.getMarket().getLisansBitisTarihi() != null &&
                        aktifKullanici.getMarket().getLisansBitisTarihi().isBefore(LocalDate.now())) {
                    throw new SecurityException("Lisans süreniz dolduğu için işlem yapamazsınız!");
                }

                Session session = entityManager.unwrap(Session.class);
                session.enableFilter("marketFilter")
                        .setParameter("marketId", aktifKullanici.getMarket().getId());

                filtreAktif.set(true); // ✅ Bu request için işaretlendi
            }
        }
    }

    // ✅ Request bitince ThreadLocal'i temizle — bellek sızıntısı olmaz
    // Bu metodun çağrılması için SecurityConfig'e bir filter eklemek gerekiyor (aşağıya bak)
    public static void filtreTemizle() {
        filtreAktif.remove();
    }
}