package com.market.pos.controller;

import java.util.HashMap;
import com.market.pos.service.YedekService;
import com.market.pos.service.KullaniciGuvenlikServisi;
import com.market.pos.entity.Urun;
import com.market.pos.entity.Market;
import com.market.pos.entity.Kullanici;
import com.market.pos.repository.UrunRepository;
import com.market.pos.repository.MarketRepository;
import com.market.pos.repository.KullaniciRepository;
import com.market.pos.repository.SatisDetayRepository;
import org.springframework.beans.factory.annotation.Autowired;
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

/**
 * Ürün yönetimini (listeleme, arama, ekleme, güncelleme, silme, toplu CSV yükleme)
 * yöneten REST kontrolcüsü.
 *
 * <p>Ürün listesi Caffeine cache ile 30 saniye tutulur. Tüm işlemler markete özgü
 * yetki kontrolünden geçer.</p>
 */
@RestController
@RequestMapping("/api/urunler")
public class UrunController {
    @Autowired private YedekService yedekService;
    @Autowired private UrunRepository urunRepository;
    @Autowired private MarketRepository marketRepository;
    @Autowired private KullaniciRepository kullaniciRepository;
    @Autowired private SatisDetayRepository satisDetayRepository;
    @Autowired private CacheManager cacheManager;
    @Autowired private KullaniciGuvenlikServisi guvenlikServisi;

    private static final int MAX_SATIR = 10_000;
    private static final long MAX_DOSYA_BOYUTU = 5L * 1024 * 1024; // 5 MB

    private Kullanici getAktifKullanici() {
        return guvenlikServisi.getAktifKullanici();
    }

    private void marketYetkiKontrolu(Long talepEdilenMarketId) {
        guvenlikServisi.marketYetkiKontrolu(talepEdilenMarketId);
    }

    // Ürün listesi 30 saniye cache'de tutulur (application.properties: caffeine spec)
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

        if (dosya.getSize() > MAX_DOSYA_BOYUTU) {
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

        // Projeksiyon — sadece çakışma tespiti için gerekli alanlar yüklenir (id, barkod, isim, fiyat)
        Map<String, UrunRepository.UrunCakismaProje> mevcutUrunHaritasi =
                urunRepository.findCakismaProjeByMarketId(marketId)
                        .stream()
                        .collect(Collectors.toMap(UrunRepository.UrunCakismaProje::getBarkod, u -> u));

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

                // Satır sınır kontrolü try-catch bloğu dışında — hata yutulmasını önler
                // Artık exception yutulmuyor, döngü gerçekten duruyor
                if (satirNo > MAX_SATIR + 1) {
                    throw new IllegalArgumentException(
                            "Dosya çok büyük! Maksimum " + MAX_SATIR + " ürün yüklenebilir."
                    );
                }

                if (ilkSatir) { ilkSatir = false; continue; }
                if (satir.trim().isEmpty()) continue;

                try {
                    String[] s = csvSatirParcala(satir);
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

                    if (cakismaBarkodelari.contains(barkod)) {
                        // Bu barkod çakışmalı — atla
                        continue;
                    } else if (sonucHaritasi.containsKey(barkod)) {
                        // Aynı CSV'de tekrar geçen barkod — son değeri kullan
                        Urun tekrar = sonucHaritasi.get(barkod);
                        tekrar.setIsim(isim);
                        tekrar.setFiyat(fiyat);
                        continue;
                    } else if (mevcutUrunHaritasi.containsKey(barkod)) {
                        UrunRepository.UrunCakismaProje mevcut = mevcutUrunHaritasi.get(barkod);
                        boolean ayniIsim  = isim.equals(mevcut.getIsim());
                        boolean ayniFiyat = fiyat.compareTo(mevcut.getFiyat()) == 0;

                        if (ayniIsim && ayniFiyat) {
                            // Birebir aynı → atla
                            continue;
                        } else {
                            // Farklı → çakışma, frontend soracak
                            Map<String, Object> c = new HashMap<>();
                            c.put("id",       mevcut.getId());
                            c.put("barkod",   barkod);
                            c.put("dbIsim",   mevcut.getIsim());
                            c.put("dbFiyat",  mevcut.getFiyat());
                            c.put("csvIsim",  isim);
                            c.put("csvFiyat", fiyat);
                            cakismalar.add(c);
                            cakismaBarkodelari.add(barkod);
                            continue;
                        }
                    } else {
                        Urun yeni = new Urun();
                        yeni.setBarkod(barkod);
                        yeni.setIsim(isim);
                        yeni.setFiyat(fiyat);
                        yeni.setMarket(market);
                        eklenenCount++;
                        sonucHaritasi.put(barkod, yeni);
                    }

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
        yedekService.yedekAl("toplu_yukleme");
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

    /**
     * CSV satırını doğru biçimde parçalar.
     *
     * <p>Ayırıcı tespiti: satırda ";" varsa noktalı virgül, yoksa virgül kullanılır.
     * Tırnak içindeki ayırıcılar (örn. "1,5l Fanta") alan sınırı sayılmaz.</p>
     *
     * <p>Örnekler:</p>
     * <pre>
     *   12345;1,5l Fanta;5,99   → ["12345", "1,5l Fanta", "5,99"]
     *   12345,"1,5l Fanta",5.99 → ["12345", "1,5l Fanta", "5.99"]
     *   12345,Fanta,5.99        → ["12345", "Fanta", "5.99"]
     * </pre>
     */
    private static String[] csvSatirParcala(String satir) {
        char ayirici = satir.contains(";") ? ';' : ',';
        List<String> alanlar = new ArrayList<>();
        StringBuilder alan = new StringBuilder();
        boolean tirnaklarici = false;

        for (int i = 0; i < satir.length(); i++) {
            char c = satir.charAt(i);
            if (c == '"') {
                tirnaklarici = !tirnaklarici;
            } else if (c == ayirici && !tirnaklarici) {
                alanlar.add(alan.toString());
                alan.setLength(0);
            } else {
                alan.append(c);
            }
        }
        alanlar.add(alan.toString()); // son alan
        return alanlar.toArray(new String[0]);
    }

    // Programatik cache temizleme: self-invocation sorunundan kaçınmak için @CacheEvict yerine kullanılır
    private void urunCacheTemizle(Long marketId) {
        var cache = cacheManager.getCache("urunListesi");
        if (cache != null) cache.evict(marketId);
    }
}