package com.market.pos.entity;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Tamamlanmış bir satış işlemini temsil eden JPA varlığı.
 *
 * <p>Her satış; markete, kasiyere, ödeme tipine ve toplam tutara sahiptir.
 * Satış kalemleri {@link SatisDetay} üzerinden ilişkilendirilir.</p>
 */
@Getter
@Setter
@Entity
@Table(
    name = "Satislar",
    indexes = {
        @Index(name = "idx_satis_market_tarih", columnList = "market_id, tarih"),
        @Index(name = "idx_satis_kullanici",    columnList = "kullanici_id")
    }
)
public class Satis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "market_id", nullable = false)
    private Market market;

    @ManyToOne
    @JoinColumn(name = "kullanici_id", nullable = false)
    private Kullanici kullanici;

    /** Ödeme yöntemi: {@code NAKIT} veya {@code KART}. */
    private String odemeTipi;

    private LocalDateTime tarih = LocalDateTime.now();

    private BigDecimal toplamTutar;
}
