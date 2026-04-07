package com.market.pos.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Veritabanı AES şifreleme anahtarını yönetir.
 *
 * İlk çalışmada:  256-bit rastgele anahtar üretir → .dbkey dosyasına yazar
 * Sonraki açılışlarda: .dbkey dosyasından okur
 *
 * ÖNEMLİ: .dbkey dosyası silinirse veritabanı kalıcı olarak erişilemez hale gelir.
 * Bu dosyayı harici diske yedekleyin.
 */
@Component
public class VeriTabaniAnahtarService {

    private static final Logger log = LoggerFactory.getLogger(VeriTabaniAnahtarService.class);

    private static final Path ANAHTAR_DOSYASI = Paths.get(
            System.getProperty("user.home"), "AppData", "Local", "MarketPOS", ".dbkey");

    /** Anahtar bu oturumda yeni oluşturulduysa true — migrasyon kararı için kullanılır. */
    private boolean yeniOlusturuldu = false;

    /**
     * Anahtarı döndürür. Dosya yoksa üretir.
     */
    public String anahtarAl() {
        try {
            if (Files.exists(ANAHTAR_DOSYASI)) {
                String anahtar = Files.readString(ANAHTAR_DOSYASI, StandardCharsets.UTF_8).trim();
                if (anahtar.length() == 64) { // 32 byte = 64 hex karakter
                    yeniOlusturuldu = false;
                    return anahtar;
                }
                log.warn("[GÜVENLİK] Anahtar dosyası bozuk, yeniden oluşturuluyor...");
            }
            yeniOlusturuldu = true;
            return anahtarOlusturVeKaydet();
        } catch (Exception e) {
            throw new RuntimeException(
                    "Veritabanı şifreleme anahtarı okunamadı! Dosya: " + ANAHTAR_DOSYASI, e);
        }
    }

    /** Bu oturumda anahtar yeni oluşturulduysa true döner (migrasyon gerekiyor olabilir). */
    public boolean anahtarYeniOlusturulduMu() {
        return yeniOlusturuldu;
    }

    public Path anahtarDosyasiYolu() {
        return ANAHTAR_DOSYASI;
    }

    private String anahtarOlusturVeKaydet() throws IOException {
        // 256-bit AES için 32 byte rastgele anahtar
        byte[] rastgeleBytes = new byte[32];
        new SecureRandom().nextBytes(rastgeleBytes);
        String anahtar = HexFormat.of().formatHex(rastgeleBytes);

        Files.createDirectories(ANAHTAR_DOSYASI.getParent());
        Files.writeString(ANAHTAR_DOSYASI, anahtar, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);

        // Windows: sadece mevcut kullanıcı okuyabilsin
        kullaniciIzinleriAyarla();

        log.info("[GÜVENLİK] Yeni veritabanı şifreleme anahtarı oluşturuldu: {}",
                ANAHTAR_DOSYASI);
        return anahtar;
    }

    /**
     * icacls ile anahtar dosyasını sadece mevcut Windows kullanıcısına kilitler.
     * Kalıtımsal izinler kaldırılır — diğer kullanıcılar ve Administrators erişemez.
     */
    private void kullaniciIzinleriAyarla() {
        try {
            String dosyaYolu = ANAHTAR_DOSYASI.toAbsolutePath().toString();
            String kullaniciAdi = System.getProperty("user.name");

            // /inheritance:r → tüm kalıtımsal izinleri kaldır
            // /grant:r       → sadece mevcut kullanıcıya okuma izni ver
            ProcessBuilder pb = new ProcessBuilder(
                    "icacls", dosyaYolu,
                    "/inheritance:r",
                    "/grant:r", kullaniciAdi + ":(R)"
            );
            pb.redirectErrorStream(true);
            int sonuc = pb.start().waitFor();
            if (sonuc == 0) {
                log.info("[GÜVENLİK] Anahtar dosyası izinleri kısıtlandı (sadece: {})",
                        kullaniciAdi);
            } else {
                log.warn("[GÜVENLİK] icacls izin ayarı başarısız oldu — devam ediliyor");
            }
        } catch (Exception e) {
            log.warn("[GÜVENLİK] Anahtar dosyası izinleri ayarlanamadı: {}", e.getMessage());
        }
    }
}
