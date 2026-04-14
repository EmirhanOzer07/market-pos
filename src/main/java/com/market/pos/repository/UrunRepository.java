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

    /** Toplu CSV yüklemede çakışma tespiti için hafif projeksiyon — sadece id, barkod, isim, fiyat. */
    interface UrunCakismaProje {
        Long getId();
        String getBarkod();
        String getIsim();
        java.math.BigDecimal getFiyat();
    }

    /** Markete ait ürünlerin yalnızca çakışma tespitinde kullanılan alanlarını döndürür. */
    @org.springframework.data.jpa.repository.Query(
        "SELECT u.id AS id, u.barkod AS barkod, u.isim AS isim, u.fiyat AS fiyat " +
        "FROM Urun u WHERE u.market.id = :marketId")
    java.util.List<UrunCakismaProje> findCakismaProjeByMarketId(
        @org.springframework.data.repository.query.Param("marketId") Long marketId);

    /** Barkod ve market ID'sine göre tek bir ürün döndürür. Satış doğrulamasında kullanılır. */
    Urun findByBarkodAndMarketId(String barkod, Long marketId);

    /** Birden fazla barkod için tek sorguda toplu arama — N+1'i önler. */
    @org.springframework.data.jpa.repository.Query(
        "SELECT u FROM Urun u WHERE u.barkod IN :barkodlar AND u.market.id = :marketId")
    java.util.List<Urun> findByBarkodInAndMarketId(
        @org.springframework.data.repository.query.Param("barkodlar") java.util.List<String> barkodlar,
        @org.springframework.data.repository.query.Param("marketId") Long marketId);

    /** Markete ait tüm ürünleri döndürür. */
    List<Urun> findByMarketId(Long marketId);

    /** İsme göre büyük/küçük harf duyarsız kısmi arama. */
    List<Urun> findByIsimContainingIgnoreCaseAndMarketId(String isim, Long marketId);
}
