package com.market.pos.ekran;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.LongConsumer;

/**
 * Otomatik güncelleme servisi.
 *
 * Döngü önleme mekanizması:
 *   - Bat başarılı kopyalamadan sonra .installed_version dosyasına yeni sürümü yazar
 *   - Sonraki açılışta kod bu dosyayı okur → GitHub sürümü ile karşılaştırır
 *   - Böylece GitHub JAR'ındaki MEVCUT_SURUM sabitinden bağımsız çalışır
 */
public class GuncellemeService {

    private static final Logger log = LoggerFactory.getLogger(GuncellemeService.class);

    private static final String GITHUB_OWNER = "EmirhanOzer07";
    private static final String GITHUB_REPO  = "market-pos";

    // ✏️ Her yeni release öncesi artır
    public static final String MEVCUT_SURUM = "2.1.0";

    private static final String GITHUB_API_URL =
            "https://api.github.com/repos/" + GITHUB_OWNER + "/" + GITHUB_REPO + "/releases/latest";

    private static final Path GUNCELLEME_KLASORU = Paths.get(
            System.getProperty("user.home"), "AppData", "Local", "MarketPOS", "guncelleme");

    // Tek log dosyası — hem Java hem bat buraya yazar
    private static final Path LOG_DOSYASI = GUNCELLEME_KLASORU.resolve("guncelleme.log");

    // Kurulu sürümü saklar — döngü önleme için
    private static final Path KURULU_SURUM_DOSYASI = Paths.get(
            System.getProperty("user.home"), "AppData", "Local", "MarketPOS", ".installed_version");

    public record GuncellemeBilgisi(String yeniSurum, String indirmeUrl, String surumNotu) {}

    /** GitHub API çağrıları için paylaşılan istemci. */
    private static final HttpClient API_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .build();

    /** Yönlendirme desteğiyle dosya indirme istemcisi. */
    private static final HttpClient INDIR_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    // =========================================================
    // Kurulu sürüm yönetimi
    // =========================================================

    /**
     * Gerçek kurulu sürümü döndürür.
     * Önce .installed_version dosyasına bakar (bat tarafından yazılır),
     * yoksa MEVCUT_SURUM sabitini kullanır.
     */
    public static String mevcutSurumOku() {
        try {
            if (Files.exists(KURULU_SURUM_DOSYASI)) {
                String surum = Files.readString(KURULU_SURUM_DOSYASI, StandardCharsets.UTF_8).trim();
                if (!surum.isBlank()) {
                    return surum;
                }
            }
        } catch (Exception ignored) {}
        return MEVCUT_SURUM;
    }

    // =========================================================
    // 1. ADIM: GitHub'dan yeni sürüm var mı?
    // =========================================================

