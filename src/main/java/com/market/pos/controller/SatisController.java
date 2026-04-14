package com.market.pos.controller;

import com.market.pos.entity.*;
import com.market.pos.repository.*;
import com.market.pos.dto.SatisIstegi;
import com.market.pos.dto.SepetUrunDTO;
import com.market.pos.security.AuditLogger;
import com.market.pos.service.KullaniciGuvenlikServisi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import com.market.pos.service.YedekService;
import java.math.BigDecimal;
import java.util.*;

/**
 * Satış işlemlerini ve raporlarını yöneten REST kontrolcüsü.
 *
 * <p>Sepet doğrulaması, lisans kontrolü ve ödeme tipi denetimi bu katmanda yapılır.
 * Tüm satış detayları atomik olarak kaydedilir.</p>
 */
@RestController
@RequestMapping("/api/satis")
public class SatisController {
    private static final Logger log = LoggerFactory.getLogger(SatisController.class);
    @Autowired private YedekService yedekService;
    @Autowired private AuditLogger auditLogger;
    @Autowired private CacheManager cacheManager;
    @Autowired private SatisRepository satisRepository;
    @Autowired private SatisDetayRepository satisDetayRepository;
    @Autowired private UrunRepository urunRepository;
    @Autowired private KullaniciGuvenlikServisi guvenlikServisi;

    private Kullanici getAktifKullanici() {
        return guvenlikServisi.getAktifKullanici();
    }

    @PostMapping("/tamamla")
    @Transactional
    public Map<String, Object> satisTamamla(@RequestBody SatisIstegi istek) {

        if (istek.getSepet() == null || istek.getSepet().isEmpty()) {
            throw new IllegalArgumentException("Sepet boş!");
        }

        Kullanici kasiyer = getAktifKullanici();
        if (kasiyer == null) {
            throw new IllegalArgumentException("Aktif kullanıcı bulunamadı!");
        }

        Market market = kasiyer.getMarket();

        // Lisans süresi dolan markette satış yapılamaz
        if (market.lisansSuresiDolduMu()) {
            throw new IllegalArgumentException(
                    "Market lisansı " + market.getLisansBitisTarihi() +
                    " tarihinde sona erdi. Satış yapılamaz!");
        }

        // Sadece NAKIT ve KART kabul edilir
        String odemeTipi = istek.getOdemeTipi();
        if (!"NAKIT".equals(odemeTipi) && !"KART".equals(odemeTipi)) {
            throw new IllegalArgumentException(
                    "Geçersiz ödeme tipi: '" + odemeTipi + "'. Sadece NAKIT veya KART kabul edilir.");
        }

        // Adet ön kontrolü
        for (SepetUrunDTO item : istek.getSepet()) {
            if (item.getAdet() == null || item.getAdet() <= 0) {
                throw new IllegalArgumentException("Geçersiz adet değeri!");
            }
        }

        // Tüm barkodları tek sorguda çek — N+1 önlemi
        List<String> barkodlar = istek.getSepet().stream()
                .map(SepetUrunDTO::getBarkod).collect(java.util.stream.Collectors.toList());
        Map<String, Urun> barkodUrunHaritasi = urunRepository
                .findByBarkodInAndMarketId(barkodlar, market.getId())
                .stream().collect(java.util.stream.Collectors.toMap(Urun::getBarkod, u -> u));

        // Doğrulama ve toplam hesaplama
        BigDecimal toplamTutar = BigDecimal.ZERO;
        List<Urun> dogrulanmisUrunler = new ArrayList<>();
        List<Double> adetler = new ArrayList<>();

        for (SepetUrunDTO item : istek.getSepet()) {
            Urun gercekUrun = barkodUrunHaritasi.get(item.getBarkod());
            if (gercekUrun == null) {
                throw new IllegalArgumentException("Sepetteki bir ürün bu markette bulunamadı!");
            }
            toplamTutar = toplamTutar.add(gercekUrun.getFiyat().multiply(BigDecimal.valueOf(item.getAdet())));
            dogrulanmisUrunler.add(gercekUrun);
            adetler.add(item.getAdet());
        }

        // Ana satışı kaydet
        Satis yeniSatis = new Satis();
        yeniSatis.setMarket(market);
        yeniSatis.setKullanici(kasiyer);
        yeniSatis.setOdemeTipi(istek.getOdemeTipi());
        yeniSatis.setToplamTutar(toplamTutar);

        Satis kaydedilenSatis = satisRepository.save(yeniSatis);

        // Detayları toplu kaydet — saveAll() ile tek batch INSERT
        List<SatisDetay> detaylar = new ArrayList<>();
        for (int i = 0; i < dogrulanmisUrunler.size(); i++) {
            Urun urun = dogrulanmisUrunler.get(i);
            SatisDetay detay = new SatisDetay();
            detay.setSatis(kaydedilenSatis);
            detay.setUrun(urun);
            detay.setAdet(adetler.get(i));
            detay.setSatilanFiyat(urun.getFiyat());
            detaylar.add(detay);
        }
        satisDetayRepository.saveAll(detaylar);

        // Audit log ve yedek: transaction dışı etkiler — exception satışı geri almasın
        try {
            auditLogger.logSalesTransaction(
                    kaydedilenSatis.getId(),
                    kasiyer.getKullaniciAdi(),
                    kaydedilenSatis.getToplamTutar()
            );
        } catch (Exception auditHata) {
            log.error("[SATIS] Audit log yazılamadı (satış kaydedildi): {}", auditHata.getMessage());
        }
        try {
            yedekService.yedekAl("satis");
        } catch (Exception yedekHata) {
            log.warn("[SATIS] Yedek alınamadı (satış kaydedildi): {}", yedekHata.getMessage());
        }

        // Satış özeti cache'ini geçersiz kıl — sonraki ozet isteği güncel veriyi çeker
        org.springframework.cache.Cache ozetCache = cacheManager.getCache("satisOzeti");
        if (ozetCache != null) ozetCache.evict(market.getId());

        Map<String, Object> yanit = new HashMap<>();
        yanit.put("mesaj", "Başarılı");
        yanit.put("satisId", kaydedilenSatis.getId());
        return yanit;
    }

