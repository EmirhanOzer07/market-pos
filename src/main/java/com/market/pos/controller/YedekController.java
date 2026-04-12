package com.market.pos.controller;

import com.market.pos.service.ExcelYedekService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.ZipFile;

/**
 * Yedek listeleme ve güvenli geri yükleme endpoint'leri.
 * Sadece ADMIN (market sahibi) ve PATRON (geliştirici) erişebilir.
 *
 * Geri yükleme akışı:
 *   1. Endpoint: ZIP doğrula → pending_restore.flag yaz → 1.5s sonra exit(0)
 *   2. Startup: DataSourceConfig pending_restore.flag'ı görür → RUNSCRIPT FROM ile SQL yedeği yükler
 *
 * NOT: Yedekler SCRIPT TO ile oluşturulan SQL formatındadır.
 *      Eski binary format (BACKUP TO) geri yüklenemez — yeni yedek alınmalıdır.
 */
@RestController
@RequestMapping("/api/yedek")
public class YedekController {

    @Autowired
    private ExcelYedekService excelYedekService;

    private static final String APPDATA_DIR =
            System.getProperty("user.home").replace("\\", "/") + "/AppData/Local/MarketPOS";
    private static final String YEDEK_DIR   = APPDATA_DIR + "/yedek";
    private static final String GUNLUK_DIR  = YEDEK_DIR + "/gunluk";
    private static final String ISLEM_DIR   = YEDEK_DIR + "/islem";

    // ─────────────────────────────────────────────
    // GET /api/yedek/liste
    // ─────────────────────────────────────────────
    @GetMapping("/liste")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PATRON')")
    public List<Map<String, Object>> liste() {
        List<Map<String, Object>> liste = new ArrayList<>();
        taraKlasor(GUNLUK_DIR, "gunluk_", "GÜNLÜK", liste);
        taraKlasor(ISLEM_DIR,  "islem_",  "İŞLEM",  liste);
        // En yeni önce — dosya adı ISO tarih içerdiğinden alfabetik = kronolojik
        liste.sort((a, b) -> b.get("dosyaAdi").toString()
                .compareTo(a.get("dosyaAdi").toString()));
        return liste;
    }

    private void taraKlasor(String klasorYolu, String prefix,
                             String tur, List<Map<String, Object>> liste) {
        File klasor = new File(klasorYolu);
        File[] dosyalar = klasor.listFiles(
                f -> f.getName().startsWith(prefix) && f.getName().endsWith(".zip"));
        if (dosyalar == null) return;

        for (File dosya : dosyalar) {
            Map<String, Object> bilgi = new LinkedHashMap<>();
            bilgi.put("dosyaAdi", dosya.getName());
            bilgi.put("tur",      tur);
            bilgi.put("boyutKB",  dosya.length() / 1024);

            // Tarih + açıklama: "gunluk_2026-04-09_14-30-00.zip"
            //                   "islem_2026-04-09_14-30-00_satis.zip"
            String tarihStr  = "";
            String aciklama  = "";
            try {
                String gövde = dosya.getName()
                        .replace(prefix, "")
                        .replace(".zip", "");
                // parcalar[0]="2026-04-09", [1]="14-30-00", [2]=sebep(opsiyonel)
                String[] parcalar = gövde.split("_", 3);
                if (parcalar.length >= 2) {
                    tarihStr = parcalar[0] + " " + parcalar[1].replace("-", ":");
                    if (parcalar.length >= 3) {
                        aciklama = parcalar[2].replace("_", " ");
                    }
                }
            } catch (Exception ignored) {}

            // SQL format mı yoksa eski binary format mı?
            boolean sqlFormat = sqlFormatMi(dosya);
            bilgi.put("tarih",    tarihStr);
            bilgi.put("aciklama", aciklama);
            bilgi.put("format",   sqlFormat ? "SQL" : "ESKİ");
            liste.add(bilgi);
        }
    }

    /** ZIP içinde veri.mv.db yoksa yeni SQL formatıdır. */
    private boolean sqlFormatMi(File dosya) {
        try (ZipFile zip = new ZipFile(dosya)) {
            return zip.stream().noneMatch(e -> e.getName().equals("veri.mv.db"));
        } catch (Exception e) {
            return false;
        }
    }

