package com.market.pos.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "Marketler")
public class Market {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String marketAdi;

    @Column(nullable = false)
    private LocalDate lisansBitisTarihi;

    // ✅ davetiyeKodu KALDIRILDI — DavetiyeKodu entity'si üzerinden yönetiliyor,
    // bu alan hem kullanılmıyordu hem de DB'de boş unique index tutuyordu.
}