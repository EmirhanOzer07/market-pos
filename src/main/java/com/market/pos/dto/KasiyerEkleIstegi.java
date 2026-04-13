package com.market.pos.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Kasiyer ekleme isteği için veri transfer nesnesi.
 *
 * <p>Yalnızca kullanıcı adı ve şifre kabul edilir;
 * rol ve market ataması sunucu tarafında yapılır.</p>
 */
@Getter
@Setter
public class KasiyerEkleIstegi {
    private String kullaniciAdi;
    private String sifre;
}
