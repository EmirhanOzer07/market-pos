package com.market.pos.config;

import com.market.pos.service.VeriTabaniAnahtarService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.nio.file.*;
import java.sql.DriverManager;

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
public class DataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(DataSourceConfig.class);

    /** H2 SQL kullanıcı şifresi — dosya şifrelemesinden bağımsız, ikinci koruma katmanı. */
    static final String DB_KULLANICI_SIFRESI = "pos_db_2024!";

    /** Eski şifresiz kurulumun varsayılan H2 şifresi (migrasyon için). */
    private static final String ESKI_DB_SIFRESI = "pos123";

    @Autowired
    private VeriTabaniAnahtarService anahtarService;

    @Bean
    @Primary
    public DataSource dataSource() {
        String dosyaAnahtari = anahtarService.anahtarAl();

        String dbYolu = System.getProperty("user.home").replace("\\", "/")
                + "/AppData/Local/MarketPOS/veri";

        // Eski şifresiz DB varsa şifreli formata geçir
        eskiDbMigrasyonuYap(dbYolu, dosyaAnahtari);

        HikariConfig config = new HikariConfig();
        // CIPHER=AES: veritabanı dosyaları AES-256 ile şifrelenir
        config.setJdbcUrl("jdbc:h2:file:" + dbYolu + ";CIPHER=AES");
        config.setDriverClassName("org.h2.Driver");
        config.setUsername("pos");
        // H2 CIPHER şifre formatı: "dosyaAnahtarı kullanıcıŞifresi"
        config.setPassword(dosyaAnahtari + " " + DB_KULLANICI_SIFRESI);

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
     * Şifresiz eski veritabanını AES şifreli formata geçirir.
     *
     * Tetikleyici: Anahtar bu oturumda yeni oluşturulduysa (ilk kurulum veya kayıp anahtar)
     * VE veri.mv.db mevcut ise → eski şifresiz kurulum → migrasyon gerekli.
     *
     * Adımlar:
     *   1. Şifresiz DB'ye bağlan, tüm veriyi SQL dosyasına aktar (SCRIPT TO)
     *   2. Eski .mv.db ve .trace.db dosyalarını sil
     *   3. Şifreli DB'ye bağlan, SQL dosyasından veriyi yükle (RUNSCRIPT FROM)
     *   4. Geçici SQL dosyasını sil
     */
    private void eskiDbMigrasyonuYap(String dbYolu, String dosyaAnahtari) {
        // Anahtar önceden varsa migrasyon gerekmiyor (daha önce yapılmış)
        if (!anahtarService.anahtarYeniOlusturulduMu()) return;

        Path mvDbDosyasi = Paths.get(dbYolu.replace("/", "\\") + ".mv.db");
        if (!Files.exists(mvDbDosyasi)) return; // Fresh install, şifresiz DB yok

        log.info("[MİGRASYON] Şifresiz veritabanı tespit edildi, AES şifreli formata geçiriliyor...");

        Path sqlDosyasi = Paths.get(System.getProperty("user.home"),
                "AppData", "Local", "MarketPOS", "migrasyon_temp.sql");

        String eskiUrl   = "jdbc:h2:file:" + dbYolu;
        String yeniUrl   = "jdbc:h2:file:" + dbYolu + ";CIPHER=AES";
        String yeniSifre = dosyaAnahtari + " " + DB_KULLANICI_SIFRESI;
        String sqlYolu   = sqlDosyasi.toString().replace("\\", "/");

        try {
            // 1. Şifresiz DB'yi SQL dosyasına aktar
            log.info("[MİGRASYON] Adım 1/3 — Veriler dışa aktarılıyor...");
            try (var conn = DriverManager.getConnection(eskiUrl, "pos", ESKI_DB_SIFRESI);
                 var stmt = conn.createStatement()) {
                stmt.execute("SCRIPT TO '" + sqlYolu + "'");
            }

            // 2. Eski şifresiz dosyaları sil
            log.info("[MİGRASYON] Adım 2/3 — Eski dosyalar siliniyor...");
            Files.deleteIfExists(Paths.get(dbYolu.replace("/", "\\") + ".mv.db"));
            Files.deleteIfExists(Paths.get(dbYolu.replace("/", "\\") + ".trace.db"));

            // 3. Şifreli DB'ye veriyi yükle
            log.info("[MİGRASYON] Adım 3/3 — Şifreli veritabanına yükleniyor...");
            try (var conn = DriverManager.getConnection(yeniUrl, "pos", yeniSifre);
                 var stmt = conn.createStatement()) {
                stmt.execute("RUNSCRIPT FROM '" + sqlYolu + "'");
            }

            // 4. Geçici SQL dosyasını temizle
            Files.deleteIfExists(sqlDosyasi);

            log.info("[MİGRASYON] ✓ Tamamlandı — veritabanı artık AES-256 şifreli!");

        } catch (Exception e) {
            log.error("[MİGRASYON] HATA — migrasyon başarısız: {}", e.getMessage(), e);
            try { Files.deleteIfExists(sqlDosyasi); } catch (Exception ignored) {}
            throw new RuntimeException(
                    "Veritabanı AES migrasyonu başarısız! Yedekleri kontrol edin: "
                    + e.getMessage(), e);
        }
    }
}
