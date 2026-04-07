package com.market.pos.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
// 🚀 PERFORMANS ZIRHI: Veritabanında barkod ve market_id için "Fihrist" oluşturduk!
@Table(name = "Urunler",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"barkod", "market_id"})},
        indexes = {
                @Index(name = "idx_urun_barkod", columnList = "barkod"),
                @Index(name = "idx_urun_market", columnList = "market_id")
        })
@FilterDef(name = "marketFilter", parameters = @ParamDef(name = "marketId", type = Long.class))
@Filter(name = "marketFilter", condition = "market_id = :marketId")
public class Urun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Barkod alanı boş bırakılamaz!")
    private String barkod;

    @NotBlank(message = "Ürün adı boş bırakılamaz!")
    private String isim;

    @NotNull(message = "Fiyat boş olamaz!")
    @DecimalMin(value = "0.0", message = "Ürün fiyatı negatif olamaz!")
    private BigDecimal fiyat;

    // 🚀 PERFORMANS ZIRHI: JsonIgnore ile ağ trafiğini hafiflettik, LAZY ile veritabanı yorgunluğunu bitirdik.
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "market_id", nullable = false)
    private Market market;
}