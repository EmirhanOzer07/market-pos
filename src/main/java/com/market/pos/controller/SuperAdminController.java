package com.market.pos.controller;

import com.market.pos.entity.DavetiyeKodu;
import com.market.pos.entity.Market;
import com.market.pos.repository.DavetiyeKoduRepository;
import com.market.pos.repository.MarketRepository;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.market.pos.security.AuditLogger;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Patron şifresi uzak sunucuda (Railway) doğrulanır — yerel dosyada hash saklanmaz.
 *
 * <p>API key: AppData/Local/MarketPOS/patron.cfg dosyasında saklanır.
 * Bu dosya git'e girmez, JAR'a dahil edilmez. Geliştirici tarafından kurulum sırasında oluşturulur.
 * Dosya eksikse patron paneli tamamen kilitlenir.</p>
 */
@RestController
@RequestMapping("/api/superadmin")
public class SuperAdminController {

    private static final Logger log = LoggerFactory.getLogger(SuperAdminController.class);

    private static final Path CFG_DOSYASI = Paths.get(
            System.getProperty("user.home"),
            "AppData", "Local", "MarketPOS", "patron.cfg");

    /** Railway sunucu URL — patron.cfg'den yüklenir. */
    private String patronServerUrl;

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired private AuditLogger auditLogger;
    @Autowired private DavetiyeKoduRepository davetiyeRepo;
    @Autowired private MarketRepository marketRepository;

    /** Railway API key — patron.cfg'den yüklenir, null ise panel kilitli. */
    private String apiKey;

    @PostConstruct
    private void configYukle() {
        try {
            if (!Files.exists(CFG_DOSYASI)) {
                log.error("[PATRON] patron.cfg bulunamadi: {} | Patron paneli devre disi.", CFG_DOSYASI);
                apiKey = null;
                return;
            }
            Properties props = new Properties();
            try (InputStream is = Files.newInputStream(CFG_DOSYASI)) {
                props.load(is);
            }
            apiKey = props.getProperty("api.key");
            patronServerUrl = props.getProperty("server.url");
            if (apiKey == null || apiKey.isBlank() || patronServerUrl == null || patronServerUrl.isBlank()) {
                log.error("[PATRON] patron.cfg eksik alanlar (api.key / server.url) | Patron paneli devre disi.");
                apiKey = null;
                return;
            }
            patronCfgGuvenliginisAyarla();
            log.info("[PATRON] Yapilandirma yuklendi.");
        } catch (IOException e) {
            log.error("[PATRON] patron.cfg okunamadi: {} | Patron paneli devre disi.", e.getMessage());
            apiKey = null;
        }
    }

    /**
     * patron.cfg dosyasını yalnızca mevcut Windows kullanıcısına kilitler.
     * icacls komutu: kalıtımsal izinleri kaldırır, sadece okuma izni bırakır.
     * VeriTabaniAnahtarService.kullaniciIzinleriAyarla() ile aynı pattern.
     */
    private void patronCfgGuvenliginisAyarla() {
        try {
            String dosyaYolu = CFG_DOSYASI.toAbsolutePath().toString();
            String kullanici  = System.getProperty("user.name");
            ProcessBuilder pb = new ProcessBuilder(
                    "icacls", dosyaYolu,
                    "/inheritance:r",
                    "/grant:r", kullanici + ":(R)"
            );
            pb.redirectErrorStream(true);
            int sonuc = pb.start().waitFor();
            if (sonuc == 0) {
                log.info("[PATRON] patron.cfg izinleri kisitlandi (sadece: {})", kullanici);
            } else {
                log.warn("[PATRON] patron.cfg icacls izin ayari basarisiz — devam ediliyor");
            }
        } catch (Exception e) {
            log.warn("[PATRON] patron.cfg izinleri ayarlanamadi: {}", e.getMessage());
        }
    }

    /**
     * Şifreyi Railway sunucusunda doğrular.
     * API key eksikse veya sunucuya ulaşılamazsa false döner.
     */
    private boolean sifreDogrula(String sifre) {
        if (apiKey == null || apiKey.isBlank()) {
            log.error("[PATRON] API key yok — patron paneli kilitli.");
            return false;
        }
        try {
            String json = MAPPER.writeValueAsString(Map.of("sifre", sifre));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(patronServerUrl))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString());

