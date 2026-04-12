package com.market.pos.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

/**
 * Market kaydı için tek kullanımlık davetiye kodunu temsil eder.
 *
 * <p>Kodlar PATRON tarafından üretilir ve yeni markete iletilir.
 * Kayıt tamamlandığında {@code kullanildiMi} atomik olarak {@code true} yapılır;
 * eşzamanlı iki isteğin aynı kodu kullanması engellenir.
 *
 * <p>{@code sonKullanmaTarihi} null ise kod süresiz geçerlidir (eski kayıtlarla uyumluluk).
 * Yeni üretilen kodlar 30 gün geçerlidir.
 */
@Getter
@Setter
@Entity
@Table(name = "Davetiyeler")
public class DavetiyeKodu {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Benzersiz davetiye kodu (örn. {@code POS-A1B2C3D4}). */
    @Column(unique = true, nullable = false)
    private String kod;

    /** Kodun kullanılıp kullanılmadığı. Atomik güncelleme ile set edilir. */
    @Column(nullable = false)
    private boolean kullanildiMi = false;

    /**
     * Kodun geçerlilik bitiş tarihi.
     * {@code null} ise süresiz geçerli (eski kurulumlarla geriye dönük uyumluluk).
     */
    @Column
    private LocalDate sonKullanmaTarihi;
}
