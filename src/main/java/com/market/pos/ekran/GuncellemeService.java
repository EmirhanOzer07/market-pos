package com.market.pos.ekran;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.function.LongConsumer;

/**
 * Otomatik güncelleme servisi.
 *
 * Akış:
 *   1. GitHub Releases API → en son sürüm kontrolü
 *   2. Yeni sürüm varsa → JAR'ı indir (ilerlemeli)
 *   3. Güncelleme betiği (guncelle.bat) oluştur
 *   4. Betiği başlat → uygulamadan çık → betik JAR'ı değiştirir → uygulamayı yeniden başlatır
 *
 * KURULUM GEREKSİNİMLERİ:
 *   - GITHUB_OWNER ve GITHUB_REPO sabitlerini kendi değerlerinizle değiştirin
 *   - Her release'de JAR dosyasını asset olarak yükleyin (adı .jar ile bitmeli)
 *   - MEVCUT_SURUM'u her yeni release öncesi artırın (örn. "1.0.0" → "1.1.0")
 */
public class GuncellemeService {

    private static final Logger log = LoggerFactory.getLogger(GuncellemeService.class);

    // ✏️ BUNLARI DEĞİŞTİR — GitHub kullanıcı adı ve repo adı
    private static final String GITHUB_OWNER = "EmirhanOzer07";
    private static final String GITHUB_REPO  = "market-pos";

    // ✏️ Her yeni sürümde bu sabiti artır, sonra GitHub'a push et
    public static final String MEVCUT_SURUM = "1.1.0";

    private static final String GITHUB_API_URL =
            "https://api.github.com/repos/" + GITHUB_OWNER + "/" + GITHUB_REPO + "/releases/latest";

    private static final Path GUNCELLEME_KLASORU = Paths.get(
            System.getProperty("user.home"), "AppData", "Local", "MarketPOS", "guncelleme");

    public record GuncellemeBilgisi(String yeniSurum, String indirmeUrl, String surumNotu) {}

    // =========================================================
    // 1. ADIM: GitHub'dan yeni sürüm var mı?
    // =========================================================

    /**
     * GitHub Releases API'yi kontrol eder.
     * @return GuncellemeBilgisi yeni sürüm varsa, null yoksa veya hata olursa.
     */
    public static GuncellemeBilgisi guncellemeVarMi() {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(6))
                    .build();

