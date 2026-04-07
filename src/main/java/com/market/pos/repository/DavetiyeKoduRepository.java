package com.market.pos.repository;

import com.market.pos.entity.DavetiyeKodu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DavetiyeKoduRepository extends JpaRepository<DavetiyeKodu, Long> {

    DavetiyeKodu findByKod(String kod);

    // ✅ YENİ: Tek SQL sorgusunda hem "kullanılmamış mı?" kontrolü hem işaretleme
    // Aynı anda gelen iki istek artık aynı kodu iki kez kullanamaz
    @Modifying
    @Query("UPDATE DavetiyeKodu d SET d.kullanildiMi = true " +
            "WHERE d.kod = :kod AND d.kullanildiMi = false")
    int kullanildiOlarakIsaretle(@Param("kod") String kod);
}