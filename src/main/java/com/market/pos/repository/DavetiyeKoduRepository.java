package com.market.pos.repository;

import com.market.pos.entity.DavetiyeKodu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * {@link DavetiyeKodu} entity'si için veri erişim katmanı.
 */
public interface DavetiyeKoduRepository extends JpaRepository<DavetiyeKodu, Long> {

    /** Koda göre davetiye kaydını döndürür. */
    DavetiyeKodu findByKod(String kod);

    /** Tüm davetiye kodlarını yeniden eskiye sıralı döndürür. */
    List<DavetiyeKodu> findAllByOrderByIdDesc();

    /**
     * Kodu atomik olarak kullanıldı olarak işaretler.
     *
     * <p>Tek bir UPDATE sorgusuyla hem "kullanılmamış mı?" kontrolünü hem de
     * işaretlemeyi yapar. Eşzamanlı iki isteğin aynı kodu kullanması engellenir.
     *
     * @return güncellenen satır sayısı; 0 ise kod zaten kullanılmış demektir
     */
    @Modifying
    @Query("UPDATE DavetiyeKodu d SET d.kullanildiMi = true " +
           "WHERE d.kod = :kod AND d.kullanildiMi = false")
    int kullanildiOlarakIsaretle(@Param("kod") String kod);
}
