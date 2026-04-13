package com.market.pos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Giriş isteği için veri transfer nesnesi.
 *
 * <p>Kullanıcı adı ve şifre alanları {@code @Valid} ile doğrulanır;
 * kısa veya boş değerler istemciye anlamlı hata mesajı döner.</p>
 */
@Getter
@Setter
public class LoginIstegi {

    @NotBlank(message = "Kullanıcı adı boş olamaz!")
    @Size(min = 3, max = 50, message = "Kullanıcı adı 3-50 karakter olmalı!")
    private String kullaniciAdi;

    @NotBlank(message = "Şifre boş olamaz!")
    @Size(min = 8, max = 100, message = "Şifre en az 8 karakter olmalı!")
    private String sifre;
}
