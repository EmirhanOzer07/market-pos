package com.market.pos.repository;

import com.market.pos.entity.GecersizToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link GecersizToken} varlığı için veri erişim katmanı.
 *
 * <p>Token kara listesi H2 veritabanında saklanır; uygulama yeniden başlatılınca kayıtlar
 * korunur. Süresi dolmuş token'lar periyodik olarak temizlenir.</p>
 */
@Repository
public interface GecersizTokenRepository extends JpaRepository<GecersizToken, Long> {

    /** Token kara listede mi? */
    boolean existsByToken(String token);

    /** Ekleme süresi belirtilen eşikten küçük olan (süresi dolmuş) token'ları siler. */
    @Modifying
    @Transactional
    @Query("DELETE FROM GecersizToken t WHERE t.eklenmeSuresi < :esik")
    void deleteByEklenmeSuresiLessThan(@Param("esik") Long esik);
}
