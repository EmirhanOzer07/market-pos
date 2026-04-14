package com.market.pos.service;

import com.market.pos.entity.Market;
import com.market.pos.entity.Urun;
import com.market.pos.repository.MarketRepository;
import com.market.pos.repository.UrunRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Ürün listesini her gece 23:59'da .xlsx formatında yedekler.
 *
 * Her market için ayrı sayfa + "Tüm Ürünler" özet sayfası.
 * Klasörde en fazla 20 dosya saklanır (dönen yedekleme).
 * Google Drive klasörü → backup.excel.path ile ayarlanır.
 *
 * @Lazy(false): spring.main.lazy-initialization=true olsa dahi bu bean'in
 * başlangıçta oluşturulmasını zorunlu kılar — aksi halde @Scheduled ve
 * @PostConstruct hiç çalışmaz.
 */
@Service
@Lazy(false)
public class ExcelYedekService {

    private static final Logger log = LoggerFactory.getLogger(ExcelYedekService.class);
    private static final int    MAX_DOSYA    = 20;
    private static final DateTimeFormatter TARIH_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    @Value("${backup.excel.path:}")
    private String configKlasor;

    @Autowired private UrunRepository   urunRepository;
    @Autowired private MarketRepository marketRepository;

    // ─────────────────────────────────────────────
    // Başlangıç kontrolü — bugün Excel yedeği yoksa al
    // ─────────────────────────────────────────────
    @EventListener(ApplicationReadyEvent.class)
    public void baslangiçExcelYedegi() {
        new Thread(() -> {
            String bugun = java.time.LocalDate.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            Path klasor = klasorYolu();
            File[] bugunYedekleri = klasor.toFile().listFiles(
                    f -> f.getName().startsWith("urunler_" + bugun) && f.getName().endsWith(".xlsx"));
            if (bugunYedekleri == null || bugunYedekleri.length == 0) {
                log.info("[EXCEL YEDEK] Bugün için Excel yedeği yok, alınıyor...");
                try {
                    Path hedef = excelYedekAl();
                    log.info("[EXCEL YEDEK] ✓ Başlangıç yedeği alındı: {}", hedef.getFileName());
                } catch (Exception e) {
                    log.warn("[EXCEL YEDEK] Başlangıç yedeği alınamadı: {}", e.getMessage());
                }
            } else {
                log.info("[EXCEL YEDEK] Bugün için Excel yedeği mevcut: {}", bugunYedekleri[0].getName());
            }
        }, "baslangiç-excel-yedek").start();
    }

