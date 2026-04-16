package com.market.pos;

import com.market.pos.config.TestDataSourceConfig;
import com.market.pos.entity.Kullanici;
import com.market.pos.entity.Market;
import com.market.pos.entity.Urun;
import com.market.pos.repository.KullaniciRepository;
import com.market.pos.repository.MarketRepository;
import com.market.pos.repository.UrunRepository;
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
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CSV toplu ürün yükleme testleri.
 *
 * <p>Özellikle virgüllü ürün adı parse hatasının regresyon testi (daha önce
 * "1,5l Fanta" gibi adlar yanlış parse ediliyordu). Çakışma tespiti ve
 * tekrarlı barkod davranışı da doğrulanır.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestDataSourceConfig.class)
class CsvYuklemeTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private UrunRepository urunRepository;
    @Autowired private KullaniciRepository kullaniciRepository;
    @Autowired private MarketRepository marketRepository;
    @Autowired private BCryptPasswordEncoder passwordEncoder;

    @MockBean private YedekService yedekService;
    @MockBean private ExcelYedekService excelYedekService;
    @MockBean private VeriTabaniAnahtarService veriTabaniAnahtarService;

    private String jwtToken;
    private Long marketId;
    private Market market;

    // ── KURULUM ──────────────────────────────────────────────────────────────

    @BeforeEach
    void setUp() {
        urunRepository.deleteAll();
        kullaniciRepository.deleteAll();
        marketRepository.deleteAll();

        market = new Market();
        market.setMarketAdi("Test Market");
        market.setLisansBitisTarihi(LocalDate.now().plusYears(1));
        market = marketRepository.save(market);
        marketId = market.getId();

        Kullanici admin = new Kullanici();
        admin.setKullaniciAdi("testadmin");
        admin.setSifre(passwordEncoder.encode("Test1234!"));
        admin.setRol("ADMIN");
        admin.setMarket(market);
        admin.setAktif(true);
        kullaniciRepository.save(admin);

        Map<String, String> loginBody = Map.of("kullaniciAdi", "testadmin", "sifre", "Test1234!");
        ResponseEntity<Map> loginResp = restTemplate.postForEntity("/api/auth/giris", loginBody, Map.class);
        assertEquals(200, loginResp.getStatusCode().value());
        jwtToken = (String) loginResp.getBody().get("token");
    }

    @AfterEach
    void tearDown() {
        urunRepository.deleteAll();
        kullaniciRepository.deleteAll();
        marketRepository.deleteAll();
    }

    // ── YARDIMCILAR ──────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> csvYukle(String csvIcerik) {
        ByteArrayResource dosya = new ByteArrayResource(
                csvIcerik.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() { return "test.csv"; }
        };
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("dosya", dosya);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + jwtToken);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        return restTemplate.exchange(
                "/api/urunler/yukle/" + marketId,
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Map.class);
    }

    // ── TESTLER ──────────────────────────────────────────────────────────────

    /**
     * REGRESYON — virgüllü ürün adı, virgül ayırıcılı CSV.
     *
     * Eski kod: split(",") → "1,5l Fanta" 3 parçaya bölünürdü → parse hatası.
     * Yeni kod: tırnak içindeki virgüller ayırıcı sayılmaz.
     *
     * Örnek satır: 1234567890123,"1,5l Fanta",5.99
     */
    @Test
    void virgul_iceren_urun_adi_virgul_ayirici_ile_dogru_parse_edilir() {
        String csv = "Barkod,Isim,Fiyat\n"
                   + "1234567890123,\"1,5l Fanta\",5.99\n";

        ResponseEntity<Map> resp = csvYukle(csv);

        assertEquals(200, resp.getStatusCode().value());
        assertEquals(1, ((Number) resp.getBody().get("eklenen")).intValue(),
                "1 ürün eklenmeli — hata listesi: " + resp.getBody().get("hatalar"));

        Urun kayit = urunRepository.findByBarkodAndMarketId("1234567890123", marketId);
        assertNotNull(kayit, "Ürün DB'de olmalı");
        assertEquals("1,5l Fanta", kayit.getIsim(),
                "Ürün adı tırnak içindeki virgül korunarak kaydedilmeli");
        assertEquals(0, new BigDecimal("5.99").compareTo(kayit.getFiyat()),
                "Fiyat 5.99 olmalı");
    }

    /**
     * Noktalı virgül ayırıcısıyla normal CSV yüklemesi.
     */
    @Test
    void noktali_virgul_ayirici_dogru_calisir() {
        String csv = "Barkod;Isim;Fiyat\n"
                   + "9876543210987;Fanta;3.50\n";

        ResponseEntity<Map> resp = csvYukle(csv);

        assertEquals(200, resp.getStatusCode().value());
        assertEquals(1, ((Number) resp.getBody().get("eklenen")).intValue());

        Urun kayit = urunRepository.findByBarkodAndMarketId("9876543210987", marketId);
        assertNotNull(kayit);
        assertEquals("Fanta", kayit.getIsim());
        assertEquals(0, new BigDecimal("3.50").compareTo(kayit.getFiyat()));
    }

    /**
     * Noktalı virgül ayırıcı + tırnaklı ürün adı kombinasyonu.
     */
    @Test
    void noktali_virgul_ayirici_ile_tirnak_iceren_urun_adi() {
        String csv = "Barkod;Isim;Fiyat\n"
                   + "5555555555555;\"1,5l Cola\";7.25\n";

        ResponseEntity<Map> resp = csvYukle(csv);

        assertEquals(200, resp.getStatusCode().value());
        Urun kayit = urunRepository.findByBarkodAndMarketId("5555555555555", marketId);
        assertNotNull(kayit);
        assertEquals("1,5l Cola", kayit.getIsim());
    }

    /**
     * Mevcut ürün farklı fiyatla gelince çakışma olarak raporlanmalı,
     * mevcut veri DEĞİŞMEMELİ (kullanıcı onayı olmadan otomatik güncelleme yok).
     */
    @Test
    @SuppressWarnings("unchecked")
    void mevcut_urun_farkli_fiyat_cakisma_olarak_doner_degismez() {
        // DB'ye ürün ekle: fiyat 10.00
        Urun mevcut = new Urun();
        mevcut.setBarkod("1111111111111");
        mevcut.setIsim("Mevcut Ürün");
        mevcut.setFiyat(new BigDecimal("10.00"));
        mevcut.setMarket(market);
        urunRepository.save(mevcut);

        // Aynı barkod, farklı fiyatla CSV yükle
        String csv = "Barkod;Isim;Fiyat\n"
                   + "1111111111111;Mevcut Ürün;15.00\n";

        ResponseEntity<Map> resp = csvYukle(csv);

        assertEquals(200, resp.getStatusCode().value());
        List<Map<String, Object>> cakismalar = (List<Map<String, Object>>) resp.getBody().get("cakismalar");
        assertEquals(1, cakismalar.size(), "1 çakışma raporlanmalı");
        assertEquals("1111111111111", cakismalar.get(0).get("barkod"));

        // Mevcut fiyat değişmemiş olmalı
        Urun sonuc = urunRepository.findByBarkodAndMarketId("1111111111111", marketId);
        assertEquals(0, new BigDecimal("10.00").compareTo(sonuc.getFiyat()),
                "Çakışmada mevcut fiyat korunmalı, otomatik değişmemeli");
    }

    /**
     * Aynı CSV'de aynı barkod iki kez geçince SON değer DB'ye yazılmalı.
     */
    @Test
    void csv_de_tekrar_eden_barkod_son_deger_kazanir() {
        String csv = "Barkod;Isim;Fiyat\n"
                   + "7777777777777;Ilk Deger;5.00\n"
                   + "7777777777777;Son Deger;9.99\n";

        ResponseEntity<Map> resp = csvYukle(csv);

        assertEquals(200, resp.getStatusCode().value());
        // CSV'de 2 satır var ama aynı barkod → sadece 1 ürün eklenmeli
        assertEquals(1, ((Number) resp.getBody().get("eklenen")).intValue());

        Urun kayit = urunRepository.findByBarkodAndMarketId("7777777777777", marketId);
        assertNotNull(kayit);
        assertEquals("Son Deger", kayit.getIsim(),
                "Tekrar eden barkodda son değer kazanmalı");
        assertEquals(0, new BigDecimal("9.99").compareTo(kayit.getFiyat()));
    }

    /**
     * Birebir aynı ürün (barkod + isim + fiyat hepsi aynı) yüklenince
     * ne eklendi ne güncellendi sayılmalı — sessizce atlanır.
     */
    @Test
    void birebir_ayni_urun_atlanir() {
        Urun mevcut = new Urun();
        mevcut.setBarkod("3333333333333");
        mevcut.setIsim("Ayni Urun");
        mevcut.setFiyat(new BigDecimal("8.00"));
        mevcut.setMarket(market);
        urunRepository.save(mevcut);

        String csv = "Barkod;Isim;Fiyat\n"
                   + "3333333333333;Ayni Urun;8.00\n";

        ResponseEntity<Map> resp = csvYukle(csv);

        assertEquals(200, resp.getStatusCode().value());
        assertEquals(0, ((Number) resp.getBody().get("eklenen")).intValue(),
                "Birebir aynı ürün eklenmemeli");
        assertEquals(0, ((Number) resp.getBody().get("guncellenen")).intValue());
        List<?> cakismalar = (List<?>) resp.getBody().get("cakismalar");
        assertTrue(cakismalar.isEmpty(), "Birebir aynı ürün çakışma sayılmamalı");
    }
}
