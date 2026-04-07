package com.market.pos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KayitIstegi {

    @NotBlank(message = "Kullanıcı adı boş olamaz!")   // ✅ EKLENDİ
    @Size(min = 3, max = 50, message = "Kullanıcı adı 3-50 karakter olmalı!")
    private String kullaniciAdi;

    @NotBlank(message = "Şifre boş olamaz!")
    @Size(min = 8, max = 100, message = "Şifre en az 8 karakter olmalı!")
    private String sifre;

    @NotBlank(message = "Market adı boş olamaz!")       // ✅ EKLENDİ
    @Size(min = 2, max = 100, message = "Market adı 2-100 karakter olmalı!")
    private String marketAdi;

    @NotBlank(message = "Kayıt olabilmek için Davetiye Kodu zorunludur!")
    private String davetiyeKodu;
}