    // ─────────────────────────────────────────────
    // Zamanlayıcı — her gece 23:59
    // ─────────────────────────────────────────────
    @Scheduled(cron = "0 59 23 * * *")
    public void gunlukExcelYedegi() {
        log.info("[EXCEL YEDEK] Otomatik günlük yedek başlıyor...");
        try {
            Path hedef = excelYedekAl();
            log.info("[EXCEL YEDEK] ✓ Tamamlandı: {}", hedef.getFileName());
        } catch (Exception e) {
            log.error("[EXCEL YEDEK] Günlük yedek alınamadı: {}", e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────
    // Ana metot — hem zamanlayıcı hem endpoint çağırır
    // ─────────────────────────────────────────────
    @Transactional(readOnly = true)
    public Path excelYedekAl() throws IOException {
        Path klasor = klasorYolu();
        Files.createDirectories(klasor);

        String tarih = LocalDateTime.now().format(TARIH_FMT);
        Path hedef   = klasor.resolve("urunler_" + tarih + ".xlsx");

        List<Market> marketler = marketRepository.findAll();

        // XSSFWorkbook try-with-resources garantisi: hem workbook hem OutputStream
        // kapatılır — bellek sızıntısı ve dosya kilidi riski yok.
        try (XSSFWorkbook wb = new XSSFWorkbook()) {

            Stiller st = new Stiller(wb);

            // Her market için kendi sayfası
            for (Market market : marketler) {
                List<Urun> urunler = urunRepository.findByMarketId(market.getId());
                sayfaOlustur(wb, st, guvenliSayfaAdi(market.getMarketAdi()), urunler, false);
            }

            // Birden fazla market varsa "Tüm Ürünler" özet sayfası
            if (marketler.size() > 1) {
                List<Urun> tumUrunler = urunRepository.findAll();
                sayfaOlustur(wb, st, "Tüm Ürünler", tumUrunler, true);
            }

            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(hedef))) {
                wb.write(out);
            }
            // OutputStream ve Workbook burada kapatıldı
        }

        eskiDosyalariTemizle(klasor);
        return hedef;
    }

    // ─────────────────────────────────────────────
    // Sayfa oluştur
    // ─────────────────────────────────────────────
    private void sayfaOlustur(XSSFWorkbook wb, Stiller st, String sayfaAdi,
                               List<Urun> urunler, boolean marketSutunuGoster) {
        Sheet sayfa = wb.createSheet(sayfaAdi);

        // Başlık satırı
        String[] basliklar = marketSutunuGoster
                ? new String[]{"#", "Barkod", "Ürün Adı", "Fiyat (TL)", "Market"}
                : new String[]{"#", "Barkod", "Ürün Adı", "Fiyat (TL)"};

        Row baslikSatiri = sayfa.createRow(0);
        for (int i = 0; i < basliklar.length; i++) {
            Cell c = baslikSatiri.createCell(i);
            c.setCellValue(basliklar[i]);
            c.setCellStyle(st.baslik);
        }
        baslikSatiri.setHeightInPoints(22);

        // Veri satırları
        int satirNo = 1;
        for (Urun u : urunler) {
            Row satir = sayfa.createRow(satirNo);

            hucre(satir, 0, satirNo, st.normal);
            hucre(satir, 1, u.getBarkod(), st.normal);
            hucre(satir, 2, u.getIsim(), st.normal);
            hucreDouble(satir, 3, u.getFiyat().doubleValue(), st.fiyat);

            if (marketSutunuGoster) {
                hucre(satir, 4, u.getMarket().getMarketAdi(), st.normal);
            }
            satirNo++;
        }

        // Kolon genişlikleri (1 birim = 1/256 karakter)
        sayfa.setColumnWidth(0, 8 * 256);    // #
        sayfa.setColumnWidth(1, 20 * 256);   // Barkod
        sayfa.setColumnWidth(2, 36 * 256);   // Ürün Adı
        sayfa.setColumnWidth(3, 14 * 256);   // Fiyat
        if (marketSutunuGoster) sayfa.setColumnWidth(4, 26 * 256);

        // Başlık satırını dondur
        sayfa.createFreezePane(0, 1);

        // Otomatik filtre
        sayfa.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(
                0, 0, 0, basliklar.length - 1));
    }

    // ─────────────────────────────────────────────
    // Yardımcılar
    // ─────────────────────────────────────────────
    private void hucre(Row satir, int sutun, Object deger, CellStyle stil) {
        Cell c = satir.createCell(sutun);
        if (deger instanceof Number n) c.setCellValue(n.doubleValue());
        else c.setCellValue(deger == null ? "" : deger.toString());
        c.setCellStyle(stil);
    }

    private void hucreDouble(Row satir, int sutun, double deger, CellStyle stil) {
        Cell c = satir.createCell(sutun);
        c.setCellValue(deger);
        c.setCellStyle(stil);
    }

    /** Excel sayfa adı: max 31 karakter, özel karakter yok */
    private String guvenliSayfaAdi(String isim) {
        String temiz = isim.replaceAll("[\\\\/*?\\[\\]:']", "_").trim();
        return temiz.length() > 31 ? temiz.substring(0, 31) : temiz;
    }

    private Path klasorYolu() {
        String yol = (configKlasor != null && !configKlasor.isBlank())
                ? configKlasor
                : System.getProperty("user.home") + "/AppData/Local/MarketPOS/yedek/excel";
        return Paths.get(yol.replace("/", "\\"));
    }

