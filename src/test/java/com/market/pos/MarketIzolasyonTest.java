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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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

/**
 * Market izolasyonu güvenlik testleri.
 *
 * <p>Market A kullanıcısının Market B verilerine erişememesini doğrular.
 * Bu testler başarısız olursa tüm market verileri tehlikede demektir.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestDataSourceConfig.class)
class MarketIzolasyonTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private UrunRepository urunRepository;
    @Autowired private KullaniciRepository kullaniciRepository;
    @Autowired private MarketRepository marketRepository;
    @Autowired private SatisRepository satisRepository;
    @Autowired private SatisDetayRepository satisDetayRepository;
    @Autowired private BCryptPasswordEncoder passwordEncoder;

    @MockBean private YedekService yedekService;
    @MockBean private ExcelYedekService excelYedekService;
    @MockBean private VeriTabaniAnahtarService veriTabaniAnahtarService;

    private String tokenA;    // Market A kullanıcısının JWT tokeni
    private Long marketAId;
    private Long marketBId;
    private String barkodB;   // Market B'ye ait ürünün barkodu — Market A'dan erişilemez

    // ── KURULUM ──────────────────────────────────────────────────────────────

    @BeforeEach
    void setUp() {
        temizle();

        // Market A
        Market marketA = new Market();
        marketA.setMarketAdi("Market A");
        marketA.setLisansBitisTarihi(LocalDate.now().plusYears(1));
        marketA = marketRepository.save(marketA);
        marketAId = marketA.getId();

        Kullanici kullaniciA = new Kullanici();
        kullaniciA.setKullaniciAdi("kullanici_a");
        kullaniciA.setSifre(passwordEncoder.encode("Test1234!"));
        kullaniciA.setRol("ADMIN");
        kullaniciA.setMarket(marketA);
        kullaniciA.setAktif(true);
        kullaniciRepository.save(kullaniciA);

        Urun urunA = new Urun();
        urunA.setBarkod("AAAAAAAAAAAAA");
        urunA.setIsim("Market A Ürünü");
        urunA.setFiyat(new BigDecimal("10.00"));
        urunA.setMarket(marketA);
        urunRepository.save(urunA);

        // Market B — ayrı market, Market A kullanıcısı erişemez
        Market marketB = new Market();
        marketB.setMarketAdi("Market B");
        marketB.setLisansBitisTarihi(LocalDate.now().plusYears(1));
        marketB = marketRepository.save(marketB);
        marketBId = marketB.getId();

        barkodB = "BBBBBBBBBBBBB";
        Urun urunB = new Urun();
        urunB.setBarkod(barkodB);
        urunB.setIsim("Market B Ürünü");
        urunB.setFiyat(new BigDecimal("20.00"));
        urunB.setMarket(marketB);
        urunRepository.save(urunB);

        tokenA = login("kullanici_a", "Test1234!");
    }

    @AfterEach
    void tearDown() {
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

    private String login(String kullaniciAdi, String sifre) {
        Map<String, String> body = Map.of("kullaniciAdi", kullaniciAdi, "sifre", sifre);
        ResponseEntity<Map> resp = restTemplate.postForEntity("/api/auth/giris", body, Map.class);
        assertEquals(200, resp.getStatusCode().value(), "Login başarısız: " + resp.getBody());
        return (String) resp.getBody().get("token");
    }

    private HttpHeaders authHeader(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        return headers;
    }

    // ── TESTLER ──────────────────────────────────────────────────────────────

    /**
     * Market A kullanıcısı Market B'nin ürün listesini görememeli.
     * marketYetkiKontrolu() SecurityException fırlatır → 403.
     */
    @Test
    void marketA_kullanicisi_marketB_urunlerini_goremez() {
        ResponseEntity<String> resp = restTemplate.exchange(
                "/api/urunler/liste/" + marketBId,
                HttpMethod.GET,
                new HttpEntity<>(authHeader(tokenA)),
                String.class);

        assertEquals(403, resp.getStatusCode().value(),
                "Başka market ürün listesi erişimi 403 dönmeli");
    }

    /**
     * Market A kullanıcısı Market B'nin ürünüyle satış yapamamalı.
     * findByBarkodInAndMarketId() Market A'da Market B barkoduyla ürün bulamaz → 400.
     * Bu test, fiyat/stok manipülasyonuna karşı kritik güvencedir.
     */
    @Test
    void marketA_kullanicisi_marketB_urunu_ile_satis_yapamaz() {
        Map<String, Object> istek = new HashMap<>();
        istek.put("odemeTipi", "NAKIT");
        istek.put("sepet", List.of(Map.of("barkod", barkodB, "adet", 1.0)));

        HttpHeaders headers = authHeader(tokenA);
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> resp = restTemplate.exchange(
                "/api/satis/tamamla", HttpMethod.POST,
                new HttpEntity<>(istek, headers), Map.class);

        assertEquals(400, resp.getStatusCode().value(),
                "Başka market ürünüyle satış denemesi 400 dönmeli");
        assertEquals(0, satisRepository.count(),
                "Satış kaydı oluşmamalı");
    }

    /**
     * JWT olmadan gelen istekler korumalı endpointlere erişememeli.
     */
    @Test
    void jwt_olmadan_istek_reddedilir() {
        ResponseEntity<String> resp = restTemplate.getForEntity(
                "/api/urunler/liste/" + marketAId, String.class);

        assertTrue(resp.getStatusCode().value() >= 400,
                "JWT olmadan istek başarılı olmamalı (401 veya 403 beklenir)");
    }
}
