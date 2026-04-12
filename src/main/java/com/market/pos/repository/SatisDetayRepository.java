package com.market.pos.repository;

import com.market.pos.entity.SatisDetay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link SatisDetay} entity'si için veri erişim katmanı.
 */
@Repository
public interface SatisDetayRepository extends JpaRepository<SatisDetay, Long> {

    /**
     * Bir satışa ait tüm kalem detaylarını siler.
     * Kasiyer silinirken ilgili satış kayıtları temizlenirken çağrılır.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM SatisDetay sd WHERE sd.satis.id = :satisId")
    void deleteBySatisId(@Param("satisId") Long satisId);

    /** Bir ürüne bağlı satış kaydı olup olmadığını kontrol eder (silme öncesi). */
    boolean existsByUrunId(Long urunId);
}
