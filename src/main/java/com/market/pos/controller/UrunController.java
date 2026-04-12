package com.market.pos.controller;

import java.util.HashMap;
import com.market.pos.service.YedekService;
import com.market.pos.entity.Urun;
import com.market.pos.entity.Market;
import com.market.pos.entity.Kullanici;
import com.market.pos.repository.UrunRepository;
import com.market.pos.repository.MarketRepository;
import com.market.pos.repository.KullaniciRepository;
import com.market.pos.repository.SatisDetayRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.Valid;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/urunler")
public class UrunController {
    @Autowired private YedekService yedekService;
    @Autowired private UrunRepository urunRepository;
    @Autowired private MarketRepository marketRepository;
    @Autowired private KullaniciRepository kullaniciRepository;
    @Autowired private SatisDetayRepository satisDetayRepository;
    @Autowired private CacheManager cacheManager;

    private static final int MAX_SATIR = 10_000;

    private Kullanici getAktifKullanici() {
        String kullaniciAdi = SecurityContextHolder.getContext().getAuthentication().getName();
        return kullaniciRepository.findByKullaniciAdi(kullaniciAdi);
    }

    private void marketYetkiKontrolu(Long talepEdilenMarketId) {
        if (!getAktifKullanici().getMarket().getId().equals(talepEdilenMarketId)) {
            throw new SecurityException("Yetkisiz Market Erişimi!");
        }
    }

    // ✅ GÖREV 4.1: Ürün listesini 30 saniye cache'le
    @GetMapping("/liste/{marketId}")
    @Cacheable(value = "urunListesi", key = "#marketId")
    public List<Urun> urunleriGetir(@PathVariable Long marketId) {
        marketYetkiKontrolu(marketId);
        return urunRepository.findByMarketId(marketId);
    }

    @GetMapping("/bul/{marketId}/{okunanBarkod}")
    public Urun barkodIleBul(@PathVariable Long marketId, @PathVariable String okunanBarkod) {
        marketYetkiKontrolu(marketId);
        return urunRepository.findByBarkodAndMarketId(okunanBarkod, marketId);
    }

    @GetMapping("/ara/{marketId}")
    public List<Urun> isimIleAra(@PathVariable Long marketId,
                                  @RequestParam String isim) {
        marketYetkiKontrolu(marketId);
        if (isim == null || isim.isBlank()) return List.of();
        return urunRepository.findByIsimContainingIgnoreCaseAndMarketId(isim.trim(), marketId);
    }

    @PostMapping("/ekle/{marketId}")
    @CacheEvict(value = "urunListesi", key = "#marketId")
    public Urun tekilUrunEkle(@PathVariable Long marketId, @Valid @RequestBody Urun yeniUrun) {
        marketYetkiKontrolu(marketId);
        Market market = marketRepository.findById(marketId).orElseThrow();
        yeniUrun.setMarket(market);
        yedekService.yedekAl("urun_eklendi");
        return urunRepository.save(yeniUrun);
    }

