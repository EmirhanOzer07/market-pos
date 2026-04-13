package com.market.pos.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Çıkış yapılmış veya iptal edilmiş JWT token'larını kalıcı olarak saklar.
 *
 * <p>Uygulama yeniden başlatıldığında kara liste korunur; yalnızca süresi
 * dolmuş kayıtlar periyodik temizleme ile silinir.</p>
 */
@Getter
@Setter
@Entity
@Table(
    name = "GecersizTokenlar",
    indexes = {
        @Index(name = "idx_gecersiz_token", columnList = "token", unique = true),
        @Index(name = "idx_gecersiz_sure",  columnList = "eklenmeSuresi")
    }
)
public class GecersizToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Geçersiz kılınan JWT token değeri. */
    @Column(nullable = false, unique = true, length = 1024)
    private String token;

    /** Token'ın kara listeye eklendiği zaman damgası (System.currentTimeMillis()). */
    @Column(nullable = false)
    private Long eklenmeSuresi;
}
