package com.market.pos.controller;

import com.market.pos.entity.*;
import com.market.pos.repository.*;
import com.market.pos.dto.SatisIstegi;
import com.market.pos.dto.SepetUrunDTO;
import com.market.pos.security.AuditLogger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
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
    @Autowired private YedekService yedekService;
    @Autowired private AuditLogger auditLogger;
    @Autowired private SatisRepository satisRepository;
    @Autowired private SatisDetayRepository satisDetayRepository;
    @Autowired private MarketRepository marketRepository;
    @Autowired private KullaniciRepository kullaniciRepository;
    @Autowired private UrunRepository urunRepository;

    private Kullanici getAktifKullanici() {
        String kullaniciAdi = (String) SecurityContextHolder
                .getContext().getAuthentication().getPrincipal();
        return kullaniciRepository.findByKullaniciAdi(kullaniciAdi);
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
        if (market.getLisansBitisTarihi() != null &&
                market.getLisansBitisTarihi().isBefore(java.time.LocalDate.now())) {
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

        // Tek döngüde doğrulama ve toplam hesaplama — tutarlılık garantisi
        BigDecimal toplamTutar = BigDecimal.ZERO;
        List<Urun> dogrulanmisUrunler = new ArrayList<>();
        List<Double> adetler = new ArrayList<>();

        for (SepetUrunDTO item : istek.getSepet()) {

            // Adet kontrolü
            if (item.getAdet() == null || item.getAdet() <= 0) {
                throw new IllegalArgumentException("Geçersiz adet değeri!");
            }

            // Ürün bu markete ait mi? (barkod üzerinden güvenli doğrulama)
            Urun gercekUrun = urunRepository.findByBarkodAndMarketId(
                    item.getBarkod(), market.getId()
            );
            if (gercekUrun == null) {
                throw new IllegalArgumentException("Ürün bu markette bulunamadı: " + item.getBarkod());
            }

            BigDecimal adet = BigDecimal.valueOf(item.getAdet());
            toplamTutar = toplamTutar.add(gercekUrun.getFiyat().multiply(adet));

            dogrulanmisUrunler.add(gercekUrun);
            adetler.add(item.getAdet());
        }

        // Ana satışı kaydet
        Satis yeniSatis = new Satis();
        yeniSatis.setMarket(market);
        yeniSatis.setKullanici(kasiyer);
        yeniSatis.setOdemeTipi(istek.getOdemeTipi());
        yeniSatis.setTarih(new java.util.Date());
        yeniSatis.setToplamTutar(toplamTutar);

        Satis kaydedilenSatis = satisRepository.save(yeniSatis);

        // Detayları kaydet — doğrulanmış listeden, tutarsızlık yok
        for (int i = 0; i < dogrulanmisUrunler.size(); i++) {
            Urun urun = dogrulanmisUrunler.get(i);
            SatisDetay detay = new SatisDetay();
            detay.setSatis(kaydedilenSatis);
            detay.setUrun(urun);
            detay.setAdet(adetler.get(i));
            detay.setSatilanFiyat(urun.getFiyat());
            satisDetayRepository.save(detay);
        }

        auditLogger.logSalesTransaction(
                kaydedilenSatis.getId(),
                kasiyer.getKullaniciAdi(),
                kaydedilenSatis.getToplamTutar()
        );
        yedekService.yedekAl("satis");

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

        Kullanici gercekKullanici = getAktifKullanici();
        if (!gercekKullanici.getMarket().getId().equals(marketId)) {
            throw new SecurityException("Başka marketin verisine erişemezsiniz!");
        }

        try {
            java.time.LocalDate basLd = java.time.LocalDate.parse(baslangic);
            java.time.LocalDate bitLd = java.time.LocalDate.parse(bitis);

            if (basLd.isAfter(bitLd)) {
                throw new IllegalArgumentException("Başlangıç tarihi bitiş tarihinden sonra olamaz!");
            }
            java.util.Date bas = java.util.Date.from(
                    basLd.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant());
            java.util.Date bit = java.util.Date.from(
                    bitLd.atTime(23, 59, 59).atZone(java.time.ZoneId.systemDefault()).toInstant());

            Map<String, Object> yanit = new HashMap<>();
            BigDecimal toplam = satisRepository.toplamCiroAralik(marketId, bas, bit);
            BigDecimal nakit  = satisRepository.nakitAralik(marketId, bas, bit);
            BigDecimal kart   = satisRepository.kartAralik(marketId, bas, bit);
            Long sayi         = satisRepository.sayiAralik(marketId, bas, bit);

            yanit.put("toplamCiro",  toplam != null ? toplam : BigDecimal.ZERO);
            yanit.put("nakitCiro",   nakit  != null ? nakit  : BigDecimal.ZERO);
            yanit.put("kartCiro",    kart   != null ? kart   : BigDecimal.ZERO);
            yanit.put("satisSayisi", sayi   != null ? sayi   : 0L);
            yanit.put("baslangic",   baslangic);
            yanit.put("bitis",       bitis);
            return yanit;
        } catch (Exception e) {
            throw new IllegalArgumentException("Geçersiz tarih formatı. YYYY-MM-DD kullanın.");
        }
    }

    @GetMapping("/ozet/{marketId}")
    public Map<String, Object> satisOzeti(@PathVariable Long marketId) {

        Kullanici gercekKullanici = getAktifKullanici();
        if (!gercekKullanici.getMarket().getId().equals(marketId)) {
            throw new SecurityException("Başka marketin verisine erişemezsiniz!");
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

        return yanit;
    }
}