            return response.statusCode() == 200 && response.body().contains("\"ok\"");

        } catch (Exception e) {
            log.error("[PATRON] Sunucuya baglanılamadi: {}", e.getMessage());
            return false;
        }
    }

    /** Sadece localhost erişebilir. */
    private boolean yerelErisimMi(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        return ip.equals("127.0.0.1") || ip.equals("0:0:0:0:0:0:0:1");
    }

    @lombok.Getter
    @lombok.Setter
    public static class DavetiyeUretIstek {
        private String sifresi;
    }

    @PostMapping("/dogrula")
    public ResponseEntity<Map<String, Object>> dogrula(
            @RequestBody DavetiyeUretIstek istek,
            HttpServletRequest request) {

        Map<String, Object> response = new HashMap<>();

        if (!yerelErisimMi(request)) {
            response.put("basarili", false);
            response.put("mesaj", "Sadece yerel erisim!");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }

        if (istek.getSifresi() == null || istek.getSifresi().isBlank()) {
            response.put("basarili", false);
            response.put("mesaj", "Sifre bos olamaz!");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        if (apiKey == null) {
            response.put("basarili", false);
            response.put("mesaj", "Patron paneli bu cihazda yapilandirilmamis!");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
        }

        boolean dogru = sifreDogrula(istek.getSifresi());
        response.put("basarili", dogru);
        response.put("mesaj", dogru ? "Sifre dogrulandi" : "Hatali sifre veya sunucuya baglanilamiyor!");

        if (!dogru) {
            auditLogger.logFailedAdminAttempt(request.getRemoteAddr(), "Patron dogrulama basarisiz");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/davetiye-uret")
    public Map<String, Object> davetiyeUret(
            @RequestBody DavetiyeUretIstek istek,
            HttpServletRequest request) {

        String ip = request.getRemoteAddr();
        Map<String, Object> response = new HashMap<>();

        if (!yerelErisimMi(request)) {
            auditLogger.logFailedAdminAttempt(ip, "Uzaktan patron erisimi engellendi");
            response.put("error", "Bu islem sadece yerel sistemden yapilabilir.");
            return response;
        }

        if (istek.getSifresi() == null || istek.getSifresi().isBlank()) {
            response.put("error", "Sifre bos olamaz!");
            return response;
        }

        if (!sifreDogrula(istek.getSifresi())) {
            response.put("error", "Hatali sifre veya sunucuya baglanilamiyor!");
            auditLogger.logFailedAdminAttempt(ip, "Davetiye uretme — dogrulama basarisiz");
            return response;
        }

        String yeniKod = "POS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        DavetiyeKodu davetiye = new DavetiyeKodu();
        davetiye.setKod(yeniKod);
        davetiye.setSonKullanmaTarihi(LocalDate.now().plusDays(30));
        davetiyeRepo.save(davetiye);

        response.put("kod", yeniKod);
        response.put("aciklama", "Davetiye basariyla uretildi!");
        auditLogger.logSuccessfulAdminAction(ip, "Davetiye uretildi: " + yeniKod);
        return response;
    }

    @GetMapping("/marketler")
    public ResponseEntity<?> tumMarketleriGetir(
            @RequestHeader(value = "X-Admin-Secret", required = false) String secret,
            HttpServletRequest request) {

        String ip = request.getRemoteAddr();

        if (!yerelErisimMi(request)) {
            auditLogger.logFailedAdminAttempt(ip, "Uzaktan market listeleme engellendi");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Sadece yerel erisim!");
        }

        if (secret == null || !sifreDogrula(secret)) {
            auditLogger.logFailedAdminAttempt(ip, "Yetkisiz market listeleme denemesi");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Yetkisiz Erisim!");
        }

        List<Market> marketler = marketRepository.findAll();
        auditLogger.logSuccessfulAdminAction(ip, "Market listesi cekildi");
        return ResponseEntity.ok(marketler);
    }

    @PutMapping("/lisans-uzat/{marketId}")
    public ResponseEntity<?> lisansUzat(
            @PathVariable Long marketId,
            @RequestParam(defaultValue = "12") int ay,
            @RequestHeader(value = "X-Admin-Secret", required = false) String secret,
            HttpServletRequest request) {

        String ip = request.getRemoteAddr();

        if (!yerelErisimMi(request)) {
            auditLogger.logFailedAdminAttempt(ip, "Uzaktan lisans uzatma engellendi");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Sadece yerel erisim!");
        }

        if (secret == null || !sifreDogrula(secret)) {
            auditLogger.logFailedAdminAttempt(ip, "Yetkisiz lisans uzatma denemesi");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Yetkisiz Erisim!");
        }

        if (ay < 1 || ay > 120) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Gecersiz sure: 1-120 ay arasinda olmalidir.");
        }

        Market market = marketRepository.findById(marketId).orElse(null);
        if (market == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Market bulunamadi!");
        }

        LocalDate mevcutTarih = market.getLisansBitisTarihi() != null
                ? market.getLisansBitisTarihi() : LocalDate.now();
        LocalDate yeniTarih = mevcutTarih.plusMonths(ay);
        market.setLisansBitisTarihi(yeniTarih);
        marketRepository.save(market);

        auditLogger.logSuccessfulAdminAction(ip,
                "Lisans uzatildi — Market ID: " + marketId +
                " | Sure: " + ay + " ay | Yeni bitis: " + yeniTarih);
        return ResponseEntity.ok("Basarili! Marketin yeni lisans bitis tarihi: " + yeniTarih);
    }

    @GetMapping("/davetiyeler")
    public ResponseEntity<?> tumDavetiyeler(
            @RequestHeader(value = "X-Admin-Secret", required = false) String secret,
            HttpServletRequest request) {

        String ip = request.getRemoteAddr();

        if (!yerelErisimMi(request)) {
            auditLogger.logFailedAdminAttempt(ip, "Uzaktan davetiye listeleme engellendi");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Sadece yerel erisim!");
        }

        if (secret == null || !sifreDogrula(secret)) {
            auditLogger.logFailedAdminAttempt(ip, "Yetkisiz davetiye listeleme denemesi");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Yetkisiz Erisim!");
        }

        List<Map<String, Object>> yanit = davetiyeRepo.findAllByOrderByIdDesc().stream()
                .map(d -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", d.getId());
                    m.put("kod", d.getKod());
                    m.put("kullanildiMi", d.isKullanildiMi());
                    m.put("sonKullanmaTarihi", d.getSonKullanmaTarihi());
                    String durum;
                    if (d.isKullanildiMi()) {
                        durum = "KULLANILDI";
                    } else if (d.getSonKullanmaTarihi() != null
                            && d.getSonKullanmaTarihi().isBefore(LocalDate.now())) {
                        durum = "SURESI_DOLDU";
                    } else {
                        durum = "AKTIF";
                    }
                    m.put("durum", durum);
                    return m;
                })
                .collect(Collectors.toList());

        auditLogger.logSuccessfulAdminAction(ip, "Davetiye listesi cekildi");
        return ResponseEntity.ok(yanit);
    }

    @DeleteMapping("/davetiye/{kod}")
    public ResponseEntity<?> davetiyeIptalEt(
            @PathVariable String kod,
            @RequestHeader(value = "X-Admin-Secret", required = false) String secret,
            HttpServletRequest request) {

        String ip = request.getRemoteAddr();

        if (!yerelErisimMi(request)) {
            auditLogger.logFailedAdminAttempt(ip, "Uzaktan davetiye iptali engellendi");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Sadece yerel erisim!");
        }

        if (secret == null || !sifreDogrula(secret)) {
            auditLogger.logFailedAdminAttempt(ip, "Yetkisiz davetiye iptali denemesi");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Yetkisiz Erisim!");
        }

        // Kod formatı doğrulama: yalnızca harf, rakam ve tire kabul et
        if (!kod.matches("[A-Z0-9\\-]{1,20}")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Gecersiz kod formati!");
        }

        com.market.pos.entity.DavetiyeKodu davetiye = davetiyeRepo.findByKod(kod);
        if (davetiye == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Davetiye bulunamadi!");
        }

        if (davetiye.isKullanildiMi()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Kullanilmis davetiye iptal edilemez!");
        }

        davetiyeRepo.delete(davetiye);
        auditLogger.logSuccessfulAdminAction(ip, "Davetiye iptal edildi: " + kod);
        return ResponseEntity.ok("Davetiye iptal edildi: " + kod);
    }
}
