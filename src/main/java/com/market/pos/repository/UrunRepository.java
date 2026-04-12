package com.market.pos.repository;

import com.market.pos.entity.Urun;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/**
 * {@link Urun} entity'si için veri erişim katmanı.
 *
 * <p>Tüm sorgular market ID'si ile filtrelidir; bir market başka marketin
 * ürünlerine erişemez. Ek olarak {@code marketFilter} Hibernate filtresi
 * aspect katmanında otomatik uygulanır.
 */
public interface UrunRepository extends JpaRepository<Urun, Long> {

    /** Barkod ve market ID'sine göre tek bir ürün döndürür. Satış doğrulamasında kullanılır. */
    Urun findByBarkodAndMarketId(String barkod, Long marketId);

    /** Markete ait tüm ürünleri döndürür. */
    List<Urun> findByMarketId(Long marketId);

    /** İsme göre büyük/küçük harf duyarsız kısmi arama. */
    List<Urun> findByIsimContainingIgnoreCaseAndMarketId(String isim, Long marketId);
}
