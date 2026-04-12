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

/**
 * Bir markete ait ürünü temsil eder.
 *
 * <p>Barkod + market_id çifti benzersizdir; aynı barkod farklı marketlerde
 * kullanılabilir. {@code marketFilter} Hibernate filtresi sayesinde her kullanıcı
 * yalnızca kendi marketinin ürünlerini görebilir.
 */
@Getter
@Setter
@Entity
@Table(
    name = "Urunler",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"barkod", "market_id"})
    },
    indexes = {
        @Index(name = "idx_urun_barkod", columnList = "barkod"),
        @Index(name = "idx_urun_market", columnList = "market_id")
    }
)
@FilterDef(name = "marketFilter", parameters = @ParamDef(name = "marketId", type = Long.class))
@Filter(name = "marketFilter", condition = "market_id = :marketId")
public class Urun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Ürün barkodu (EAN-13, QR vb.). Market içinde benzersizdir. */
    @NotBlank(message = "Barkod alanı boş bırakılamaz!")
    private String barkod;

    /** Ürünün görünen adı. */
    @NotBlank(message = "Ürün adı boş bırakılamaz!")
    private String isim;

    /** Satış fiyatı. Negatif olamaz. */
    @NotNull(message = "Fiyat boş olamaz!")
    @DecimalMin(value = "0.0", message = "Ürün fiyatı negatif olamaz!")
    private BigDecimal fiyat;

    /** Ürünün ait olduğu market. Lazy yüklenir; JSON yanıtlarına dahil edilmez. */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "market_id", nullable = false)
    private Market market;
}
