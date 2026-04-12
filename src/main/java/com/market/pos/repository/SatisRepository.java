package com.market.pos.repository;

import com.market.pos.entity.Satis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * {@link Satis} entity'si için veri erişim katmanı.
 *
 * <p>Tüm toplu ciro sorguları market bazında izole edilmiştir.
 * Tarih aralıklı sorgular için {@link java.util.Date} kullanılır (JPA uyumluluğu).
 */
public interface SatisRepository extends JpaRepository<Satis, Long> {

    /** Belirtilen kasiyere ait tüm satışları getirir. */
    @Query("SELECT s FROM Satis s WHERE s.kullanici.id = :kullaniciId")
    List<Satis> findAllByKullaniciId(@Param("kullaniciId") Long kullaniciId);

    /** Bir kasiyer silinirken onun satış kayıtlarını temizler. */
    @Modifying
    @Transactional
    @Query("DELETE FROM Satis s WHERE s.kullanici.id = :kullaniciId")
    void deleteByKullaniciId(@Param("kullaniciId") Long kullaniciId);

    // ── Anlık özet sorguları ──────────────────────────────────────────────────

    @Query("SELECT SUM(s.toplamTutar) FROM Satis s WHERE s.market.id = :marketId")
    BigDecimal toplamCiroHesapla(@Param("marketId") Long marketId);

    @Query("SELECT SUM(s.toplamTutar) FROM Satis s WHERE s.market.id = :marketId AND s.odemeTipi = 'NAKIT'")
    BigDecimal nakitToplamHesapla(@Param("marketId") Long marketId);

    @Query("SELECT SUM(s.toplamTutar) FROM Satis s WHERE s.market.id = :marketId AND s.odemeTipi = 'KART'")
    BigDecimal kartToplamHesapla(@Param("marketId") Long marketId);

    @Query("SELECT COUNT(s) FROM Satis s WHERE s.market.id = :marketId")
    Long satisSayisiGetir(@Param("marketId") Long marketId);

    // ── Tarih aralıklı rapor sorguları ───────────────────────────────────────

    @Query("SELECT SUM(s.toplamTutar) FROM Satis s WHERE s.market.id = :marketId AND s.tarih BETWEEN :bas AND :bit")
    BigDecimal toplamCiroAralik(@Param("marketId") Long marketId,
                                @Param("bas") java.util.Date bas,
                                @Param("bit") java.util.Date bit);

    @Query("SELECT SUM(s.toplamTutar) FROM Satis s WHERE s.market.id = :marketId AND s.odemeTipi = 'NAKIT' AND s.tarih BETWEEN :bas AND :bit")
    BigDecimal nakitAralik(@Param("marketId") Long marketId,
                           @Param("bas") java.util.Date bas,
                           @Param("bit") java.util.Date bit);

    @Query("SELECT SUM(s.toplamTutar) FROM Satis s WHERE s.market.id = :marketId AND s.odemeTipi = 'KART' AND s.tarih BETWEEN :bas AND :bit")
    BigDecimal kartAralik(@Param("marketId") Long marketId,
                          @Param("bas") java.util.Date bas,
                          @Param("bit") java.util.Date bit);

    @Query("SELECT COUNT(s) FROM Satis s WHERE s.market.id = :marketId AND s.tarih BETWEEN :bas AND :bit")
    Long sayiAralik(@Param("marketId") Long marketId,
                    @Param("bas") java.util.Date bas,
                    @Param("bit") java.util.Date bit);
}
