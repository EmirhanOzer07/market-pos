package com.market.pos.repository;

import com.market.pos.entity.Kullanici;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * {@link Kullanici} varlığı için Spring Data JPA repository.
 *
 * <p>{@code open-in-view=false} ortamında lazy proxy hatasını önlemek için
 * {@link #findByKullaniciAdiWithMarket(String)} metodu JOIN FETCH kullanır.</p>
 */
@Repository
public interface KullaniciRepository extends JpaRepository<Kullanici, Long> {

    Kullanici findByKullaniciAdi(String kullaniciAdi);

    /**
     * Kullanıcıyı marketi ile birlikte tek sorguda getirir.
     *
     * @param kullaniciAdi aranan kullanıcı adı
     * @return kullanıcı ve ilişkili market nesnesi, bulunamazsa {@code null}
     */
    @Query("SELECT k FROM Kullanici k LEFT JOIN FETCH k.market WHERE k.kullaniciAdi = :kullaniciAdi")
    Kullanici findByKullaniciAdiWithMarket(@Param("kullaniciAdi") String kullaniciAdi);

    List<Kullanici> findAllByMarketId(Long marketId);

    /** Markete ait yalnızca aktif (silinmemiş) kullanıcıları getirir. */
    List<Kullanici> findAllByMarketIdAndAktifTrue(Long marketId);
}
