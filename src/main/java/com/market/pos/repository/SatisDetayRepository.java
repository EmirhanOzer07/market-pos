package com.market.pos.repository;

import com.market.pos.entity.SatisDetay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface SatisDetayRepository extends JpaRepository<SatisDetay, Long> {

    // ✅ Belirli bir satışa ait tüm detayları sil
    @Modifying
    @Transactional
    @Query("DELETE FROM SatisDetay sd WHERE sd.satis.id = :satisId")
    void deleteBySatisId(@Param("satisId") Long satisId);

    // Ürüne ait satış kaydı var mı? (silme öncesi kontrol)
    boolean existsByUrunId(Long urunId);
}