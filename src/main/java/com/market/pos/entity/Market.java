package com.market.pos.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

/**
 * Sisteme kayıtlı bir marketi temsil eder.
 *
 * <p>Her market, bir davetiye kodu kullanılarak kayıt sırasında oluşturulur.
 * Lisans bitiş tarihi aşıldığında giriş engellenir; yenileme için PATRON yetkisi gerekir.
 */
@Getter
@Setter
@Entity
@Table(name = "Marketler")
public class Market {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Marketin görünen adı. */
    @Column(nullable = false)
    private String marketAdi;

    /** Lisansın sona erdiği tarih. Bu tarihten sonra giriş yapılamaz. */
    @Column(nullable = false)
    private LocalDate lisansBitisTarihi;

    /** Lisans süresinin dolup dolmadığını merkezi olarak kontrol eder. */
    public boolean lisansSuresiDolduMu() {
        return lisansBitisTarihi != null && lisansBitisTarihi.isBefore(LocalDate.now());
    }
}
