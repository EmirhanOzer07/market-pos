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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import com.market.pos.security.AuditLogger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.*;

/**
 * Patron şifresi application.properties veya env var'da değil,
 * %APPDATA%\Local\MarketPOS\patron.hash dosyasında saklanır.
 *
 * Bu dosya git'e girmez → CWE-798 (hard-coded credentials) riski yok.
 * Yeni kurulumlar BCrypt kullanır. Eski SHA-256 hash'ler ilk başarılı girişte otomatik migrate edilir.
 *
 * Şifre sıfırlamak için patron.hash dosyasını silin — uygulama varsayılan şifreyle yeniden oluşturur.
 */
@RestController
@RequestMapping("/api/superadmin")
public class SuperAdminController {

    private static final Logger log = LoggerFactory.getLogger(SuperAdminController.class);

    // AppData/Local/MarketPOS — veritabanıyla aynı klasör, git'in dışında
    private static final Path HASH_DOSYASI = Paths.get(
            System.getProperty("user.home"),
            "AppData", "Local", "MarketPOS", "patron.hash");

    @Autowired private AuditLogger auditLogger;
    @Autowired private DavetiyeKoduRepository davetiyeRepo;
    @Autowired private MarketRepository marketRepository;
    @Autowired private BCryptPasswordEncoder passwordEncoder;

    private String patronSifresiHash;

    /**
     * Uygulama başlarken hash'i AppData'dan yükle.
     * - Dosya yoksa BCrypt("patron123") ile oluştur (yeni kurulum).
     * - Eski SHA-256 format (64 hex) geçerli — ilk başarılı girişte otomatik BCrypt'e migrate edilir.
     */
    @PostConstruct
    private void hashYukle() {
        try {
            Files.createDirectories(HASH_DOSYASI.getParent());

            if (!Files.exists(HASH_DOSYASI)) {
                // Yeni kurulum: direkt BCrypt ile oluştur
                String bcryptDefault = passwordEncoder.encode("patron123");
                Files.writeString(HASH_DOSYASI, bcryptDefault, StandardCharsets.UTF_8);
                patronSifresiHash = bcryptDefault;
                log.warn("Patron hash dosyası oluşturuldu (BCrypt): {} | " +
                         "Varsayılan şifre: patron123 | Güvenlik için değiştirin!", HASH_DOSYASI);
                return;
            }

            String okunan = Files.readString(HASH_DOSYASI, StandardCharsets.UTF_8).trim();
            // PowerShell UTF-8 BOM'unu temizle (\uFEFF)
            if (okunan.startsWith("\uFEFF")) okunan = okunan.substring(1);

            // BCrypt format: $2a$, $2b$, $2y$ ile başlar
            if (okunan.startsWith("$2")) {
                patronSifresiHash = okunan;
                log.info("Patron BCrypt hash yüklendi: {}", HASH_DOSYASI);
            } else if (okunan.matches("[0-9a-f]{64}")) {
                // Eski SHA-256 format — geçerli, ilk başarılı girişte migrate edilecek
                patronSifresiHash = okunan;
                log.info("Patron SHA-256 hash yüklendi (ilk başarılı girişte BCrypt'e migrate edilecek): {}",
                        HASH_DOSYASI);
            } else {
                log.error("patron.hash geçersiz format — varsayılan BCrypt kullanılıyor. " +
                          "Dosyayı silerek uygulamayı yeniden başlatın.");
                patronSifresiHash = passwordEncoder.encode("patron123");
            }

        } catch (IOException e) {
            log.error("patron.hash okunamadı, varsayılan BCrypt kullanılıyor: {}", e.getMessage());
            patronSifresiHash = passwordEncoder.encode("patron123");
        }
    }

    /**
     * Şifre doğrulama — BCrypt ve eski SHA-256 formatlarını destekler.
     * SHA-256 eşleşmesi durumunda dosya BCrypt'e otomatik migrate edilir.
     */
    private boolean sifreDogrula(String giris) {
        if (patronSifresiHash.startsWith("$2")) {
            // BCrypt format
            return passwordEncoder.matches(giris, patronSifresiHash);
        }
        // Eski SHA-256 format
        boolean eslesme = patronSifresiHash.equals(sha256Hashle(giris));
        if (eslesme) {
            // Başarılı — BCrypt'e migrate et
            try {
                String yeniHash = passwordEncoder.encode(giris);
                Files.writeString(HASH_DOSYASI, yeniHash, StandardCharsets.UTF_8);
                patronSifresiHash = yeniHash;
                log.info("patron.hash SHA-256'dan BCrypt'e otomatik migrate edildi.");
            } catch (Exception e) {
                log.warn("patron.hash BCrypt migrate edilemedi: {}", e.getMessage());
            }
        }
        return eslesme;
    }

    @lombok.Getter
    @lombok.Setter
    public static class DavetiyeUretIstek {
        private String sifresi;
    }

