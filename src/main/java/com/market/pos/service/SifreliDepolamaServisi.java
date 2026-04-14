package com.market.pos.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * AES-256/GCM ile dosya ve veri şifreleme/çözme işlemlerini yöneten servis.
 *
 * <p>YedekService ve DataSourceConfig bu servisi kullanarak şifreleme mantığını
 * tek bir yerden yönetir (Single Responsibility Principle).</p>
 *
 * <p>Dosya formatı: {@code MPOS_ENC} (8 bayt sihirli başlık) + IV (12 bayt) + şifreli veri.</p>
 */
@Service
public class SifreliDepolamaServisi {

    private static final Logger log = LoggerFactory.getLogger(SifreliDepolamaServisi.class);

    /** MPOS_ENC: şifreli yedek dosyalarını tanımlayan 8 baytlık sihirli başlık. */
    static final byte[] SIFRE_BASLIGI = "MPOS_ENC".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private VeriTabaniAnahtarService anahtarService;

    /**
     * Belirtilen dosyayı AES-256/GCM ile şifreler ve üzerine yazar.
     *
     * @param dosya Şifrelenecek dosyanın yolu (ham veri → şifreli veri ile değiştirilir)
     * @throws Exception Şifreleme veya dosya okuma/yazma hatası
     */
    public void sifrele(Path dosya) throws Exception {
        String hexAnahtar = anahtarService.anahtarAl();
        byte[] anahtarBytes = HexFormat.of().parseHex(hexAnahtar);

        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);

        SecretKeySpec anahtarSpec = new SecretKeySpec(anahtarBytes, "AES");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, anahtarSpec, new GCMParameterSpec(128, iv));

        byte[] ham = Files.readAllBytes(dosya);
        byte[] sifreli = cipher.doFinal(ham);

        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(dosya))) {
            out.write(SIFRE_BASLIGI);
            out.write(iv);
            out.write(sifreli);
        }
    }

    /**
     * MPOS_ENC formatındaki şifreli bayt dizisini AES-256/GCM ile çözer.
     *
     * @param sifreliBayt Formatı: {@code MPOS_ENC} (8 bayt) + IV (12 bayt) + şifreli veri
     * @return Çözülmüş ham bayt dizisi
     * @throws Exception Şifre çözme veya anahtar hatası
     */
    public byte[] coz(byte[] sifreliBayt) throws Exception {
        String hexAnahtar = anahtarService.anahtarAl();
        byte[] anahtarBytes = HexFormat.of().parseHex(hexAnahtar);

        byte[] iv          = Arrays.copyOfRange(sifreliBayt, 8, 20);
        byte[] sifreliVeri = Arrays.copyOfRange(sifreliBayt, 20, sifreliBayt.length);

        SecretKeySpec anahtarSpec = new SecretKeySpec(anahtarBytes, "AES");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, anahtarSpec, new GCMParameterSpec(128, iv));
        return cipher.doFinal(sifreliVeri);
    }

    /**
     * Verilen bayt dizisinin MPOS_ENC başlığına sahip olup olmadığını kontrol eder.
     *
     * @param baytlar Kontrol edilecek bayt dizisi
     * @return true ise şifreli formattır
     */
    public boolean sifrelimi(byte[] baytlar) {
        if (baytlar == null || baytlar.length < 8) return false;
        return "MPOS_ENC".equals(new String(baytlar, 0, 8, StandardCharsets.UTF_8));
    }
}
