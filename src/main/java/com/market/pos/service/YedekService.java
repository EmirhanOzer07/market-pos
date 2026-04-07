package com.market.pos.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.File;
import java.nio.file.*;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class YedekService {

    private static final Logger log = LoggerFactory.getLogger(YedekService.class);

    @Autowired
    private DataSource dataSource;

    private static final String APPDATA_DIR =
            System.getProperty("user.home") + "/AppData/Local/MarketPOS";
    private static final String YEDEK_KLASORU     = APPDATA_DIR + "/yedek";
    private static final String GUNLUK_KLASORU    = YEDEK_KLASORU + "/gunluk";   // Günlük yedekler
    private static final String ISLEM_KLASORU     = YEDEK_KLASORU + "/islem";    // İşlem sonrası yedekler

    // Kaç yedek saklanır
    private static final int MAX_GUNLUK_YEDEK = 30;  // 30 gün
    private static final int MAX_ISLEM_YEDEK  = 20;  // Son 20 işlem yedeği
    private static final int MIN_YEDEK_KORU   = 7;   // En az 7 yedek asla silinmez

    private final ReentrantLock yedekKilidi = new ReentrantLock();

    /**
     * Uygulama başlarken: Bugün için günlük yedek yoksa al.
     * Veri kaybına karşı ilk savunma hattı.
     */
    @PostConstruct
    public void baslangiçYedegi() {
        new Thread(() -> {
            try { Thread.sleep(8000); } catch (InterruptedException ignored) {} // Spring tam ayağa kalksın
            String bugun = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            File gunlukKlasor = new File(GUNLUK_KLASORU);
            File[] bugunYedekleri = gunlukKlasor.listFiles(
                    f -> f.getName().startsWith("gunluk_" + bugun));
            if (bugunYedekleri == null || bugunYedekleri.length == 0) {
                log.info("[YEDEK] Bugün için günlük yedek yok, alınıyor...");
                gunlukYedekAl();
            } else {
                log.info("[YEDEK] Bugün için günlük yedek mevcut: {}", bugunYedekleri[0].getName());
            }
        }).start();
    }

    /**
     * Her gece 02:00'da otomatik günlük yedek.
     * Kullanıcı uyurken, sistem meşgul değilken.
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void otomatikGunlukYedek() {
        log.info("[YEDEK] Gece otomatik yedek başlıyor...");
        gunlukYedekAl();
    }

    /**
     * Günlük yedek — GUNLUK_KLASORU içine yazar.
     */
    private void gunlukYedekAl() {
        if (!yedekKilidi.tryLock()) return;
        try {
            Files.createDirectories(Paths.get(GUNLUK_KLASORU));
            String tarih = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            Path hedef = Paths.get(GUNLUK_KLASORU, "gunluk_" + tarih + ".zip")
                    .toAbsolutePath().normalize();
            if (!hedef.startsWith(Paths.get(GUNLUK_KLASORU).toAbsolutePath().normalize())) {
                log.error("[YEDEK] Güvenlik: geçersiz yol engellendi");
                return;
            }
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("BACKUP TO '" + hedef + "'");
            }
            log.info("[YEDEK] Günlük yedek alındı: {}", hedef.getFileName());
            eskiYedekleriTemizle(GUNLUK_KLASORU, "gunluk_", MAX_GUNLUK_YEDEK);
        } catch (Exception e) {
            log.warn("[YEDEK] Günlük yedek alınamadı: {}", e.getMessage());
        } finally {
            yedekKilidi.unlock();
        }
    }

    /**
     * İşlem bazlı yedek (satış, ürün ekleme vb.) — ISLEM_KLASORU içine yazar.
     * Sık çağrılır ama kilitli olursa atlar — günlük yedek asıl güvence.
     */
    public void yedekAl(String sebep) {
        if (!yedekKilidi.tryLock()) {
            // Başka yedek devam ediyorsa atla — günlük yedek zaten alacak
            return;
        }
        try {
            Files.createDirectories(Paths.get(ISLEM_KLASORU));
            String temizSebep = sebep.replaceAll("[^a-zA-Z0-9_]", "_");
            String tarih = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            Path hedef = Paths.get(ISLEM_KLASORU, "islem_" + tarih + "_" + temizSebep + ".zip")
                    .toAbsolutePath().normalize();

            // CWE-22: Path traversal koruması
            if (!hedef.startsWith(Paths.get(ISLEM_KLASORU).toAbsolutePath().normalize())) {
                log.error("[YEDEK] Güvenlik: geçersiz yol engellendi");
                return;
            }
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("BACKUP TO '" + hedef + "'");
            }
            log.info("[YEDEK] İşlem yedeği alındı: islem_{}_{}.zip", tarih, temizSebep);
            eskiYedekleriTemizle(ISLEM_KLASORU, "islem_", MAX_ISLEM_YEDEK);
        } catch (Exception e) {
            log.warn("[YEDEK] İşlem yedeği alınamadı: {}", e.getMessage());
        } finally {
            yedekKilidi.unlock();
        }
    }

    /**
     * Eski yedekleri temizle — MIN_YEDEK_KORU sayısının altına inme.
     */
    private void eskiYedekleriTemizle(String klasorYolu, String prefix, int maksimum) {
        try {
            File klasor = new File(klasorYolu);
            File[] dosyalar = klasor.listFiles(
                    f -> f.getName().startsWith(prefix) && f.getName().endsWith(".zip"));
            if (dosyalar == null || dosyalar.length <= maksimum) return;

            Arrays.sort(dosyalar, Comparator.comparing(File::lastModified));

            int silinecek = dosyalar.length - maksimum;
            // En az MIN_YEDEK_KORU dosya kalsın — ne olursa olsun
            int koruma = Math.max(0, dosyalar.length - MIN_YEDEK_KORU);
            silinecek = Math.min(silinecek, koruma);

            for (int i = 0; i < silinecek; i++) {
                boolean silindi = dosyalar[i].delete();
                if (silindi) log.info("[YEDEK] Eski yedek silindi: {}", dosyalar[i].getName());
            }
        } catch (Exception e) {
            log.warn("[YEDEK] Yedek temizleme hatası: {}", e.getMessage());
        }
    }
}
