package com.market.pos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;
import java.util.UUID;

/**
 * Uygulama başlangıç konfigürasyonunu yöneten yardımcı sınıf.
 *
 * <p>PosApplication'dan çıkarıldı (Single Responsibility Principle):
 * config.properties okuma/yazma, ilk kurulum, port tespiti ve versiyon
 * kontrolü gibi tüm konfigürasyon işlemleri burada toplanmıştır.</p>
 *
 * <p>Yalnızca statik metotlar içerir — Spring bean değildir.</p>
 */
public final class ConfigManager {

    private static final Logger log = LoggerFactory.getLogger(ConfigManager.class);

    /** AppData klasör kök yolu — diğer sınıflar bu sabiti kullanabilir. */
    public static final String APPDATA_DIR =
            System.getProperty("user.home") + "/AppData/Local/MarketPOS";

    private static final String CONFIG_DOSYASI = APPDATA_DIR + "/config.properties";
    private static final String UYGULAMA_VERSIYONU = "2.1.0";

    private ConfigManager() {}

    /**
     * Uygulama başlamadan önce çağrılır: klasör hazırlama, konfigürasyon yükleme
     * ve boş port tespiti bu metotta gerçekleşir.
     */
    public static void hazirla() throws IOException {
        Files.createDirectories(Paths.get(APPDATA_DIR + "/temp"));
        System.setProperty("java.io.tmpdir", APPDATA_DIR + "/temp");
        Files.createDirectories(Paths.get(APPDATA_DIR));

        versiyonDosyasiKontrol();

        File configDosya = new File(CONFIG_DOSYASI);
        if (!configDosya.exists()) {
            ilkKurulumYap(configDosya);
        }
        configYukle(configDosya);

        int port = portBul();
        System.setProperty("server.port", String.valueOf(port));
    }

    /** 8080-8090 arasında boş port bulur. */
    public static int portBul() {
        for (int p = 8080; p <= 8090; p++) {
            try (java.net.ServerSocket ss = new java.net.ServerSocket(p)) {
                ss.setReuseAddress(true);
                return p;
            } catch (IOException ignored) {}
        }
        return 8080;
    }

    private static void versiyonDosyasiKontrol() {
        Path versiyonYolu = Paths.get(APPDATA_DIR + "/version.txt");
        try {
            if (!Files.exists(versiyonYolu)) {
                Files.writeString(versiyonYolu, UYGULAMA_VERSIYONU);
            }
        } catch (IOException e) {
            log.warn("Versiyon dosyası oluşturulamadı: {}", e.getMessage());
        }
    }

    private static void ilkKurulumYap(File configDosya) throws IOException {
        Properties props = new Properties();

        // Her kurulum için benzersiz rastgele değerler üret — kaynak kodda YAZILMAZ
        String jwtSecret = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        String dbSifresi = UUID.randomUUID().toString().replace("-", "");

        props.setProperty("JWT_SECRET", jwtSecret);
        props.setProperty("DB_KULLANICI_SIFRESI", dbSifresi);

        try (OutputStream os = new FileOutputStream(configDosya)) {
            props.store(os, "OZR POS - Uygulama Yapilandirmasi\n# BU DOSYAYI SILMEYIN");
        }
    }

    private static void configYukle(File configDosya) throws IOException {
        Properties props = new Properties();
        try (InputStream is = new FileInputStream(configDosya)) {
            props.load(is);
        }
        for (String key : props.stringPropertyNames()) {
            if (System.getenv(key) == null && System.getProperty(key) == null) {
                System.setProperty(key, props.getProperty(key));
            }
        }
    }
}
