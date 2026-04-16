package com.market.pos;

import com.market.pos.config.TestDataSourceConfig;
import com.market.pos.entity.Kullanici;
import com.market.pos.entity.Market;
import com.market.pos.entity.Urun;
import com.market.pos.repository.*;
import com.market.pos.service.ExcelYedekService;
import com.market.pos.service.VeriTabaniAnahtarService;
import com.market.pos.service.YedekService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * SatisController.satisTamamla() için entegrasyon testleri.
 *
 * <p>Para akışının kritik noktaları test edilir:
 * başarılı kayıt, transaction rollback, fiyat güvenliği,
 * lisans kontrolü ve girdi doğrulaması.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestDataSourceConfig.class)
class SatisTransactionTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private SatisRepository satisRepository;
    @Autowired private UrunRepository urunRepository;
    @Autowired private KullaniciRepository kullaniciRepository;
    @Autowired private MarketRepository marketRepository;
    @Autowired private BCryptPasswordEncoder passwordEncoder;

    // SpyBean: gerçek bean'i sarmalar; doThrow ile seçici olarak hata fırlatılabilir
    @SpyBean  private SatisDetayRepository satisDetayRepository;

    // Dosya sistemi bağımlılıklarını mockla — test ortamında AppData yazmayı engeller
    @MockBean private YedekService yedekService;
    @MockBean private ExcelYedekService excelYedekService;
    @MockBean private VeriTabaniAnahtarService veriTabaniAnahtarService;

    private String jwtToken;
    private Long marketId;
    private Urun urunA;  // fiyat: 10.50
    private Urun urunB;  // fiyat:  5.25

    // ── KURULUM ──────────────────────────────────────────────────────────────

    @BeforeEach
    void setUp() {
        Mockito.reset(satisDetayRepository); // önceki test'in doThrow'unu sıfırla
        temizle();

        Market market = new Market();
        market.setMarketAdi("Test Market");
        market.setLisansBitisTarihi(LocalDate.now().plusYears(1));
        market = marketRepository.save(market);
        marketId = market.getId();

        Kullanici kasiyer = new Kullanici();
        kasiyer.setKullaniciAdi("testkasiyer");
        kasiyer.setSifre(passwordEncoder.encode("Test1234!"));
        kasiyer.setRol("KASIYER");
        kasiyer.setMarket(market);
        kasiyer.setAktif(true);
        kullaniciRepository.save(kasiyer);

        urunA = urunKaydet("1111111111111", "Ürün A", "10.50", market);
        urunB = urunKaydet("2222222222222", "Ürün B", "5.25",  market);

        jwtToken = login("testkasiyer", "Test1234!");
    }

    @AfterEach
    void tearDown() {
        Mockito.reset(satisDetayRepository);
        temizle();
    }

    // ── YARDIMCILAR ──────────────────────────────────────────────────────────

    private void temizle() {
        satisDetayRepository.deleteAll();
        satisRepository.deleteAll();
        urunRepository.deleteAll();
        kullaniciRepository.deleteAll();
        marketRepository.deleteAll();
    }

    private Urun urunKaydet(String barkod, String isim, String fiyat, Market market) {
        Urun u = new Urun();
        u.setBarkod(barkod);
        u.setIsim(isim);
        u.setFiyat(new BigDecimal(fiyat));
        u.setMarket(market);
        return urunRepository.save(u);
    }

    private String login(String kullaniciAdi, String sifre) {
        Map<String, String> body = Map.of("kullaniciAdi", kullaniciAdi, "sifre", sifre);
        ResponseEntity<Map> resp = restTemplate.postForEntity("/api/auth/giris", body, Map.class);
        assertEquals(200, resp.getStatusCode().value(), "Login başarısız olmamalı: " + resp.getBody());
        return (String) resp.getBody().get("token");
    }

    private ResponseEntity<Map> satisYap(String odemeTipi, List<Map<String, Object>> sepet) {
        Map<String, Object> istek = new HashMap<>();
        istek.put("odemeTipi", odemeTipi);
        istek.put("sepet", sepet);
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + jwtToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(
                "/api/satis/tamamla", HttpMethod.POST,
                new HttpEntity<>(istek, headers), Map.class);
    }

    private List<Map<String, Object>> sepet(Object... barkodAdetCiftleri) {
        // Kullanım: sepet("barkod1", 2.0, "barkod2", 1.0)
        List<Map<String, Object>> liste = new java.util.ArrayList<>();
        for (int i = 0; i < barkodAdetCiftleri.length; i += 2) {
            Map<String, Object> item = new HashMap<>();
            item.put("barkod", barkodAdetCiftleri[i]);
            item.put("adet",   barkodAdetCiftleri[i + 1]);
            liste.add(item);
        }
        return liste;
    }

    // ── TESTLER ──────────────────────────────────────────────────────────────

    /**
     * Normal nakit satış: Satis + SatisDetay doğru kaydedilmeli,
     * toplam tutar BigDecimal hassasiyetiyle doğru hesaplanmalı.
     * 10.50 × 2 + 5.25 × 1 = 26.25
     */
    @Test
    void nakit_satis_dogru_kaydedilir() {
        ResponseEntity<Map> resp = satisYap("NAKIT",
                sepet(urunA.getBarkod(), 2.0, urunB.getBarkod(), 1.0));

        assertEquals(200, resp.getStatusCode().value());
        assertNotNull(resp.getBody().get("satisId"), "satisId dönmeli");
        assertEquals(1, satisRepository.count(),       "Tam olarak 1 Satis kaydı olmalı");
        assertEquals(2, satisDetayRepository.count(),  "2 SatisDetay kaydı olmalı");

        BigDecimal beklenen = new BigDecimal("26.25");
        BigDecimal gercek   = satisRepository.findAll().get(0).getToplamTutar();
        assertEquals(0, beklenen.compareTo(gercek),
                "Toplam tutar yanlış. Beklenen: " + beklenen + " Gerçek: " + gercek);
    }

    /**
     * Kart ödemesi de nakit ile aynı şekilde kaydedilmeli,
     * ödeme tipi 'KART' olarak persist edilmeli.
     */
    @Test
    void kart_satisi_dogru_kaydedilir() {
        ResponseEntity<Map> resp = satisYap("KART", sepet(urunA.getBarkod(), 1.0));

        assertEquals(200, resp.getStatusCode().value());
        assertEquals("KART", satisRepository.findAll().get(0).getOdemeTipi());
    }

    /**
     * SepetUrunDTO'da fiyat alanı yoktur — istemci fiyat gönderemez.
     * SatisDetay.satilanFiyat, veritabanındaki güncel fiyatla eşleşmeli.
     * Fiyat manipülasyonuna karşı temel güvence.
     */
    @Test
    void satilan_fiyat_veritabanindan_alinir() {
        satisYap("NAKIT", sepet(urunA.getBarkod(), 1.0));

        var detaylar = satisDetayRepository.findAll();
        assertEquals(1, detaylar.size());
        assertEquals(0, urunA.getFiyat().compareTo(detaylar.get(0).getSatilanFiyat()),
                "satilanFiyat DB fiyatıyla eşleşmeli. DB: "
                        + urunA.getFiyat() + " Kaydedilen: " + detaylar.get(0).getSatilanFiyat());
    }

    /**
     * @Transactional rollback testi — kritik!
     * SatisDetay kaydı başarısız olursa ana Satis kaydı da geri alınmalı.
     * Yarım satış verisi kesinlikle DB'de kalmamalı.
     */
    @Test
    void detay_kaydi_basarisiz_olursa_ana_satis_rollback_olur() {
        doThrow(new RuntimeException("Test: DB hatası simülasyonu"))
                .when(satisDetayRepository).saveAll(any());

        ResponseEntity<Map> resp = satisYap("NAKIT", sepet(urunA.getBarkod(), 1.0));

        assertEquals(500, resp.getStatusCode().value(),
                "Rollback senaryosunda sunucu hatası (500) dönmeli");
        assertEquals(0, satisRepository.count(),
                "Rollback: Satis tablosunda kayıt KALMAMALI — yarım satış tehlikelidir");
    }

    /**
     * Boş sepet hem @NotEmpty ile bean validation'da hem controller'da korunmalı.
     */
    @Test
    void bos_sepet_400_doner() {
        Map<String, Object> istek = Map.of("odemeTipi", "NAKIT", "sepet", List.of());
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + jwtToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> resp = restTemplate.exchange(
                "/api/satis/tamamla", HttpMethod.POST,
                new HttpEntity<>(istek, headers), Map.class);

        assertEquals(400, resp.getStatusCode().value());
        assertEquals(0, satisRepository.count());
    }

    /**
     * NAKIT ve KART dışında hiçbir ödeme tipi kabul edilmemeli.
     */
    @Test
    void gecersiz_odeme_tipi_400_doner() {
        ResponseEntity<Map> resp = satisYap("TAKSIT", sepet(urunA.getBarkod(), 1.0));
        assertEquals(400, resp.getStatusCode().value());
        assertEquals(0, satisRepository.count());
    }

    /**
     * Var olmayan barkod sepette ise satış tamamlanmamalı.
     */
    @Test
    void var_olmayan_barkod_400_doner() {
        ResponseEntity<Map> resp = satisYap("NAKIT", sepet("0000000000000", 1.0));
        assertEquals(400, resp.getStatusCode().value());
        assertEquals(0, satisRepository.count());
    }

    /**
     * Lisansı sona ermiş markette satış yapılamaz.
     * Login sonrası lisans expire edilebilir — JWT zaten verildi,
     * ama SatisController her istekte güncel lisansı kontrol eder.
     */
    @Test
    void lisans_dolmus_satis_engellenir() {
        Market market = marketRepository.findById(marketId).orElseThrow();
        market.setLisansBitisTarihi(LocalDate.now().minusDays(1));
        marketRepository.save(market);

        ResponseEntity<Map> resp = satisYap("NAKIT", sepet(urunA.getBarkod(), 1.0));
        assertEquals(400, resp.getStatusCode().value());
        assertEquals(0, satisRepository.count());
    }

    /**
     * Negatif adet @DecimalMin("0.001") ile bean validation'da yakalanmalı.
     */
    @Test
    void negatif_adet_400_doner() {
        ResponseEntity<Map> resp = satisYap("NAKIT", sepet(urunA.getBarkod(), -1.0));
        assertEquals(400, resp.getStatusCode().value());
        assertEquals(0, satisRepository.count());
    }

    /**
     * Sıfır adet de @DecimalMin("0.001") kapsamına girer.
     */
    @Test
    void sifir_adet_400_doner() {
        ResponseEntity<Map> resp = satisYap("NAKIT", sepet(urunA.getBarkod(), 0.0));
        assertEquals(400, resp.getStatusCode().value());
        assertEquals(0, satisRepository.count());
    }
}
