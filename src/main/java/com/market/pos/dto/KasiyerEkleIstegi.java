package com.market.pos.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KasiyerEkleIstegi {
    // Sadece bu ikisini dışarıdan kabul ediyoruz! Başka hiçbir şeye izin yok.
    private String kullaniciAdi;
    private String sifre;
}