    // ─────────────────────────────────────────────
    // POST /api/yedek/excel  — manuel Excel yedeği al
    // GET  /api/yedek/excel/liste — mevcut Excel dosyalarını listele
    // ─────────────────────────────────────────────
    @PostMapping("/excel")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PATRON')")
    public Map<String, Object> excelYedekAl() throws Exception {
        java.nio.file.Path hedef = excelYedekService.excelYedekAl();
        return Map.of(
                "mesaj",    "Excel yedeği alındı: " + hedef.getFileName(),
                "dosyaAdi", hedef.getFileName().toString(),
                "klasor",   excelYedekService.klasorYoluStr()
        );
    }

    @GetMapping("/excel/liste")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PATRON')")
    public List<Map<String, Object>> excelListesi() {
        return excelYedekService.excelListesi();
    }

    // ─────────────────────────────────────────────
    // POST /api/yedek/geri-yukle
    // Body: {"dosyaAdi":"gunluk_2026-04-09_02-00-00.zip"}
    // ─────────────────────────────────────────────
    @PostMapping("/geri-yukle")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PATRON')")
    public Map<String, Object> geriYukle(@RequestBody Map<String, String> istek) throws Exception {

        String dosyaAdi = istek.get("dosyaAdi");
        if (dosyaAdi == null || dosyaAdi.isBlank()) {
            throw new IllegalArgumentException("Dosya adı belirtilmedi!");
        }

        // CWE-22: Path traversal koruması — normalize + startsWith ile güvenli kontrol
        if (!dosyaAdi.endsWith(".zip")) {
            throw new IllegalArgumentException("Geçersiz dosya formatı!");
        }

        // Dosyayı bul — sadece izin verilen klasörler içinde arama yap
        File dosya = null;
        java.nio.file.Path gunlukBase = java.nio.file.Paths.get(GUNLUK_DIR).toAbsolutePath().normalize();
        java.nio.file.Path islemBase  = java.nio.file.Paths.get(ISLEM_DIR).toAbsolutePath().normalize();

        java.nio.file.Path gunlukAdayi = gunlukBase.resolve(dosyaAdi).normalize();
        java.nio.file.Path islemAdayi  = islemBase.resolve(dosyaAdi).normalize();

        // Resolved path hâlâ izin verilen klasör içinde mi?
        if (!gunlukAdayi.startsWith(gunlukBase) && !islemAdayi.startsWith(islemBase)) {
            throw new IllegalArgumentException("Geçersiz dosya adı!");
        }

        if (gunlukAdayi.startsWith(gunlukBase) && gunlukAdayi.toFile().isFile()) dosya = gunlukAdayi.toFile();
        else if (islemAdayi.startsWith(islemBase) && islemAdayi.toFile().isFile()) dosya = islemAdayi.toFile();

        if (dosya == null) {
            throw new IllegalArgumentException("Yedek dosyası bulunamadı: " + dosyaAdi);
        }

        // Format kontrolü — sadece yeni SQL formatı destekleniyor
        if (!sqlFormatMi(dosya)) {
            throw new IllegalArgumentException(
                    "Bu yedek eski formatta ve geri yüklenemiyor. " +
                    "Lütfen yeni bir yedek alıp onu geri yükleyin.");
        }

        // pending_restore.flag yaz — DataSourceConfig startup'ta okuyacak
        String zipYolu = dosya.getAbsolutePath().replace("\\", "/");
        Path flagDosyasi = Paths.get(APPDATA_DIR.replace("/", "\\"), "pending_restore.flag");
        Files.writeString(flagDosyasi, zipYolu, StandardCharsets.UTF_8);

        // 1.5 saniye sonra JVM'i kapat (Spring + H2 kapanma süresi)
        new Thread(() -> {
            try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
            System.exit(0);
        }, "restore-shutdown").start();

        return Map.of("mesaj",
                "Geri yükleme başlatıldı. Uygulama yeniden başlıyor...\n"
                + "Yüklenen yedek: " + dosyaAdi);
    }
}