    /** Klasördeki .xlsx sayısı MAX_DOSYA'yı geçince en eskiyi sil */
    private void eskiDosyalariTemizle(Path klasor) {
        try {
            File[] dosyalar = klasor.toFile().listFiles(
                    f -> f.isFile() && f.getName().endsWith(".xlsx"));
            if (dosyalar == null || dosyalar.length <= MAX_DOSYA) return;

            // En eski dosya önce gelsin (lastModified küçükten büyüğe)
            Arrays.sort(dosyalar, Comparator.comparingLong(File::lastModified));
            int silinecek = dosyalar.length - MAX_DOSYA;
            for (int i = 0; i < silinecek; i++) {
                if (dosyalar[i].delete()) {
                    log.info("[EXCEL YEDEK] Eski dosya silindi: {}", dosyalar[i].getName());
                } else {
                    log.warn("[EXCEL YEDEK] Dosya silinemedi: {}", dosyalar[i].getName());
                }
            }
        } catch (Exception e) {
            log.warn("[EXCEL YEDEK] Temizleme hatası: {}", e.getMessage());
        }
    }

    // ─────────────────────────────────────────────
    // Liste — YedekController için
    // ─────────────────────────────────────────────
    public List<Map<String, Object>> excelListesi() {
        Path klasor = klasorYolu();
        List<Map<String, Object>> liste = new ArrayList<>();
        if (!Files.exists(klasor)) return liste;

        File[] dosyalar = klasor.toFile().listFiles(
                f -> f.isFile() && f.getName().endsWith(".xlsx"));
        if (dosyalar == null) return liste;

        // En yeni önce
        Arrays.sort(dosyalar, Comparator.comparingLong(File::lastModified).reversed());
        for (File f : dosyalar) {
            Map<String, Object> bilgi = new LinkedHashMap<>();
            bilgi.put("dosyaAdi", f.getName());
            bilgi.put("boyutKB",  f.length() / 1024);
            bilgi.put("tarih",    tarihCikart(f.getName()));
            liste.add(bilgi);
        }
        return liste;
    }

    /** "urunler_2026-04-12_23-59-00.xlsx" → "2026-04-12 23:59:00" */
    private String tarihCikart(String dosyaAdi) {
        try {
            String govde = dosyaAdi.replace("urunler_", "").replace(".xlsx", "");
            // "2026-04-12_23-59-00" → "2026-04-12 23:59:00"
            String[] p = govde.split("_", 2);
            if (p.length == 2) return p[0] + " " + p[1].replace("-", ":");
        } catch (Exception ignored) {}
        return "";
    }

    public String klasorYoluStr() {
        return klasorYolu().toString();
    }

    // ─────────────────────────────────────────────
    // Stil nesneleri (iç sınıf — Workbook ile birlikte yaşar)
    // ─────────────────────────────────────────────
    private static class Stiller {
        final CellStyle baslik;
        final CellStyle normal;
        final CellStyle fiyat;

        Stiller(XSSFWorkbook wb) {
            baslik = baslikStili(wb);
            normal = normalStili(wb);
            fiyat  = fiyatStili(wb);
        }

        private static CellStyle baslikStili(XSSFWorkbook wb) {
            XSSFCellStyle stil = wb.createCellStyle();
            XSSFFont font = wb.createFont();
            font.setBold(true);
            font.setFontHeightInPoints((short) 11);
            // Beyaz yazı
            font.setColor(new XSSFColor(new byte[]{(byte) 255, (byte) 255, (byte) 255}, null));
            stil.setFont(font);
            // Koyu lacivert arka plan (#2c3e50)
            stil.setFillForegroundColor(
                    new XSSFColor(new byte[]{(byte) 44, (byte) 62, (byte) 80}, null));
            stil.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            stil.setAlignment(HorizontalAlignment.CENTER);
            stil.setVerticalAlignment(VerticalAlignment.CENTER);
            kenarlık(stil);
            return stil;
        }

        private static CellStyle normalStili(XSSFWorkbook wb) {
            CellStyle stil = wb.createCellStyle();
            kenarlık(stil);
            return stil;
        }

        private static CellStyle fiyatStili(XSSFWorkbook wb) {
            CellStyle stil = wb.createCellStyle();
            stil.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));
            stil.setAlignment(HorizontalAlignment.RIGHT);
            kenarlık(stil);
            return stil;
        }

        private static void kenarlık(CellStyle s) {
            s.setBorderTop(BorderStyle.THIN);
            s.setBorderBottom(BorderStyle.THIN);
            s.setBorderLeft(BorderStyle.THIN);
            s.setBorderRight(BorderStyle.THIN);
        }
    }
}
