package com.market.pos.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import java.util.List;

/**
 * Satış tamamlama isteği için veri transfer nesnesi.
 *
 * <p>Ödeme tipi ({@code NAKIT} veya {@code KART}) ve sepet içeriği taşınır.
 * Gerçek fiyatlar sunucu tarafında veritabanından alınır; istemciden gelen fiyat kabul edilmez.</p>
 */
@Getter
@Setter
public class SatisIstegi {
    private String odemeTipi;

    @NotNull
    @NotEmpty
    @Valid
    private List<SepetUrunDTO> sepet;
}
