package com.market.pos.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SepetUrunDTO {

    private Long id;
    private String barkod;

    // ✅ DÜZELTİLDİ: Negatif ve sıfır adet artık reddediliyor
    @NotNull(message = "Adet boş olamaz!")
    @DecimalMin(value = "0.001", message = "Adet sıfır veya negatif olamaz!")
    private Double adet;
}