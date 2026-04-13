package com.market.pos.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Şifre değiştirme isteği için veri transfer nesnesi.
 *
 * <p>Mevcut şifre doğrulandıktan sonra yeni şifre BCrypt ile hashlenerek kaydedilir.</p>
 */
@Getter
@Setter
public class SifreDegistirIstek {
    private String eskiSifre;
    private String yeniSifre;
}
