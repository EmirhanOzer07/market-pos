package com.market.pos.dto;

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
    private List<SepetUrunDTO> sepet;
}
