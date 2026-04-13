package com.market.pos.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;

/**
 * Sistem kullanıcısını temsil eder.
 *
 * <p>Üç rol desteklenir:
 * <ul>
 *   <li>{@code PATRON} – Geliştirici / sistem yöneticisi</li>
 *   <li>{@code ADMIN}  – Market sahibi; personel ve ürün yönetimi yapabilir</li>
 *   <li>{@code KASIYER} – Kasada satış yapabilir, yönetim paneline erişemez</li>
 * </ul>
 *
 * <p>Şifre alanı her zaman BCrypt ile hashlenerek saklanır ve JSON yanıtlarına dahil edilmez.
 */
@Getter
@Setter
@Entity
@Table(
    name = "Kullanicilar",
    indexes = {
        @Index(name = "idx_kullanici_ad",     columnList = "kullaniciAdi"),
        @Index(name = "idx_kullanici_market", columnList = "market_id")
    }
)
@Filter(name = "marketFilter", condition = "market_id = :marketId")
public class Kullanici {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Kullanıcının ait olduğu market. Lazy yüklenir; JSON serileştirmesine dahil edilmez. */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "market_id", nullable = false)
    private Market market;

    /** Benzersiz giriş adı. */
    @Column(unique = true, nullable = false)
    private String kullaniciAdi;

    /** BCrypt hashlenmiş şifre. JSON yanıtlarına dahil edilmez. */
    @JsonIgnore
    @Column(nullable = false)
    private String sifre;

    /** Kullanıcı rolü: {@code PATRON}, {@code ADMIN} veya {@code KASIYER}. */
    @Column(nullable = false)
    private String rol;

    /**
     * Hesap aktif mi? Silinen kasiyerler fiziksel olarak silinmez,
     * {@code false} yapılarak satış geçmişi korunur.
     */
    @Column(columnDefinition = "BOOLEAN DEFAULT TRUE")
    private boolean aktif = true;
}