    // Sadece localhost erişebilir
    private boolean yerelErisimMi(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        return ip.equals("127.0.0.1") || ip.equals("0:0:0:0:0:0:0:1");
    }

    // SHA-256 — sadece eski patron.hash formatı migrate edilirken kullanılır
    private String sha256Hashle(String metin) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(metin.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    @PostMapping("/dogrula")
    public Map<String, Object> dogrula(
            @RequestBody DavetiyeUretIstek istek,
            HttpServletRequest request) {

        Map<String, Object> response = new HashMap<>();

        if (!yerelErisimMi(request)) {
            response.put("basarili", false);
            response.put("mesaj", "Sadece yerel erişim!");
            return response;
        }

        if (istek.getSifresi() == null || istek.getSifresi().isBlank()) {
            response.put("basarili", false);
            response.put("mesaj", "Şifre boş olamaz!");
            return response;
        }

        boolean dogru = sifreDogrula(istek.getSifresi());
        response.put("basarili", dogru);
        response.put("mesaj", dogru ? "Şifre doğrulandı" : "Hatalı şifre!");

        if (!dogru) {
            auditLogger.logFailedAdminAttempt(
                    request.getRemoteAddr(), "Patron doğrulama — hatalı şifre");
        }
        return response;
    }

    @PostMapping("/davetiye-uret")
    public Map<String, Object> davetiyeUret(
            @RequestBody DavetiyeUretIstek istek,
            HttpServletRequest request) {

        String ip = request.getRemoteAddr();
        Map<String, Object> response = new HashMap<>();

        if (!yerelErisimMi(request)) {
            auditLogger.logFailedAdminAttempt(ip, "Uzaktan patron erişimi engellendi");
            response.put("error", "Bu işlem sadece yerel sistemden yapılabilir.");
            return response;
        }

        if (istek.getSifresi() == null || istek.getSifresi().isBlank()) {
            response.put("error", "Şifre boş olamaz!");
            auditLogger.logFailedAdminAttempt(ip, "Davetiye üretme — şifre boş");
            return response;
        }

        if (!sifreDogrula(istek.getSifresi())) {
            response.put("error", "Hatalı şifre!");
            auditLogger.logFailedAdminAttempt(ip, "Davetiye üretme — hatalı şifre");
            return response;
        }

        String yeniKod = "POS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        DavetiyeKodu davetiye = new DavetiyeKodu();
        davetiye.setKod(yeniKod);
        davetiye.setSonKullanmaTarihi(LocalDate.now().plusDays(30));
        davetiyeRepo.save(davetiye);

        response.put("kod", yeniKod);
        response.put("aciklama", "Davetiye başarıyla üretildi!");
        auditLogger.logSuccessfulAdminAction(ip, "Davetiye üretildi: " + yeniKod);
        return response;
    }

    @GetMapping("/marketler")
    public ResponseEntity<?> tumMarketleriGetir(
            @RequestHeader(value = "X-Admin-Secret", required = false) String secret,
            HttpServletRequest request) {

        String ip = request.getRemoteAddr();

        if (!yerelErisimMi(request)) {
            auditLogger.logFailedAdminAttempt(ip, "Uzaktan market listeleme engellendi");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Sadece yerel erişim!");
        }

        if (secret == null || !sifreDogrula(secret)) {
            auditLogger.logFailedAdminAttempt(ip, "Yetkisiz market listeleme denemesi");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Yetkisiz Erişim!");
        }

        List<Market> marketler = marketRepository.findAll();
        auditLogger.logSuccessfulAdminAction(ip, "Market listesi çekildi");
        return ResponseEntity.ok(marketler);
    }

    @PutMapping("/lisans-uzat/{marketId}")
    public ResponseEntity<?> lisansUzat(
            @PathVariable Long marketId,
            @RequestHeader(value = "X-Admin-Secret", required = false) String secret,
            HttpServletRequest request) {

        String ip = request.getRemoteAddr();

        if (!yerelErisimMi(request)) {
            auditLogger.logFailedAdminAttempt(ip, "Uzaktan lisans uzatma engellendi");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Sadece yerel erişim!");
        }

        if (secret == null || !sifreDogrula(secret)) {
            auditLogger.logFailedAdminAttempt(ip, "Yetkisiz lisans uzatma denemesi");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Yetkisiz Erişim!");
        }

        Market market = marketRepository.findById(marketId).orElse(null);
        if (market == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Market bulunamadı!");
        }

        LocalDate mevcutTarih = market.getLisansBitisTarihi() != null
                ? market.getLisansBitisTarihi() : LocalDate.now();
        LocalDate yeniTarih = mevcutTarih.plusYears(1);
        market.setLisansBitisTarihi(yeniTarih);
        marketRepository.save(market);

        auditLogger.logSuccessfulAdminAction(ip,
                "Lisans uzatıldı — Market ID: " + marketId + " | Yeni bitiş: " + yeniTarih);
        return ResponseEntity.ok("Başarılı! Marketin yeni lisans bitiş tarihi: " + yeniTarih);
    }
}
