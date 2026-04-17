package com.market.pos.config;

import com.market.pos.service.SifreliDepolamaServisi;
import com.market.pos.service.VeriTabaniAnahtarService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.zip.*;

/**
 * AES-256 şifreli H2 veritabanı bağlantısını yapılandırır.
 *
 * H2 CIPHER=AES şifre formatı:
 *   password = "<dosyaAnahtarı> <kullanıcıŞifresi>"  (boşlukla ayrılmış iki parça)
 *
 * - dosyaAnahtarı  → .dbkey'den okunan 64 hex karakter (256-bit AES)
 * - kullanıcıŞifresi → H2 SQL kimlik doğrulaması için ikinci katman
 *
 * Migrasyon: Eski şifresiz DB varsa SCRIPT/RUNSCRIPT ile otomatik şifreli formata geçirir.
 */
@Configuration
@Profile("!test")
public class DataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(DataSourceConfig.class);

    /**
     * H2 SQL kullanıcı şifresi — PosApplication tarafından config.properties'den
     * System.setProperty("DB_KULLANICI_SIFRESI", ...) ile yüklenir.
     * Kaynak kodda YAZILMAZ — her kurulum için benzersizdir.
     */
    static String dbKullaniciSifresi() {
        String s = System.getProperty("DB_KULLANICI_SIFRESI");
        if (s == null || s.isBlank()) {
            throw new IllegalStateException(
                    "[GÜVENLİK] DB_KULLANICI_SIFRESI sistem özelliği ayarlanmamış! " +
                    "config.properties eksik veya bozuk olabilir.");
        }
        return s;
    }

    @Autowired
    private VeriTabaniAnahtarService anahtarService;

    @Autowired
    private SifreliDepolamaServisi sifreliDepolamaServisi;

    private static final Path RESTORE_FLAG = Paths.get(
            System.getProperty("user.home"), "AppData", "Local", "MarketPOS", "pending_restore.flag");

    @Bean
    @Primary
    public DataSource dataSource() {
        String dosyaAnahtari = anahtarService.anahtarAl();

        String dbYolu = System.getProperty("user.home").replace("\\", "/")
                + "/AppData/Local/MarketPOS/veri";

        // Bekleyen yedek restore varsa önce onu işle
        if (Files.exists(RESTORE_FLAG)) {
            try {
                String backupZipYolu = Files.readString(RESTORE_FLAG, StandardCharsets.UTF_8).trim();
                geriYuklemeYap(dbYolu, dosyaAnahtari, backupZipYolu);
            } catch (Exception e) {
                log.error("[GERİ YÜKLEME] Flag okunamadı: {}", e.getMessage(), e);
            } finally {
                try { Files.deleteIfExists(RESTORE_FLAG); } catch (Exception ignored) {}
            }
        }

        HikariConfig config = new HikariConfig();
        // CIPHER=AES: veritabanı dosyaları AES-256 ile şifrelenir
        config.setJdbcUrl("jdbc:h2:file:" + dbYolu + ";CIPHER=AES");
        config.setDriverClassName("org.h2.Driver");
        config.setUsername("pos");
        // H2 CIPHER şifre formatı: "dosyaAnahtarı kullanıcıŞifresi"
        config.setPassword(dosyaAnahtari + " " + dbKullaniciSifresi());

        // Bağlantı havuzu — tek kullanıcı masaüstü uygulaması
        config.setMaximumPoolSize(3);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);

        log.info("[GÜVENLİK] Şifreli veritabanı bağlantısı hazır: {}", dbYolu);
        return new HikariDataSource(config);
    }

    /**
     * pending_restore.flag bulunduğunda SQL yedeğini yeni bir veritabanına yükler.
     *
     * Adımlar:
     *   1. Mevcut veri.mv.db → veri.mv.db.bak (geri alınabilirlik için)
     *   2. Yeni boş şifreli DB aç
     *   3. RUNSCRIPT FROM backup.zip → tüm veri yüklenir
     *   4. Başarılı: .bak sil | Başarısız: .bak → geri yükle
     */
    private void geriYuklemeYap(String dbYolu, String dosyaAnahtari, String backupZipYolu) {
        log.info("[GERİ YÜKLEME] Yedekten geri yükleme başlıyor: {}", backupZipYolu);

        Path backupZip = Paths.get(backupZipYolu.replace("/", "\\"));
        if (!Files.exists(backupZip)) {
            log.error("[GERİ YÜKLEME] Yedek dosyası bulunamadı: {}", backupZipYolu);
            return;
        }

        String dbWin  = dbYolu.replace("/", "\\");
        Path mvDb     = Paths.get(dbWin + ".mv.db");
        Path mvDbBak  = Paths.get(dbWin + ".mv.db.bak");
        Path traceDb  = Paths.get(dbWin + ".trace.db");

        // 1. Mevcut DB'yi yedekle (geri alınabilirlik)
        try {
            Files.deleteIfExists(mvDbBak);
            if (Files.exists(mvDb)) Files.copy(mvDb, mvDbBak);
        } catch (Exception e) {
            log.warn("[GERİ YÜKLEME] .bak oluşturulamadı, devam ediliyor: {}", e.getMessage());
        }

        // 2. Mevcut DB dosyalarını sil
        try {
            Files.deleteIfExists(mvDb);
            Files.deleteIfExists(traceDb);
        } catch (Exception e) {
            log.error("[GERİ YÜKLEME] DB dosyaları silinemedi: {}", e.getMessage());
            geriAlBak(mvDb, mvDbBak);
            return;
        }

        // 3. Şifreleme kontrolü → gerekirse çöz → ZIP'ten script.sql'i çıkar
        Path tempSql = Paths.get(dbWin).getParent().resolve("restore_temp.sql");
        try {
            byte[] ham = Files.readAllBytes(backupZip);
            byte[] zipBytes;
            if (sifreliDepolamaServisi.sifrelimi(ham)) {
                log.info("[GERİ YÜKLEME] Şifreli yedek tespit edildi, çözülüyor...");
                zipBytes = sifreliDepolamaServisi.coz(ham);
            } else {
                zipBytes = ham;
            }
            try (ZipInputStream zis = new ZipInputStream(
                         new java.io.ByteArrayInputStream(zipBytes))) {
                ZipEntry entry = zis.getNextEntry();
                if (entry == null) throw new IOException("ZIP içinde giriş bulunamadı");
                try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(tempSql))) {
                    zis.transferTo(out);
                }
            }
        } catch (Exception e) {
            log.error("[GERİ YÜKLEME] ZIP açılamadı: {}", e.getMessage(), e);
            try { Files.deleteIfExists(mvDb); } catch (Exception ignored) {}
            geriAlBak(mvDb, mvDbBak);
            return;
        }

        // 4. Boş şifreli DB aç ve RUNSCRIPT çalıştır
        String jdbcUrl  = "jdbc:h2:file:" + dbYolu + ";CIPHER=AES";
        String sifre    = dosyaAnahtari + " " + dbKullaniciSifresi();
        String sqlYolu  = tempSql.toString().replace("\\", "/");
        try (java.sql.Connection conn = java.sql.DriverManager.getConnection(jdbcUrl, "pos", sifre);
             java.sql.Statement stmt  = conn.createStatement()) {
            // sqlYolu = AppData altındaki geçici dosya — kullanıcı girdisi değil, path traversal riski yok
            stmt.execute("RUNSCRIPT FROM '" + sqlYolu + "'");
            log.info("[GERİ YÜKLEME] ✓ Geri yükleme başarıyla tamamlandı!");
        } catch (Exception e) {
            log.error("[GERİ YÜKLEME] RUNSCRIPT başarısız: {}", e.getMessage(), e);
            try { Files.deleteIfExists(mvDb); } catch (Exception ignored) {}
            geriAlBak(mvDb, mvDbBak);
            return;
        } finally {
            try { Files.deleteIfExists(tempSql); } catch (Exception ignored) {}
        }

        // 4. Başarılı → .bak'ı temizle
        try { Files.deleteIfExists(mvDbBak); } catch (Exception ignored) {}
    }

    private void geriAlBak(Path mvDb, Path mvDbBak) {
        try {
            if (Files.exists(mvDbBak)) {
                Files.deleteIfExists(mvDb);
                Files.copy(mvDbBak, mvDb);
                Files.deleteIfExists(mvDbBak);
                log.info("[GERİ YÜKLEME] Orijinal veritabanı geri yüklendi (.bak)");
            }
        } catch (Exception ex) {
            log.error("[GERİ YÜKLEME] .bak geri yükleme başarısız: {}", ex.getMessage());
        }
    }

}
