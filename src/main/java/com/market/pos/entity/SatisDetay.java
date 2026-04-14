package com.market.pos.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

/**
 * Bir satış işleminin kalem detayını temsil eder.
 *
 * <p>{@code satilanFiyat} alanı, satış anındaki fiyatı sabitler.
 * Ürün fiyatı sonradan değişse bile geçmiş satış kayıtları etkilenmez.
 */
@Getter
@Setter
@Entity
@Table(
    name = "Satis_Detaylari",
    indexes = {
        @Index(name = "idx_detay_satis", columnList = "satis_id"),
        @Index(name = "idx_detay_urun",  columnList = "urun_id")
    }
)
public class SatisDetay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Bu detayın ait olduğu ana satış kaydı. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "satis_id", nullable = false)
    private Satis satis;

    /** Satılan ürün. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "urun_id", nullable = false)
    private Urun urun;

    /** Satılan adet (kesirli değer desteklenir, örn. 0.5 kg). */
    @Column(nullable = false)
    private Double adet = 1.0;

    /** Satış anındaki birim fiyat. Fiyat değişikliklerinden etkilenmez. */
    @Column(name = "satilan_fiyat", nullable = false)
    private BigDecimal satilanFiyat;
}
