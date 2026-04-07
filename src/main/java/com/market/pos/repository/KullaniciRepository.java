package com.market.pos.repository;

import com.market.pos.entity.Kullanici;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface KullaniciRepository extends JpaRepository<Kullanici, Long> {

    Kullanici findByKullaniciAdi(String kullaniciAdi);

    // Market'i tek sorguda yükler — open-in-view=false altında lazy proxy hatası olmaz
    @Query("SELECT k FROM Kullanici k LEFT JOIN FETCH k.market WHERE k.kullaniciAdi = :kullaniciAdi")
    Kullanici findByKullaniciAdiWithMarket(@Param("kullaniciAdi") String kullaniciAdi);

    List<Kullanici> findAllByMarketId(Long marketId);
}