    @GetMapping("/rapor/{marketId}")
    public Map<String, Object> raporGetir(
            @PathVariable Long marketId,
            @org.springframework.web.bind.annotation.RequestParam String baslangic,
            @org.springframework.web.bind.annotation.RequestParam String bitis) {

        guvenlikServisi.marketYetkiKontrolu(marketId);

        java.time.LocalDateTime bas;
        java.time.LocalDateTime bit;
        try {
            java.time.LocalDate basLd = java.time.LocalDate.parse(baslangic);
            java.time.LocalDate bitLd = java.time.LocalDate.parse(bitis);
            if (basLd.isAfter(bitLd)) {
                throw new IllegalArgumentException("Başlangıç tarihi bitiş tarihinden sonra olamaz!");
            }
            bas = basLd.atStartOfDay();
            bit = bitLd.atTime(23, 59, 59);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Geçersiz tarih formatı. YYYY-MM-DD kullanın.");
        }

        BigDecimal toplam = satisRepository.toplamCiroAralik(marketId, bas, bit);
        BigDecimal nakit  = satisRepository.nakitAralik(marketId, bas, bit);
        BigDecimal kart   = satisRepository.kartAralik(marketId, bas, bit);
        Long sayi         = satisRepository.sayiAralik(marketId, bas, bit);

        Map<String, Object> yanit = new HashMap<>();
        yanit.put("toplamCiro",  toplam != null ? toplam : BigDecimal.ZERO);
        yanit.put("nakitCiro",   nakit  != null ? nakit  : BigDecimal.ZERO);
        yanit.put("kartCiro",    kart   != null ? kart   : BigDecimal.ZERO);
        yanit.put("satisSayisi", sayi   != null ? sayi   : 0L);
        yanit.put("baslangic",   baslangic);
        yanit.put("bitis",       bitis);
        return yanit;
    }

    @GetMapping("/ozet/{marketId}")
    @SuppressWarnings("unchecked")
    public Map<String, Object> satisOzeti(@PathVariable Long marketId) {
        // Güvenlik kontrolü her zaman çalışır — cache proxy'sinin dışında
        guvenlikServisi.marketYetkiKontrolu(marketId);

        // Manuel cache: self-invocation sorunundan kaçınmak için @Cacheable yerine
        org.springframework.cache.Cache cache = cacheManager.getCache("satisOzeti");
        if (cache != null) {
            org.springframework.cache.Cache.ValueWrapper sarili = cache.get(marketId);
            if (sarili != null) return (Map<String, Object>) sarili.get();
        }

        Map<String, Object> yanit = new HashMap<>();
        BigDecimal toplam = satisRepository.toplamCiroHesapla(marketId);
        yanit.put("toplamCiro", toplam != null ? toplam : BigDecimal.ZERO);
        BigDecimal nakit = satisRepository.nakitToplamHesapla(marketId);
        yanit.put("nakitCiro", nakit != null ? nakit : BigDecimal.ZERO);
        BigDecimal kart = satisRepository.kartToplamHesapla(marketId);
        yanit.put("kartCiro", kart != null ? kart : BigDecimal.ZERO);
        Long sayisi = satisRepository.satisSayisiGetir(marketId);
        yanit.put("satisSayisi", sayisi != null ? sayisi : 0L);

        if (cache != null) cache.put(marketId, yanit);
        return yanit;
    }
}