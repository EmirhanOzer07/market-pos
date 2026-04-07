package com.market.pos.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;

@Getter
@Setter
@Entity
// 🚀 PERFORMANS ZIRHI: Kullanıcı adı sorgularını ışık hızına çıkaran indeks eklendi.
@Table(name = "Kullanicilar",
        indexes = {
                @Index(name = "idx_kullanici_ad", columnList = "kullaniciAdi"),
                @Index(name = "idx_kullanici_market", columnList = "market_id")
        })
@Filter(name = "marketFilter", condition = "market_id = :marketId")
public class Kullanici {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 🚀 PERFORMANS ZIRHI: Tembel yükleme ve JSON gizlemesi
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "market_id", nullable = false)
    private Market market;

    @Column(unique = true, nullable = false)
    private String kullaniciAdi;

    @JsonIgnore
    @Column(nullable = false)
    private String sifre;

    @Column(nullable = false)
    private String rol;
}