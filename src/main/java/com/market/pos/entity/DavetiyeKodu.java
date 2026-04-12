package com.market.pos.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "Davetiyeler")
public class DavetiyeKodu {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String kod;

    @Column(nullable = false)
    private boolean kullanildiMi = false;

    /** Kodun geçerlilik bitiş tarihi — null ise süresiz geçerli (eski kayıtlar). */
    @Column
    private LocalDate sonKullanmaTarihi;
}