    public static GuncellemeBilgisi guncellemeVarMi() {
        try {
            String kurulu = mevcutSurumOku();

            HttpRequest istek = HttpRequest.newBuilder()
                    .uri(URI.create(GITHUB_API_URL))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "OZRPos/" + kurulu)
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> yanit = API_CLIENT.send(istek, HttpResponse.BodyHandlers.ofString());

            guncellemeyiLogla("GitHub API HTTP " + yanit.statusCode());
            if (yanit.statusCode() != 200) return null;

            ObjectMapper mapper = new ObjectMapper();
            JsonNode kok = mapper.readTree(yanit.body());

            String yeniSurum = kok.get("tag_name").asText("").replaceFirst("^v", "");
            guncellemeyiLogla("Kurulu sürüm: " + kurulu + " | GitHub sürümü: " + yeniSurum);

            if (yeniSurum.isBlank() || !surumDahaYeni(yeniSurum, kurulu)) return null;

            // Assets içinden .jar bul
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
            if (indirmeUrl == null) {
                guncellemeyiLogla("HATA: Release'de .jar asset bulunamadı!");
                return null;
            }

            String surumNotu = kok.has("body") ? kok.get("body").asText("") : "";
            return new GuncellemeBilgisi(yeniSurum, indirmeUrl, surumNotu);

        } catch (Exception e) {
            log.debug("[GÜNCELLEME] Kontrol edilemedi: {}", e.getMessage());
            return null;
        }
    }

    // =========================================================
    // 2. ADIM: İndir
    // =========================================================

    public static void indir(String indirmeUrl, LongConsumer ilerlemeCB) throws Exception {
        Files.createDirectories(GUNCELLEME_KLASORU);
        Path yeniJar = GUNCELLEME_KLASORU.resolve("OZRPos-yeni.jar");

        guncellemeyiLogla("İndirme başlıyor: " + indirmeUrl);

        // HEAD ile boyut al
        long toplamBoyut = -1;
        try {
            HttpRequest headIstek = HttpRequest.newBuilder()
                    .uri(URI.create(indirmeUrl))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .header("User-Agent", "OZRPos/" + mevcutSurumOku())
                    .timeout(Duration.ofSeconds(10))
                    .build();
            toplamBoyut = INDIR_CLIENT.send(headIstek, HttpResponse.BodyHandlers.discarding())
                    .headers().firstValueAsLong("content-length").orElse(-1);
        } catch (Exception ignored) {}

        // Gerçek indirme
        HttpRequest getIstek = HttpRequest.newBuilder()
                .uri(URI.create(indirmeUrl))
                .header("User-Agent", "OZRPos/" + mevcutSurumOku())
                .header("Accept", "application/octet-stream")
                .timeout(Duration.ofSeconds(300))
                .build();

        HttpResponse<InputStream> yanit = INDIR_CLIENT.send(getIstek,
                HttpResponse.BodyHandlers.ofInputStream());

        if (yanit.statusCode() != 200) {
            throw new IOException("İndirme hatası: HTTP " + yanit.statusCode());
        }
        if (toplamBoyut <= 0) {
            toplamBoyut = yanit.headers().firstValueAsLong("content-length").orElse(-1);
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
                    ilerlemeCB.accept(indirildi * 100 / toplamFinal);
                }
            }
        }

        if (indirildi == 0) {
            throw new IOException("İndirilen dosya boş (0 byte)!");
        }

        guncellemeyiLogla("İndirme tamamlandı: " + indirildi + " / " + toplamBoyut + " byte");
    }

    // =========================================================
    // 3. ADIM: Uygula — bat yaz, başlat, çık
    // =========================================================

    /**
     * @param yeniSurum  Yeni sürüm numarası (bat tarafından .installed_version'a yazılacak)
     */
    public static void guncellemeUygula(String yeniSurum) throws Exception {
        // CWE-78: Bat script injection önlemi — sürüm yalnızca sayı ve nokta içermeli
        if (yeniSurum == null || !yeniSurum.matches("^\\d+\\.\\d+(\\.\\d+)?$")) {
            throw new IllegalArgumentException(
                    "Geçersiz sürüm formatı (bat injection koruması): " + yeniSurum);
        }

        Path yeniJar = GUNCELLEME_KLASORU.resolve("OZRPos-yeni.jar");
        Path betik   = GUNCELLEME_KLASORU.resolve("guncelle.bat");

        if (!Files.exists(yeniJar)) {
            throw new IOException("İndirilen JAR bulunamadı: " + yeniJar);
        }

        // İndirilen JAR'ın geçerli bir ZIP/JAR dosyası olduğunu doğrula (magic bytes: PK)
        byte[] magic = new byte[4];
        try (java.io.InputStream is = Files.newInputStream(yeniJar)) {
            int okunan = is.read(magic);
            if (okunan < 4 || magic[0] != 0x50 || magic[1] != 0x4B) {
                Files.deleteIfExists(yeniJar);
                throw new IOException("İndirilen dosya geçerli bir JAR değil (bozuk indirme)!");
            }
        }

        // Boyut kontrolü — 10MB'dan küçük JAR geçersiz
        if (Files.size(yeniJar) < 10 * 1024 * 1024L) {
            Files.deleteIfExists(yeniJar);
            throw new IOException("İndirilen JAR çok küçük (eksik indirme)! Boyut: "
                    + Files.size(yeniJar) + " byte");
        }

        // EXE yolu — hem launcher hem JAR konumu için kullanılır
        String exeYolu = ProcessHandle.current().info().command().orElse(null);
        guncellemeyiLogla("EXE yolu: " + exeYolu);

        if (exeYolu == null || exeYolu.toLowerCase().contains("java")) {
            throw new IOException(
                    "Paketlenmiş uygulama dışında güncelleme yapılamaz.\n"
                    + "EXE yolu: " + exeYolu + "\n"
                    + "(Geliştirme ortamında bu hata normaldir.)");
        }

        File exeDosya  = new File(exeYolu);
        File jarDosya  = new File(exeDosya.getParentFile(), "app/pos-0.0.1-SNAPSHOT.jar");

        guncellemeyiLogla("Hedef JAR: " + jarDosya.getAbsolutePath() + " | exists=" + jarDosya.exists());

        if (!jarDosya.exists()) {
            throw new IOException("Hedef JAR bulunamadı: " + jarDosya.getAbsolutePath());
        }

        String yeniJarYolu = yeniJar.toAbsolutePath().toString();
        String eskiJarYolu = jarDosya.getAbsolutePath();
        String logYolu     = LOG_DOSYASI.toAbsolutePath().toString();
        String versiyonYolu = KURULU_SURUM_DOSYASI.toAbsolutePath().toString();

        // ─────────────────────────────────────────────────────
        // Bat içeriği
        // chcp 65001: Türkçe karakter içeren path'lerin doğru çalışması için zorunlu
        // Tüm log çıktısı guncelleme.log'a → tek log dosyası
        // ─────────────────────────────────────────────────────
        // Bat dosyası Windows-1254 (Türkçe Windows varsayılan kod sayfası) ile yazılır.
        // cmd.exe bat dosyalarını varsayılan sistem kod sayfasında okur.
        // UTF-8 yazılırsa İ/Ö gibi Türkçe karakterler bozulur → path bulunamaz.
        // CP1254 ile yazınca cmd.exe doğru okur, Türkçe path'ler çalışır.
        Charset batKodSayfasi = Charset.forName("windows-1254");

        String ts = timestamp();
        String bat = "@echo off\r\n"
                + "chcp 1254 >nul\r\n"

                // 1. Eski JVM kapansın, port ve JAR kilidi bırakılsın
                + "echo " + ts + " BAT: Bekleniyor (JVM kapansin)... >> \"" + logYolu + "\"\r\n"
                + "timeout /t 8 /nobreak >nul\r\n"

                // 2. Kopyala
                + "copy /y \"" + yeniJarYolu + "\" \"" + eskiJarYolu + "\"\r\n"
                + "if errorlevel 1 (\r\n"
                + "  echo " + ts + " BAT HATA: JAR kopyalanamadi >> \"" + logYolu + "\"\r\n"
                + "  goto cleanup\r\n"
                + ") else (\r\n"
                + "  echo " + ts + " BAT: JAR kopyalandi >> \"" + logYolu + "\"\r\n"
                + ")\r\n"

                // 3. Kurulu sürüm dosyasını güncelle — döngü önleme
                + "echo " + yeniSurum + " > \"" + versiyonYolu + "\"\r\n"
                + "echo " + ts + " BAT: .installed_version yazildi: " + yeniSurum
                            + " >> \"" + logYolu + "\"\r\n"

                // 4. Port 8080'i zorla boşalt (taskkill /F /IM OZRPos.exe KULLANILMAZ
                //    — cmd.exe OZRPos.exe'nin child process'i olduğundan kendini öldürür)
                + "for /f \"tokens=5\" %%a in ('netstat -ano ^| findstr :8080') do (\r\n"
                + "  taskkill /F /PID %%a 2>nul\r\n"
                + ")\r\n"
                + "timeout /t 2 /nobreak >nul\r\n"

                // 5. Yeni örneği başlat
                + "start \"OZR POS\" \"" + exeYolu + "\"\r\n"
                + "echo " + ts + " BAT: Uygulama baslatildi >> \"" + logYolu + "\"\r\n"

                + ":cleanup\r\n"
                + "del \"" + yeniJarYolu + "\" 2>nul\r\n"
                + "del \"%~f0\"\r\n";

        Files.writeString(betik, bat, batKodSayfasi);
        guncellemeyiLogla("Betik yazıldı — yeniSurum=" + yeniSurum);

        new ProcessBuilder("cmd.exe", "/c", betik.toAbsolutePath().toString()).start();
        guncellemeyiLogla("Betik başlatıldı — Java kapanıyor...");
    }

    // =========================================================
    // Loglama
    // =========================================================

    public static void guncellemeyiLogla(String mesaj) {
        try {
            Files.createDirectories(GUNCELLEME_KLASORU);
            String satir = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    + " | " + mesaj + System.lineSeparator();
            Files.writeString(LOG_DOSYASI, satir, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {}
        System.err.println("[GÜNCELLEME] " + mesaj);
    }

    // =========================================================
    // Yardımcı metodlar
    // =========================================================

    private static String timestamp() {
        return LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
    }

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
