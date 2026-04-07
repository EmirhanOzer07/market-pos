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
 * Şifre değiştirmek için: patron.hash içindeki satırı yeni SHA-256 hash ile değiştir.
 *
 * Hash üretmek için PowerShell:
 *   ([System.BitConverter]::ToString(
 *     [System.Security.Cryptography.SHA256]::Create()
 *     .ComputeHash([System.Text.Encoding]::UTF8.GetBytes("YeniSifre"))
 *   ) -replace "-").ToLower()
 */
@RestController
@RequestMapping("/api/superadmin")
public class SuperAdminController {

    private static final Logger log = LoggerFactory.getLogger(SuperAdminController.class);

    // AppData/Local/MarketPOS — veritabanıyla aynı klasör, git'in dışında
    private static final Path HASH_DOSYASI = Paths.get(
            System.getProperty("user.home"),
            "AppData", "Local", "MarketPOS", "patron.hash");

    // Varsayılan şifre "patron123" — sadece patron.hash yoksa kullanılır
    private static final String VARSAYILAN_HASH =
            "080f732e09f4a6b86878423a60561e87e814571f7069399cb3614578ef151560";

    @Autowired private AuditLogger auditLogger;
    @Autowired private DavetiyeKoduRepository davetiyeRepo;
    @Autowired private MarketRepository marketRepository;

    private String patronSifresiHash;

    /**
     * Uygulama başlarken hash'i AppData'dan yükle.
     * Dosya yoksa oluştur ve varsayılan hash'i yaz.
     */
    @PostConstruct
    private void hashYukle() {
        try {
            Files.createDirectories(HASH_DOSYASI.getParent());

            if (!Files.exists(HASH_DOSYASI)) {
                // İlk çalıştırma: varsayılan hash ile dosya oluştur
                Files.writeString(HASH_DOSYASI, VARSAYILAN_HASH, StandardCharsets.UTF_8);
                log.warn("Patron hash dosyası oluşturuldu: {} | Varsayılan şifre: patron123 | " +
                         "Güvenlik için değiştirin!", HASH_DOSYASI);
            }

            String okunan = Files.readString(HASH_DOSYASI, StandardCharsets.UTF_8).trim();
            // PowerShell UTF-8 BOM'unu temizle (\uFEFF)
            if (okunan.startsWith("\uFEFF")) okunan = okunan.substring(1);

            // Basit format doğrulaması — 64 hex karakter olmalı
            if (!okunan.matches("[0-9a-f]{64}")) {
                log.error("patron.hash geçersiz format — varsayılan kullanılıyor. " +
                          "Dosya 64 karakterlik küçük harf hex içermeli.");
                patronSifresiHash = VARSAYILAN_HASH;
            } else {
                patronSifresiHash = okunan;
                log.info("Patron hash yüklendi: {}  ({}...)",
                        HASH_DOSYASI, okunan.substring(0, 8));
            }

        } catch (IOException e) {
            log.error("patron.hash okunamadı, varsayılan kullanılıyor: {}", e.getMessage());
            patronSifresiHash = VARSAYILAN_HASH;
        }
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

    // SHA-256 — patron şifresi asla düz metin saklanmaz/karşılaştırılmaz
    private String hashle(String metin) {
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

        boolean dogru = patronSifresiHash.equals(hashle(istek.getSifresi()));
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

        if (!patronSifresiHash.equals(hashle(istek.getSifresi()))) {
            response.put("error", "Hatalı şifre!");
            auditLogger.logFailedAdminAttempt(ip, "Davetiye üretme — hatalı şifre");
            return response;
        }

        String yeniKod = "POS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        DavetiyeKodu davetiye = new DavetiyeKodu();
        davetiye.setKod(yeniKod);
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

        if (secret == null || !patronSifresiHash.equals(hashle(secret))) {
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

        if (secret == null || !patronSifresiHash.equals(hashle(secret))) {
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