            HttpRequest istek = HttpRequest.newBuilder()
                    .uri(URI.create(GITHUB_API_URL))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "MarketPOS/" + MEVCUT_SURUM)
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> yanit = client.send(istek,
                    HttpResponse.BodyHandlers.ofString());

            if (yanit.statusCode() != 200) return null;

            ObjectMapper mapper = new ObjectMapper();
            JsonNode kok = mapper.readTree(yanit.body());

            String yeniSurum = kok.get("tag_name").asText("").replaceFirst("^v", "");
            if (yeniSurum.isBlank() || !surumDahaYeni(yeniSurum, MEVCUT_SURUM)) return null;

            // Assets içinden .jar dosyasını bul
            String indirmeUrl = null;
            JsonNode assets = kok.get("assets");
            if (assets != null) {
                for (JsonNode asset : assets) {
                    if (asset.get("name").asText("").toLowerCase().endsWith(".jar")) {
                        indirmeUrl = asset.get("browser_download_url").asText();
                        break;
                    }
                }
            }
            if (indirmeUrl == null) return null;

            String surumNotu = kok.has("body") ? kok.get("body").asText("") : "";
            return new GuncellemeBilgisi(yeniSurum, indirmeUrl, surumNotu);

        } catch (Exception e) {
            // İnternet yok veya API erişilemiyor → sessizce geç
            log.debug("[GÜNCELLEME] Kontrol edilemedi: {}", e.getMessage());
            return null;
        }
    }

    // =========================================================
    // 2. ADIM: İndir + Uygula
    // =========================================================

    /**
     * Yeni JAR'ı indirir ve güncelleme betiğini çalıştırarak uygulamayı yeniden başlatır.
     *
     * @param indirmeUrl  GitHub asset download URL
     * @param ilerlemeCB  0.0–1.0 arası indirme ilerlemesi (UI progress bar için)
     */
    public static void indir(String indirmeUrl, LongConsumer ilerlemeCB) throws Exception {
        Files.createDirectories(GUNCELLEME_KLASORU);
        Path yeniJar = GUNCELLEME_KLASORU.resolve("MarketPOS-yeni.jar");

        // HttpClient: redirect'leri otomatik takip eder (GitHub → CDN arası yönlendirme)
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(15))
                .build();

        // Önce HEAD ile dosya boyutunu al (progress bar için)
        long toplamBoyut = -1;
        try {
            HttpRequest headIstek = HttpRequest.newBuilder()
                    .uri(URI.create(indirmeUrl))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .header("User-Agent", "MarketPOS/" + MEVCUT_SURUM)
                    .timeout(Duration.ofSeconds(10))
                    .build();
            HttpResponse<Void> headYanit = client.send(headIstek,
                    HttpResponse.BodyHandlers.discarding());
            toplamBoyut = headYanit.headers()
                    .firstValueAsLong("content-length").orElse(-1);
        } catch (Exception ignored) {}

        // Gerçek indirme — InputStream olarak al, elle oku (progress takibi)
        HttpRequest getIstek = HttpRequest.newBuilder()
                .uri(URI.create(indirmeUrl))
                .header("User-Agent", "MarketPOS/" + MEVCUT_SURUM)
                .header("Accept", "application/octet-stream")
                .timeout(Duration.ofSeconds(300))
                .build();

        HttpResponse<InputStream> yanit = client.send(getIstek,
                HttpResponse.BodyHandlers.ofInputStream());

        int httpKod = yanit.statusCode();
        if (httpKod != 200) {
            throw new IOException("GitHub indirme hatası: HTTP " + httpKod
                    + " — URL: " + indirmeUrl);
        }

        // Content-Length HEAD'den gelemediyse GET response header'ından dene
        if (toplamBoyut <= 0) {
            toplamBoyut = yanit.headers()
                    .firstValueAsLong("content-length").orElse(-1);
        }

        long indirildi = 0;
        try (InputStream is  = new BufferedInputStream(yanit.body());
             OutputStream os = Files.newOutputStream(yeniJar,
                     StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {

            byte[] buffer = new byte[16_384];
            int okunan;
            final long toplamFinal = toplamBoyut;
            while ((okunan = is.read(buffer)) != -1) {
                os.write(buffer, 0, okunan);
                indirildi += okunan;
                if (toplamFinal > 0 && ilerlemeCB != null) {
                    ilerlemeCB.accept(indirildi * 100 / toplamFinal); // 0–100
                }
            }
        }

        if (indirildi == 0) {
            throw new IOException("İndirilen dosya boş! URL erişilebilir değil olabilir.");
        }

        log.info("[GÜNCELLEME] İndirme tamamlandı: {} byte", indirildi);
    }

    /**
     * Güncelleme betiğini oluşturur ve çalıştırır.
     * Betik: uygulamadan çıkıldıktan 3 saniye sonra JAR'ı değiştirir ve yeniden başlatır.
     */
    public static void guncellemeUygula() throws Exception {
        Path yeniJar    = GUNCELLEME_KLASORU.resolve("MarketPOS-yeni.jar");
        Path mevcutJar  = mevcutJarYoluBul();
        String launcher = launcherYoluBul();
        Path betik      = GUNCELLEME_KLASORU.resolve("guncelle.bat");

        guncellemeyiLogla("guncellemeUygula() çağrıldı");
        guncellemeyiLogla("mevcutJar = " + mevcutJar);
        guncellemeyiLogla("launcher  = " + launcher);
        guncellemeyiLogla("yeniJar   = " + yeniJar + " | exists=" + Files.exists(yeniJar));

        if (!Files.exists(yeniJar)) {
            throw new IOException("İndirilen JAR bulunamadı: " + yeniJar);
        }

        if (mevcutJar == null) {
            throw new IOException(
                    "Mevcut JAR konumu belirlenemedi.\n" +
                    "CodeSource: " + GuncellemeService.class
                            .getProtectionDomain().getCodeSource().getLocation() + "\n" +
                    "Bu hata geliştirme ortamında (IDE/mvn) normaldir.\n" +
                    "Paketlenmiş kurulum dosyasında çalışmaz.");
        }

        String yeniJarYolu = yeniJar.toAbsolutePath().toString();
        String eskiJarYolu = mevcutJar.toAbsolutePath().toString();

        String bat = "@echo off\r\n"
                + "timeout /t 3 /nobreak >nul\r\n"
                + "copy /y \"" + yeniJarYolu + "\" \"" + eskiJarYolu + "\"\r\n"
                + "if errorlevel 1 (\r\n"
                + "  echo HATA: Kopyalama basarisiz >> \""
                +     GUNCELLEME_KLASORU.resolve("guncelle.log") + "\"\r\n"
                + ") else (\r\n"
                + "  echo Guncelleme basarili >> \""
                +     GUNCELLEME_KLASORU.resolve("guncelle.log") + "\"\r\n"
                + (launcher != null ? "  start \"\" \"" + launcher + "\"\r\n" : "")
                + ")\r\n"
                + "del \"" + yeniJarYolu + "\"\r\n"
                + "del \"%~f0\"\r\n";

        Files.writeString(betik, bat, StandardCharsets.UTF_8);
        guncellemeyiLogla("Betik yazıldı: " + betik);

        new ProcessBuilder("cmd.exe", "/c", betik.toAbsolutePath().toString()).start();

        guncellemeyiLogla("Betik başlatıldı, çıkılıyor...");
        log.info("[GÜNCELLEME] Güncelleme betiği başlatıldı. Uygulama kapatılıyor...");
    }

    /** Güncelleme işlemlerini AppData/Local/MarketPOS/guncelleme/guncelleme.log'a yazar. */
    public static void guncellemeyiLogla(String mesaj) {
        try {
            Path logDosyasi = GUNCELLEME_KLASORU.resolve("guncelleme.log");
            Files.createDirectories(GUNCELLEME_KLASORU);
            String satir = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    + " | " + mesaj + System.lineSeparator();
            Files.writeString(logDosyasi, satir, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {}
        System.err.println("[GÜNCELLEME] " + mesaj);
    }

    // =========================================================
    // Yardımcı metodlar
    // =========================================================

    /**
     * Çalışan JAR'ın tam dosya yolunu döndürür. IDE'den çalışıyorsa null.
     *
     * Spring Boot fat JAR'da getCodeSource().getLocation() şu formatta gelir:
     *   jar:file:/C:/path/to/app.jar!/BOOT-INF/classes/
     * Bunu parse ederek dış JAR yolunu döndürürüz.
     */
    static Path mevcutJarYoluBul() {
        // 1. Yöntem: CodeSource location — hem düz JAR hem Spring Boot fat JAR için
        try {
            var location = GuncellemeService.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation();
            String urlStr = location.toString();

            if (urlStr.startsWith("jar:file:")) {
                // Spring Boot fat JAR: "jar:file:/C:/path/app.jar!/BOOT-INF/classes/"
                String yol = urlStr.substring("jar:file:".length());
                int bang = yol.indexOf("!/");
                if (bang > 0) yol = yol.substring(0, bang);
                yol = URLDecoder.decode(yol, StandardCharsets.UTF_8);
                // Windows'ta başındaki / kaldır: /C:/Users → C:/Users
                if (yol.startsWith("/") && yol.length() > 2 && yol.charAt(2) == ':') {
                    yol = yol.substring(1);
                }
                Path p = Paths.get(yol);
                if (Files.exists(p)) return p;

            } else if (urlStr.startsWith("file:")) {
                // Düz JAR: "file:/C:/path/app.jar"
                Path p = Paths.get(location.toURI());
                if (p.toString().toLowerCase().endsWith(".jar") && Files.exists(p)) return p;
            }
        } catch (Exception ignored) {}

        // 2. Yöntem: ProcessHandle — process argümanlarında .jar ara
        try {
            var args = ProcessHandle.current().info().arguments();
            if (args.isPresent()) {
                for (String arg : args.get()) {
                    if (arg.toLowerCase().endsWith(".jar")) {
                        Path p = Paths.get(arg);
                        if (Files.exists(p)) return p;
                    }
                }
            }
        } catch (Exception ignored) {}

        return null; // IDE'den çalışıyor
    }

    /**
     * Uygulamayı başlatan launcher exe'yi döndürür.
     * jpackage ile paketlendiyse MarketPOS.exe, aksi hâlde null.
     */
    private static String launcherYoluBul() {
        try {
            String komut = ProcessHandle.current().info().command().orElse("");
            if (!komut.toLowerCase().contains("java")) {
                return komut; // MarketPOS.exe
            }
            // JAR'ın yanındaki .exe'yi ara
            Path jarYolu = mevcutJarYoluBul();
            if (jarYolu != null) {
                Path exeAday = jarYolu.getParent().resolve("MarketPOS.exe");
                if (Files.exists(exeAday)) return exeAday.toString();
            }
        } catch (Exception ignored) {}
        return null;
    }

    /** "1.1.0" > "1.0.5" → true gibi semantik sürüm karşılaştırması. */
    static boolean surumDahaYeni(String yeni, String mevcut) {
        try {
            String[] y = yeni.trim().split("\\.");
            String[] m = mevcut.trim().split("\\.");
            int uzunluk = Math.max(y.length, m.length);
            for (int i = 0; i < uzunluk; i++) {
                int yi = i < y.length ? Integer.parseInt(y[i]) : 0;
                int mi = i < m.length ? Integer.parseInt(m[i]) : 0;
                if (yi > mi) return true;
                if (yi < mi) return false;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
