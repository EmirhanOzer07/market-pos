package com.market.pos.ekran;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

/**
 * Tema ve ses ayarlarını yönetir.
 * Ayarlar AppData/Local/MarketPOS/config.properties'e kaydedilir.
 * Tüm metodlar thread-safe (volatile + synchronized kaydet).
 */
public class AyarYoneticisi {

    private static final Path CONFIG_YOLU = Paths.get(
            System.getProperty("user.home"), "AppData", "Local", "MarketPOS", "config.properties");

    private static volatile boolean koyu;
    private static volatile boolean sesAcik;

    static {
        Properties props = configOku();
        koyu    = "KOYU".equals(props.getProperty("TEMA", "ACIK"));
        sesAcik = !"KAPALI".equals(props.getProperty("SES", "ACIK"));
    }

    public static boolean isKoyu()    { return koyu; }
    public static boolean isSesAcik() { return sesAcik; }

    public static void temaToggle() {
        koyu = !koyu;
        kaydet();
    }

    public static void sesToggle() {
        sesAcik = !sesAcik;
        kaydet();
    }

    // =========================================================
    // Tema renkleri
    // Koyu tema: iOS/macOS sistem koyu temasına yakın nötr karanlık palet
    // =========================================================

    /** Ana arka plan (pencere/sahne) */
    public static String arkaRengi()        { return koyu ? "#1c1c1e" : "#ecf0f1"; }
    /** Form/kart arka planı */
    public static String formRengi()        { return koyu ? "#2c2c2e" : "white"; }
    /** Birincil metin rengi */
    public static String metinRengi()       { return koyu ? "#f5f5f7" : "#2c3e50"; }
    /** İkincil/açıklama metin rengi */
    public static String ikincilMetin()     { return koyu ? "#8e8e93" : "#7f8c8d"; }
    /** Kenarlık rengi */
    public static String kenarlık()        { return koyu ? "#48484a" : "#bdc3c7"; }
    /** Input alanı arka planı */
    public static String inputArka()        { return koyu ? "#1c1c1e" : "white"; }
    /** Input alanı kenarlık rengi */
    public static String inputKenarlık()   { return koyu ? "#636366" : "#bdc3c7"; }
    /** KasaEkranı sol panel */
    public static String solPanelRengi()    { return koyu ? "#1a1a1a" : "#f4f6f7"; }
    /** Sol panel kenarlık */
    public static String solPanelKenar()    { return koyu ? "#000000" : "#d5d8dc"; }
    /** Tablo çift indis satır rengi */
    public static String tabloSatirCift()   { return koyu ? "#252525" : "#ffffff"; }
    /** Tablo tek indis satır rengi */
    public static String tabloSatirTek()    { return koyu ? "#1e1e1e" : "#f7f9fc"; }
    /** Barkod alanı arka planı */
    public static String barkodAlaniArka()  { return koyu ? "#2c2c2e" : "white"; }

    // =========================================================
    // Config yardımcı metodlar
    // =========================================================

    private static Properties configOku() {
        Properties props = new Properties();
        try {
            if (Files.exists(CONFIG_YOLU)) {
                try (InputStream is = new FileInputStream(CONFIG_YOLU.toFile())) {
                    props.load(is);
                }
            }
        } catch (Exception ignored) {}
        return props;
    }

    private static synchronized void kaydet() {
        try {
            Properties props = configOku();
            props.setProperty("TEMA", koyu ? "KOYU" : "ACIK");
            props.setProperty("SES",  sesAcik ? "ACIK" : "KAPALI");
            try (OutputStream os = new FileOutputStream(CONFIG_YOLU.toFile())) {
                props.store(os, "Market POS - Uygulama Yapilandirmasi");
            }
        } catch (Exception ignored) {}
    }

    // =========================================================
    // Ses geri bildirimleri
    // =========================================================

    /** Başarılı barkod okutma sesi — kısa, yüksek ton */
    public static void basariSesi() {
        if (!sesAcik) return;
        bipCal(1050, 70);
    }

    /** Hata sesi — uzun, alçak ton */
    public static void hataSesi() {
        if (!sesAcik) return;
        bipCal(380, 190);
    }

    /**
     * Belirtilen frekans (Hz) ve sürede (ms) sinüs dalgası üretip çalar.
     * AudioSystem kullanılamıyorsa Toolkit.beep() ile sistem sesi çalar.
     */
    private static void bipCal(int hz, int ms) {
        new Thread(() -> {
            try {
                javax.sound.sampled.AudioFormat fmt =
                        new javax.sound.sampled.AudioFormat(44100, 16, 1, true, false);
                javax.sound.sampled.DataLine.Info info =
                        new javax.sound.sampled.DataLine.Info(
                                javax.sound.sampled.SourceDataLine.class, fmt);

                // Ses çıkışı desteklenmiyorsa sistem sesiyle yedekle
                if (!javax.sound.sampled.AudioSystem.isLineSupported(info)) {
                    java.awt.Toolkit.getDefaultToolkit().beep();
                    return;
                }

                try (javax.sound.sampled.SourceDataLine hat =
                             (javax.sound.sampled.SourceDataLine)
                                     javax.sound.sampled.AudioSystem.getLine(info)) {
                    hat.open(fmt);
                    hat.start();
                    int ornekSayisi = 44100 * ms / 1000;
                    byte[] tampon = new byte[ornekSayisi * 2];
                    double aci = 2.0 * Math.PI * hz / 44100;
                    double fadeBaslangic = ornekSayisi * 0.8;
                    for (int i = 0; i < ornekSayisi; i++) {
                        double kazanim = (i > fadeBaslangic)
                                ? 0.45 * (1.0 - (i - fadeBaslangic) / (ornekSayisi * 0.2))
                                : 0.45;
                        short deger = (short) (Short.MAX_VALUE * kazanim * Math.sin(i * aci));
                        tampon[i * 2]     = (byte) (deger & 0xff);
                        tampon[i * 2 + 1] = (byte) ((deger >> 8) & 0xff);
                    }
                    hat.write(tampon, 0, tampon.length);
                    hat.drain();
                }
            } catch (Exception e) {
                // Ses sistemi hatası — sistem sesiyle yedekle
                try { java.awt.Toolkit.getDefaultToolkit().beep(); } catch (Exception ignored) {}
            }
        }, "pos-ses").start();
    }
}
