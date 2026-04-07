package com.market.pos.repository;

import com.market.pos.entity.Urun;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface UrunRepository extends JpaRepository<Urun, Long> {

    // Bir barkodu sadece o marketin içinde ara (Çok kritik!)
    Urun findByBarkodAndMarketId(String barkod, Long marketId);

    // ✅ Temizlendi: findAllByMarketId kaldırıldı, findByMarketId ile birleştirildi
    List<Urun> findByMarketId(Long marketId);

    // İsme göre arama (büyük/küçük harf duyarsız, kısmi eşleşme)
    List<Urun> findByIsimContainingIgnoreCaseAndMarketId(String isim, Long marketId);
}