    @PostMapping("/yukle/{marketId}")
    @Transactional
    @CacheEvict(value = "urunListesi", key = "#marketId")
    public Map<String, Object> topluUrunYukle(@PathVariable Long marketId,
                                              @RequestParam("dosya") MultipartFile dosya) {

        marketYetkiKontrolu(marketId);

        if (dosya.isEmpty()) throw new IllegalArgumentException("Dosya boş olamaz!");

        String dosyaAdi = dosya.getOriginalFilename();
        if (dosyaAdi == null || (!dosyaAdi.toLowerCase().endsWith(".csv") && !dosyaAdi.toLowerCase().endsWith(".txt"))) {
            throw new IllegalArgumentException("Sadece .csv veya .txt yüklenebilir!");
        }

        if (dosya.getSize() > 5 * 1024 * 1024) {
            throw new IllegalArgumentException("Dosya 5MB'dan büyük olamaz!");
        }

        // CWE-434: MIME type kontrolü — uzantı spoofing'e karşı ek güvence
        // Not: farklı OS/tarayıcılar aynı CSV için farklı MIME gönderebilir, izin verilenler geniş tutuldu
        String contentType = dosya.getContentType();
        if (contentType != null) {
            String tip = contentType.toLowerCase().split(";")[0].trim();
            boolean izinli = tip.equals("text/csv")
                    || tip.equals("text/plain")
                    || tip.equals("application/csv")
                    || tip.equals("application/vnd.ms-excel")
                    || tip.equals("application/octet-stream");
            if (!izinli) {
                throw new IllegalArgumentException(
                        "Geçersiz dosya türü (" + tip + "). Sadece CSV/TXT kabul edilir.");
            }
        }

        Market market = marketRepository.findById(marketId).orElseThrow();

        Map<String, Urun> mevcutUrunHaritasi = urunRepository.findByMarketId(marketId)
                .stream()
                .collect(Collectors.toMap(Urun::getBarkod, u -> u));

        Map<String, Urun> sonucHaritasi = new HashMap<>();

        int eklenenCount = 0;
        int guncellenenCount = 0;
        List<String> hatalar = new ArrayList<>();
        // Çakışma: aynı barkod, farklı isim veya fiyat — frontend kullanıcıya sorar
        List<Map<String, Object>> cakismalar = new ArrayList<>();
        // Çakışma barkodlarını takip et — saveAll'dan hariç tut
        java.util.Set<String> cakismaBarkodelari = new java.util.HashSet<>();

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(dosya.getInputStream(), StandardCharsets.UTF_8))) {

            String satir;
            boolean ilkSatir = true;
            int satirNo = 0;

            while ((satir = br.readLine()) != null) {
                satirNo++;

                // ✅ DÜZELTİLDİ: Sınır kontrolü iç try-catch'in DIŞINDA
                // Artık exception yutulmuyor, döngü gerçekten duruyor
                if (satirNo > MAX_SATIR + 1) {
                    throw new IllegalArgumentException(
                            "Dosya çok büyük! Maksimum " + MAX_SATIR + " ürün yüklenebilir."
                    );
                }

                if (ilkSatir) { ilkSatir = false; continue; }
                if (satir.trim().isEmpty()) continue;

                try {
                    String[] s = satir.split("[,;]");
                    if (s.length < 3) {
                        hatalar.add("Satır " + satirNo + ": Eksik sütun (Barkod;İsim;Fiyat bekleniyor)");
                        continue;
                    }

                    String barkod = s[0].trim();
                    String isim = s[1].trim();

                    if (barkod.isEmpty() || isim.isEmpty()) {
                        hatalar.add("Satır " + satirNo + ": Barkod veya isim alanı boş");
                        continue;
                    }
                    if (barkod.length() > 100) {
                        hatalar.add("Satır " + satirNo + ": Barkod çok uzun (max 100 karakter)");
                        continue;
                    }
                    if (isim.length() > 255) {
                        hatalar.add("Satır " + satirNo + ": Ürün adı çok uzun (max 255 karakter)");
                        continue;
                    }

                    BigDecimal fiyat;
                    try {
                        fiyat = new BigDecimal(s[2].trim().replace(",", "."));
                    } catch (Exception e) {
                        hatalar.add("Satır " + satirNo + ": Geçersiz fiyat formatı -> " + s[2]);
                        continue;
                    }

                    if (fiyat.compareTo(BigDecimal.ZERO) < 0) {
                        hatalar.add("Satır " + satirNo + ": Fiyat negatif olamaz");
                        continue;
                    }

                    Urun urun;

                    if (cakismaBarkodelari.contains(barkod)) {
                        // Bu barkod çakışmalı — atla
                        continue;
                    } else if (sonucHaritasi.containsKey(barkod)) {
                        // Aynı CSV'de tekrar geçen barkod — son değeri kullan
                        urun = sonucHaritasi.get(barkod);
                        urun.setIsim(isim);
                        urun.setFiyat(fiyat);
                        continue; // sonucHaritasi'nda zaten var, tekrar put etme
                    } else if (mevcutUrunHaritasi.containsKey(barkod)) {
                        urun = mevcutUrunHaritasi.get(barkod);
                        boolean ayniIsim  = isim.equals(urun.getIsim());
                        boolean ayniFiyat = fiyat.compareTo(urun.getFiyat()) == 0;

                        if (ayniIsim && ayniFiyat) {
                            // Birebir aynı → atla, hiçbir şey yapma
                            continue;
                        } else {
                            // Farklı → çakışma olarak kaydet, frontend soracak
                            Map<String, Object> c = new HashMap<>();
                            c.put("id",       urun.getId());
                            c.put("barkod",   barkod);
                            c.put("dbIsim",   urun.getIsim());
                            c.put("dbFiyat",  urun.getFiyat());
                            c.put("csvIsim",  isim);
                            c.put("csvFiyat", fiyat);
                            cakismalar.add(c);
                            cakismaBarkodelari.add(barkod);
                            continue; // Güncelleme yok — kullanıcı karar verecek
                        }
                    } else {
                        urun = new Urun();
                        urun.setBarkod(barkod);
                        urun.setIsim(isim);
                        urun.setFiyat(fiyat);
                        urun.setMarket(market);
                        eklenenCount++;
                    }

                    sonucHaritasi.put(barkod, urun);

                } catch (Exception e) {
                    hatalar.add("Satır " + satirNo + ": Beklenmeyen hata -> " + e.getMessage());
                }
            }

            if (!sonucHaritasi.isEmpty()) {
                urunRepository.saveAll(sonucHaritasi.values());
            }

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Dosya okunurken kritik hata: " + e.getMessage());
        }

        Map<String, Object> response = new HashMap<>();
        response.put("eklenen", eklenenCount);
        response.put("guncellenen", guncellenenCount);
        response.put("hataSayisi", hatalar.size());
        response.put("hatalar", hatalar);
        response.put("toplamIslem", eklenenCount + guncellenenCount);
        response.put("cakismalar", cakismalar);
        yedekService.yedekAl("toplu_yukleme"); // ✅
        return response;
    }

    @DeleteMapping("/sil/{id}")
    public String urunSil(@PathVariable Long id) {
        Urun mevcutUrun = urunRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Ürün bulunamadı!"));

        Long marketId = getAktifKullanici().getMarket().getId();
        if (!mevcutUrun.getMarket().getId().equals(marketId)) {
            throw new SecurityException("Bu ürün sizin marketinize ait değil!");
        }

        if (satisDetayRepository.existsByUrunId(id)) {
            throw new IllegalArgumentException(
                    "Bu ürüne ait satış kayıtları var, silinemez! " +
                    "Yeniden satışa sunmak istemiyorsanız fiyatını 0 yapabilirsiniz.");
        }

        urunRepository.deleteById(id);
        urunCacheTemizle(marketId);
        yedekService.yedekAl("urun_silindi");
        return "Silindi";
    }

    @PutMapping("/guncelle/{id}")
    public String urunGuncelle(@PathVariable Long id, @Valid @RequestBody Urun guncelUrun) {
        Urun mevcutUrun = urunRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Ürün bulunamadı!"));

        Long marketId = getAktifKullanici().getMarket().getId();
        if (!mevcutUrun.getMarket().getId().equals(marketId)) {
            throw new SecurityException("Bu ürün sizin marketinize ait değil!");
        }

        mevcutUrun.setIsim(guncelUrun.getIsim());
        mevcutUrun.setFiyat(guncelUrun.getFiyat());
        mevcutUrun.setBarkod(guncelUrun.getBarkod());
        urunRepository.save(mevcutUrun);
        urunCacheTemizle(marketId);
        return "Güncellendi";
    }

    // ✅ CacheManager ile programatik cache temizleme (self-invocation sorununu önler)
    private void urunCacheTemizle(Long marketId) {
        var cache = cacheManager.getCache("urunListesi");
        if (cache != null) cache.evict(marketId);
    }
}