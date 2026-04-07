package com.market.pos.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "Satis_Detaylari")
public class SatisDetay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 🚀 PROFESYONEL İLİŞKİ: Bir satışın birçok detayı olabilir.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "satis_id", nullable = false)
    private Satis satis;

    // 🚀 PROFESYONEL İLİŞKİ: Bir ürün birçok farklı satış detayında yer alabilir.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "urun_id", nullable = false)
    private Urun urun;

    @Column(nullable = false)
    private Double adet = 1.0;

    @Column(name = "satilan_fiyat", nullable = false)
    private BigDecimal satilanFiyat; // Java standartlarına göre camelCase yaptık
}