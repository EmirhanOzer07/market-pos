package com.market.pos.ekran;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.*;

public class KasaEkrani {

    private static final Logger log = LoggerFactory.getLogger(KasaEkrani.class);

    private final Stage stage;
    private TextField barkodField;
    private Label toplamLabel;
    private Label sonEklenenLabel;
    private TableView<SepetSatir> sepetTablosu;
    private ObservableList<SepetSatir> sepetVerisi;
    private boolean islemYapiliyor = false;
    private Stage aktifBildirim;
    private Label urunSayisiLabel;

    private final List<String> bulunamayanBarkodlar = new ArrayList<>();
    private Button bulunamayanBtn;

    // hizliUrunler: her eleman [barkod, butonIsmi]
    private final List<String[]> hizliUrunler = new ArrayList<>();
    private FlowPane hizliUrunlerPanel;
    private static final java.nio.file.Path HIZLI_URUNLER_DOSYASI = java.nio.file.Paths.get(
            System.getProperty("user.home"), "AppData", "Local", "MarketPOS", "hizliurunler.txt");

    public KasaEkrani(Stage stage) {
        this.stage = stage;
    }

    // ===== SEPET SATIRI =====
    public static class SepetSatir {
        private String barkod;
        private String urunAdi;
        private double adet;
        private BigDecimal fiyat;
        private BigDecimal tutar;
        private Long urunId;

        public SepetSatir(String barkod, String urunAdi, double adet,
                          BigDecimal fiyat, Long urunId) {
            this.barkod = barkod;
            this.urunAdi = urunAdi;
            this.adet = adet;
            this.fiyat = fiyat;
            this.tutar = fiyat.multiply(BigDecimal.valueOf(adet));
            this.urunId = urunId;
        }

        public String getBarkod() { return barkod; }
        public String getUrunAdi() { return urunAdi; }
        public double getAdet() { return adet; }
        public BigDecimal getFiyat() { return fiyat; }
        public BigDecimal getTutar() { return tutar; }
        public Long getUrunId() { return urunId; }

        public void setAdet(double adet) {
            this.adet = adet;
            this.tutar = fiyat.multiply(BigDecimal.valueOf(adet));
        }
    }

    public Scene olustur() {
        boolean koyu = AyarYoneticisi.isKoyu();
        String sayfaArka  = koyu ? "#111827" : "#eef1f5";
        String panelArka  = koyu ? AyarYoneticisi.formRengi() : "#ffffff";
        String kenarRenk  = koyu ? AyarYoneticisi.kenarlık() : "#e2e8f0";
        String metin      = AyarYoneticisi.metinRengi();
        String ikincil    = AyarYoneticisi.ikincilMetin();
        String bolumStil  = "-fx-border-color: " + kenarRenk + "; -fx-border-width: 0 0 1 0;";

        // ── ÜST BAR ─────────────────────────────────────────────────────────────
        Label baslikLabel = new Label("🏪  EXPRESS SATIŞ");
        baslikLabel.setFont(Font.font("Arial", FontWeight.BOLD, 15));
        baslikLabel.setTextFill(Color.WHITE);

        Label versiyon = new Label("v1.4.0");
        versiyon.setFont(Font.font("Arial", 11));
        versiyon.setTextFill(Color.web("#475569"));

        Label kullaniciLabel = new Label("👤  " + ApiClient.getKullaniciAdi());
        kullaniciLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        kullaniciLabel.setTextFill(Color.web("#94a3b8"));
        kullaniciLabel.setStyle("-fx-background-color: #1e293b; -fx-background-radius: 6; -fx-padding: 5 12;");

        String ustBtnStyle = "-fx-background-radius: 6; -fx-cursor: hand; -fx-font-size: 12px; -fx-font-weight: bold; -fx-padding: 6 14;";

        bulunamayanBtn = new Button("⚠  Bulunamayan [0]");
        bulunamayanBtn.setStyle("-fx-background-color: #b45309; -fx-text-fill: white; " + ustBtnStyle);
        bulunamayanBtn.setVisible(false);

        boolean sesAcik = AyarYoneticisi.isSesAcik();
        Button sesBtn = new Button(sesAcik ? "🔊  Ses Açık" : "🔇  Ses Kapalı");
        sesBtn.setStyle("-fx-background-color: " + (sesAcik ? "#166534" : "#374151") + "; -fx-text-fill: white; " + ustBtnStyle);
        sesBtn.setTooltip(new Tooltip(sesAcik ? "Sesi Kapat" : "Sesi Aç"));
        sesBtn.setOnAction(e -> {
            AyarYoneticisi.sesToggle();
            boolean d = AyarYoneticisi.isSesAcik();
            sesBtn.setText(d ? "🔊  Ses Açık" : "🔇  Ses Kapalı");
            sesBtn.setStyle("-fx-background-color: " + (d ? "#166534" : "#374151") + "; -fx-text-fill: white; " + ustBtnStyle);
        });

        Button temaBtn = new Button(koyu ? "☀  Açık Tema" : "🌙  Koyu Tema");
        temaBtn.setStyle("-fx-background-color: #334155; -fx-text-fill: white; " + ustBtnStyle);
        temaBtn.setOnAction(e -> {
            if (!sepetVerisi.isEmpty()) {
                Alert onay = new Alert(Alert.AlertType.CONFIRMATION);
                onay.initOwner(stage);
                onay.setTitle("Tema Değiştir");
                onay.setHeaderText(null);
                onay.setContentText("Tema değişikliği için ekran yeniden yüklenecek.\nSepetteki ürünler silinecek. Devam edilsin mi?");
                ButtonType devam = new ButtonType("Devam Et", ButtonBar.ButtonData.OK_DONE);
                ButtonType iptal = new ButtonType("İptal", ButtonBar.ButtonData.CANCEL_CLOSE);
                onay.getButtonTypes().setAll(devam, iptal);
                onay.showAndWait().ifPresent(s -> { if (s == devam) { AyarYoneticisi.temaToggle(); stage.setScene(new KasaEkrani(stage).olustur()); } });
            } else { AyarYoneticisi.temaToggle(); stage.setScene(new KasaEkrani(stage).olustur()); }
        });

        Button yonetimBtn = new Button("⚙  Yönetim Paneli");
        yonetimBtn.setStyle("-fx-background-color: #1d4ed8; -fx-text-fill: white; " + ustBtnStyle);
        yonetimBtn.setVisible("ADMIN".equals(ApiClient.getRol()));
        yonetimBtn.setManaged("ADMIN".equals(ApiClient.getRol()));

        Button cikisBtn = new Button("🚪  Çıkış Yap");
        cikisBtn.setStyle("-fx-background-color: #be123c; -fx-text-fill: white; " + ustBtnStyle);

        Region ustBosluk = new Region();
        HBox.setHgrow(ustBosluk, Priority.ALWAYS);

        HBox solBaslik = new HBox(8, baslikLabel, versiyon);
        solBaslik.setAlignment(Pos.CENTER_LEFT);

        HBox ustBar = new HBox(10, solBaslik, ustBosluk,
                kullaniciLabel, bulunamayanBtn, sesBtn, temaBtn, yonetimBtn, cikisBtn);
        ustBar.setAlignment(Pos.CENTER_LEFT);
        ustBar.setPadding(new Insets(10, 16, 10, 16));
        ustBar.setStyle("-fx-background-color: #0f172a; -fx-border-color: #1e293b; -fx-border-width: 0 0 1 0;");

        // ── BARKOD ALANI (tam genişlik) ──────────────────────────────────────────
        Label barkodLbl = new Label("🔍");
        barkodLbl.setFont(Font.font("Arial", 16));
        barkodLbl.setTextFill(Color.web(koyu ? "#64748b" : "#94a3b8"));

        barkodField = new TextField();
        barkodField.setPromptText("Barkod okutun veya  2 * barkod  yazın...");
        barkodField.setPrefHeight(46);
        barkodField.setFont(Font.font("Arial", 15));
        String bfArka = koyu ? AyarYoneticisi.inputArka() : "white";
        barkodField.setStyle(
                "-fx-background-color: " + bfArka + "; " +
                "-fx-control-inner-background: " + bfArka + "; " +
                "-fx-border-color: #3b82f6; -fx-border-width: 2; " +
                "-fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 6 12; " +
                "-fx-text-fill: " + metin + "; -fx-prompt-text-fill: " + ikincil + ";");
        HBox.setHgrow(barkodField, Priority.ALWAYS);

        Button ekleBtn = new Button("EKLE ↵");
        ekleBtn.setPrefHeight(46);
        ekleBtn.setPrefWidth(100);
        ekleBtn.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        ekleBtn.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; " +
                "-fx-background-radius: 8; -fx-cursor: hand;");
        ekleBtn.setOnMouseEntered(e -> ekleBtn.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-background-radius: 8; -fx-cursor: hand;"));
        ekleBtn.setOnMouseExited(e -> ekleBtn.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-background-radius: 8; -fx-cursor: hand;"));

        HBox barkodAlani = new HBox(10, barkodLbl, barkodField, ekleBtn);
        barkodAlani.setAlignment(Pos.CENTER_LEFT);
        barkodAlani.setPadding(new Insets(10, 16, 10, 16));
        barkodAlani.setStyle("-fx-background-color: " + panelArka + "; " + bolumStil);

        // ── SEPET TABLOSU ────────────────────────────────────────────────────────
        sepetVerisi = FXCollections.observableArrayList();
        sepetTablosu = new TableView<>(sepetVerisi);
        Label placeholder = new Label("Sepet boş  —  barkod okutun veya F1'e basın");
        placeholder.setFont(Font.font("Arial", 13));
        placeholder.setTextFill(Color.web(ikincil));
        sepetTablosu.setPlaceholder(placeholder);
        sepetTablosu.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        String tabloStil = "-fx-font-size: 13px;";
        if (koyu) tabloStil += " -fx-background-color: #1e1e1e; -fx-control-inner-background: #1e1e1e;";
        sepetTablosu.setStyle(tabloStil);
        sepetTablosu.setFixedCellSize(40);
        VBox.setVgrow(sepetTablosu, Priority.ALWAYS);

        String satirCift = AyarYoneticisi.tabloSatirCift();
        String satirTek  = AyarYoneticisi.tabloSatirTek();
        sepetTablosu.setRowFactory(tv -> new TableRow<SepetSatir>() {
            private void guncelle() {
                if (getItem() == null || isEmpty()) { setStyle(""); return; }
                if (koyu) {
                    String bg = isSelected() ? "#3a3a3a" : (getIndex() % 2 == 0 ? satirCift : satirTek);
                    setStyle("-fx-background-color: " + bg + "; -fx-text-fill: white;");
                } else {
                    String bg = isSelected() ? "#dbeafe" : (getIndex() % 2 == 0 ? satirCift : satirTek);
                    setStyle("-fx-background-color: " + bg + ";");
                }
            }
            { selectedProperty().addListener((o, a, b) -> guncelle()); }
            @Override protected void updateItem(SepetSatir item, boolean empty) { super.updateItem(item, empty); guncelle(); }
        });

        TableColumn<SepetSatir, Integer> siraKol = new TableColumn<>("#");
        siraKol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : String.valueOf(getIndex() + 1));
                setStyle("-fx-alignment: CENTER; -fx-text-fill: " + ikincil + ";");
            }
        });
        siraKol.setPrefWidth(40);

        TableColumn<SepetSatir, String> barkodKol = new TableColumn<>("Barkod");
        barkodKol.setCellValueFactory(new PropertyValueFactory<>("barkod"));
        barkodKol.setPrefWidth(120);

        TableColumn<SepetSatir, String> urunKol = new TableColumn<>("Ürün Adı");
        urunKol.setCellValueFactory(new PropertyValueFactory<>("urunAdi"));
        urunKol.setPrefWidth(290);

        TableColumn<SepetSatir, Double> adetKol = new TableColumn<>("Adet");
        adetKol.setCellValueFactory(new PropertyValueFactory<>("adet"));
        adetKol.setPrefWidth(60);
        adetKol.setStyle("-fx-alignment: CENTER;");

        TableColumn<SepetSatir, BigDecimal> fiyatKol = new TableColumn<>("Fiyat ₺");
        fiyatKol.setCellValueFactory(new PropertyValueFactory<>("fiyat"));
        fiyatKol.setPrefWidth(90);
        fiyatKol.setStyle("-fx-alignment: CENTER-RIGHT;");
        fiyatKol.setCellFactory(col -> new TableCell<SepetSatir, BigDecimal>() {
            @Override protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : String.format("%,.2f", item));
            }
        });

        TableColumn<SepetSatir, BigDecimal> tutarKol = new TableColumn<>("Tutar ₺");
        tutarKol.setCellValueFactory(new PropertyValueFactory<>("tutar"));
        tutarKol.setPrefWidth(105);
        tutarKol.setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-weight: bold;");
        tutarKol.setCellFactory(col -> new TableCell<SepetSatir, BigDecimal>() {
            @Override protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : String.format("%,.2f", item));
                String renk = koyu ? "#38bdf8" : "#1e40af";
                setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-weight: bold; -fx-text-fill: " + renk + ";");
            }
        });

        TableColumn<SepetSatir, Void> silKol = new TableColumn<>("");
        silKol.setPrefWidth(52);
        silKol.setCellFactory(col -> new TableCell<>() {
            final Button btn = new Button("✕");
            {
                String n = "-fx-background-color: #fee2e2; -fx-text-fill: #dc2626; -fx-background-radius: 5; -fx-cursor: hand; -fx-padding: 4 9; -fx-font-size: 12px; -fx-font-weight: bold;";
                String h = "-fx-background-color: #dc2626; -fx-text-fill: white; -fx-background-radius: 5; -fx-cursor: hand; -fx-padding: 4 9; -fx-font-size: 12px; -fx-font-weight: bold;";
                btn.setStyle(n);
                btn.setOnMouseEntered(e -> btn.setStyle(h));
                btn.setOnMouseExited(e -> btn.setStyle(n));
                btn.setOnAction(e -> { sepetVerisi.remove(getTableView().getItems().get(getIndex())); toplamGuncelle(); barkodField.requestFocus(); });
            }
            @Override protected void updateItem(Void item, boolean empty) { super.updateItem(item, empty); setGraphic(empty ? null : btn); }
        });

        sepetTablosu.getColumns().addAll(siraKol, barkodKol, urunKol, adetKol, fiyatKol, tutarKol, silKol);

        // Tablo altı aksiyon çubuğu
        String aksBtnBase = "-fx-background-radius: 0; -fx-cursor: hand; -fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 14 0;";
        Button sonSilBtn = new Button("⌫   Son Satırı Sil   [Del]");
        sonSilBtn.setMaxWidth(Double.MAX_VALUE);
        sonSilBtn.setStyle("-fx-background-color: #ea580c; -fx-text-fill: white; " + aksBtnBase);
        sonSilBtn.setOnMouseEntered(e -> sonSilBtn.setStyle("-fx-background-color: #c2410c; -fx-text-fill: white; " + aksBtnBase));
        sonSilBtn.setOnMouseExited(e -> sonSilBtn.setStyle("-fx-background-color: #ea580c; -fx-text-fill: white; " + aksBtnBase));

        Button temizleBtn = new Button("🗑   Tümünü Temizle   [Esc]");
        temizleBtn.setMaxWidth(Double.MAX_VALUE);
        temizleBtn.setStyle("-fx-background-color: #dc2626; -fx-text-fill: white; " + aksBtnBase);
        temizleBtn.setOnMouseEntered(e -> temizleBtn.setStyle("-fx-background-color: #b91c1c; -fx-text-fill: white; " + aksBtnBase));
        temizleBtn.setOnMouseExited(e -> temizleBtn.setStyle("-fx-background-color: #dc2626; -fx-text-fill: white; " + aksBtnBase));

        HBox.setHgrow(sonSilBtn, Priority.ALWAYS);
        HBox.setHgrow(temizleBtn, Priority.ALWAYS);
        HBox aksiyonCubugu = new HBox(1, sonSilBtn, temizleBtn);
        aksiyonCubugu.setStyle("-fx-background-color: " + (koyu ? "#1e1e1e" : "#f1f5f9") + "; " +
                "-fx-border-color: " + kenarRenk + "; -fx-border-width: 1 0 0 0;");

        VBox tableBox = new VBox(0, sepetTablosu, aksiyonCubugu);
        tableBox.setStyle("-fx-background-color: " + sayfaArka + ";");
        VBox.setVgrow(sepetTablosu, Priority.ALWAYS);
        VBox.setVgrow(tableBox, Priority.ALWAYS);

        // ── SAĞ PANEL ────────────────────────────────────────────────────────────
        // Yardımcı: bölüm başlık etiketi
        // -- TOPLAM --
        urunSayisiLabel = new Label("SEPET BOŞ");
        urunSayisiLabel.setFont(Font.font("Arial", FontWeight.BOLD, 10));
        urunSayisiLabel.setTextFill(Color.web(ikincil));

        toplamLabel = new Label("0,00 ₺");
        toplamLabel.setFont(Font.font("Arial", FontWeight.BOLD, 34));
        toplamLabel.setTextFill(Color.web("#10b981"));

        VBox toplamBolumu = new VBox(3, urunSayisiLabel, toplamLabel);
        toplamBolumu.setAlignment(Pos.CENTER);
        toplamBolumu.setPadding(new Insets(18, 14, 18, 14));
        String toplamBg = koyu ? "#052e16" : "#f0fdf4";
        toplamBolumu.setStyle("-fx-background-color: " + toplamBg + "; " + bolumStil);

        // -- ÖDEME BUTONLARI --
        Button nakitBtn = new Button("💵  NAKİT  [F5]");
        nakitBtn.setMaxWidth(Double.MAX_VALUE);
        nakitBtn.setPrefHeight(68);
        nakitBtn.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        nakitBtn.setStyle("-fx-background-color: #16a34a; -fx-text-fill: white; -fx-background-radius: 10; -fx-cursor: hand;");
        nakitBtn.setOnMouseEntered(e -> nakitBtn.setStyle("-fx-background-color: #15803d; -fx-text-fill: white; -fx-background-radius: 10; -fx-cursor: hand;"));
        nakitBtn.setOnMouseExited(e -> nakitBtn.setStyle("-fx-background-color: #16a34a; -fx-text-fill: white; -fx-background-radius: 10; -fx-cursor: hand;"));

        Button kartBtn = new Button("💳  KREDİ KARTI  [F6]");
        kartBtn.setMaxWidth(Double.MAX_VALUE);
        kartBtn.setPrefHeight(68);
        kartBtn.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        kartBtn.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-background-radius: 10; -fx-cursor: hand;");
        kartBtn.setOnMouseEntered(e -> kartBtn.setStyle("-fx-background-color: #1d4ed8; -fx-text-fill: white; -fx-background-radius: 10; -fx-cursor: hand;"));
        kartBtn.setOnMouseExited(e -> kartBtn.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-background-radius: 10; -fx-cursor: hand;"));

        VBox odemeBolumu = new VBox(10, nakitBtn, kartBtn);
        odemeBolumu.setPadding(new Insets(14, 14, 14, 14));
        odemeBolumu.setStyle("-fx-background-color: " + panelArka + "; " + bolumStil);

        // -- SON EKLENEN --
        Label sonEklenenBaslik = new Label("SON EKLENEN");
        sonEklenenBaslik.setFont(Font.font("Arial", FontWeight.BOLD, 9));
        sonEklenenBaslik.setTextFill(Color.web(ikincil));

        sonEklenenLabel = new Label("—");
        sonEklenenLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        sonEklenenLabel.setTextFill(Color.web(metin));
        sonEklenenLabel.setWrapText(true);

        VBox sonEklenenBolumu = new VBox(4, sonEklenenBaslik, sonEklenenLabel);
        sonEklenenBolumu.setPadding(new Insets(10, 14, 10, 14));
        String seArka = koyu ? "#1e293b" : "#f8fafc";
        sonEklenenBolumu.setStyle("-fx-background-color: " + seArka + "; " + bolumStil);

        // -- SEPET İŞLEMLERİ --
        // -- HIZLI ÜRÜNLER --
        Label hizliBaslikLbl = new Label("HIZLI ÜRÜNLER");
        hizliBaslikLbl.setFont(Font.font("Arial", FontWeight.BOLD, 9));
        hizliBaslikLbl.setTextFill(Color.web(ikincil));

        Button hizliEkleBtn = new Button("+");
        hizliEkleBtn.setPrefSize(22, 22);
        hizliEkleBtn.setStyle("-fx-background-color: #16a34a; -fx-text-fill: white; -fx-background-radius: 11; -fx-cursor: hand; -fx-font-size: 14px; -fx-font-weight: bold; -fx-padding: 0;");
        hizliEkleBtn.setTooltip(new Tooltip("Hızlı ürün ekle"));

        Region hizliAra = new Region();
        HBox.setHgrow(hizliAra, Priority.ALWAYS);
        HBox hizliBaslikSatiri = new HBox(4, hizliBaslikLbl, hizliAra, hizliEkleBtn);
        hizliBaslikSatiri.setAlignment(Pos.CENTER_LEFT);

        hizliUrunlerPanel = new FlowPane(6, 6);
        hizliUrunlerPanel.setPrefWrapLength(280);

        VBox hizliBolumu = new VBox(8, hizliBaslikSatiri, hizliUrunlerPanel);
        hizliBolumu.setPadding(new Insets(10, 14, 10, 14));
        hizliBolumu.setStyle("-fx-background-color: " + panelArka + "; " + bolumStil);

        // -- FİYAT GÜNCELLE (alt) --
        Button fiyatGuncelleBtn = new Button("✏  Hızlı Fiyat Güncelle  [F2]");
        fiyatGuncelleBtn.setMaxWidth(Double.MAX_VALUE);
        fiyatGuncelleBtn.setPrefHeight(46);
        fiyatGuncelleBtn.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        fiyatGuncelleBtn.setStyle("-fx-background-color: #7c3aed; -fx-text-fill: white; -fx-background-radius: 10; -fx-cursor: hand;");
        fiyatGuncelleBtn.setOnMouseEntered(e -> fiyatGuncelleBtn.setStyle("-fx-background-color: #6d28d9; -fx-text-fill: white; -fx-background-radius: 10; -fx-cursor: hand;"));
        fiyatGuncelleBtn.setOnMouseExited(e -> fiyatGuncelleBtn.setStyle("-fx-background-color: #7c3aed; -fx-text-fill: white; -fx-background-radius: 10; -fx-cursor: hand;"));

        Region altBosluk = new Region();
        VBox.setVgrow(altBosluk, Priority.ALWAYS);
        VBox fiyatBolumu = new VBox(0, altBosluk, fiyatGuncelleBtn);
        fiyatBolumu.setPadding(new Insets(10, 14, 14, 14));
        VBox.setVgrow(fiyatBolumu, Priority.ALWAYS);

        // -- SAĞ PANEL BİRLEŞTİR --
        ScrollPane sagScroll = new ScrollPane();
        VBox sagPanelIc = new VBox(0,
                toplamBolumu, odemeBolumu, sonEklenenBolumu, hizliBolumu, fiyatBolumu);
        VBox.setVgrow(fiyatBolumu, Priority.ALWAYS);
        sagPanelIc.setStyle("-fx-background-color: " + panelArka + ";");
        sagScroll.setContent(sagPanelIc);
        sagScroll.setFitToWidth(true);
        sagScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sagScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        sagScroll.setStyle("-fx-background-color: " + panelArka + "; -fx-background: " + panelArka + "; " +
                "-fx-border-color: " + kenarRenk + "; -fx-border-width: 0 0 0 1;");
        sagScroll.setPrefWidth(314);
        sagScroll.setMinWidth(314);
        sagScroll.setMaxWidth(314);

        // ── ANA LAYOUT ───────────────────────────────────────────────────────────
        String kisayolBg = koyu ? "#0d1b27" : "#1e293b";
        HBox icerik = new HBox(0, tableBox, sagScroll);
        HBox.setHgrow(tableBox, Priority.ALWAYS);
        VBox.setVgrow(icerik, Priority.ALWAYS);

        VBox anaLayout = new VBox(0, ustBar, barkodAlani, icerik, kisayolCubuguOlustur(kisayolBg));
        VBox.setVgrow(icerik, Priority.ALWAYS);

        // ── OLAYLAR ──────────────────────────────────────────────────────────────
        barkodField.setOnAction(e -> barkodOkut());
        ekleBtn.setOnAction(e -> barkodOkut());
        nakitBtn.setOnAction(e -> odemeYap("NAKIT", nakitBtn));
        kartBtn.setOnAction(e -> odemeYap("KART", kartBtn));
        sonSilBtn.setOnAction(e -> { sonUrunuSil(); barkodField.requestFocus(); });
        temizleBtn.setOnAction(e -> sepetiTemizle());
        yonetimBtn.setOnAction(e -> yonetimeGec());
        cikisBtn.setOnAction(e -> cikisYap());
        bulunamayanBtn.setOnAction(e -> bulunamayanBarkodlariGoster());
        fiyatGuncelleBtn.setOnAction(e -> hizliFiyatGuncelle());
        hizliEkleBtn.setOnAction(e -> hizliUrunEkleDialog());

        Scene scene = new Scene(anaLayout, 1280, 800);
        if (koyu) {
            java.net.URL cssUrl = getClass().getResource("/dark-theme.css");
            if (cssUrl != null) scene.getStylesheets().add(cssUrl.toExternalForm());
        }

        scene.setOnKeyPressed(e -> {
            switch (e.getCode()) {
                case F1 -> barkodField.requestFocus();
                case F2 -> hizliFiyatGuncelle();
                case F5 -> odemeYap("NAKIT", nakitBtn);
                case F6 -> odemeYap("KART", kartBtn);
                case DELETE -> {
                    SepetSatir secili = sepetTablosu.getSelectionModel().getSelectedItem();
                    if (secili != null) {
                        sepetVerisi.remove(secili);
                        toplamGuncelle();
                        barkodField.requestFocus();
                    }
                }
                case ESCAPE -> sepetiTemizle();
            }
        });

        hizliUrunleriYukle();
        hizliUrunPaneliniYenile();

        Platform.runLater(() -> barkodField.requestFocus());
        return scene;
    }

    // ===== BARKOD OKUT =====
    private void barkodOkut() {
        String deger = barkodField.getText().trim();
        if (deger.isEmpty()) return;

        String barkod = deger;
        double miktar = 1.0;

        if (deger.contains("*")) {
            String[] parcalar = deger.split("\\*", 2);
            try {
                miktar = Double.parseDouble(parcalar[0].trim());
                barkod = parcalar[1].trim();
                if (miktar <= 0) throw new Exception();
            } catch (Exception ex) {
                bildir("HATA: '2*Barkod' formatında giriniz!", "#e74c3c");
                barkodField.clear();
                return;
            }
        }

        barkodField.clear();
        final String finalBarkod = barkod;
        final double finalMiktar = miktar;

        new Thread(() -> {
            try {
                Map<String, Object> urun = ApiClient.get(
                        "/api/urunler/bul/" + ApiClient.getMarketId() + "/" + finalBarkod);

                if (urun != null && urun.containsKey("id")) {
                    Platform.runLater(() -> {
                        sepeteEkle(urun, finalMiktar);
                        barkodField.requestFocus();
                    });
                } else {
                    Platform.runLater(() -> {
                        if (!bulunamayanBarkodlar.contains(finalBarkod)) {
                            bulunamayanBarkodlar.add(finalBarkod);
                        }
                        bulunamayanBtn.setText("⚠  Bulunamayan [" + bulunamayanBarkodlar.size() + "]");
                        bulunamayanBtn.setVisible(true);
                        ses("hata");
                        bildir("❌  Barkod bulunamadı:  " + finalBarkod, "#e74c3c");
                        barkodField.requestFocus();
                    });
                }
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    bildir("❌  Bağlantı hatası!", "#c0392b");
                    barkodField.requestFocus();
                });
            }
        }).start();
    }

    // ===== SEPETE EKLE =====
    private void sepeteEkle(Map<String, Object> urun, double miktar) {
        String barkod = urun.get("barkod").toString();
        String urunAdi = urun.get("isim").toString();
        BigDecimal fiyat = new BigDecimal(urun.get("fiyat").toString());
        Long urunId = Long.valueOf(urun.get("id").toString());

        SepetSatir yeniSatir = new SepetSatir(barkod, urunAdi, miktar, fiyat, urunId);
        sepetVerisi.add(yeniSatir);
        toplamGuncelle();
        sepetTablosu.scrollTo(sepetVerisi.size() - 1);
        ses("basarili");

        // Popup yok — sol panelde sessiz güncelleme
        String adetStr = miktar == (long) miktar
                ? String.valueOf((long) miktar)
                : String.valueOf(miktar);
        sonEklenenLabel.setText(urunAdi + "\n× " + adetStr + "  =  " +
                String.format("%,.2f ₺", fiyat.multiply(BigDecimal.valueOf(miktar))));
        sonEklenenLabel.setTextFill(Color.web(AyarYoneticisi.isKoyu() ? "#4fc3f7" : "#1a5276"));
    }

    // ===== TOPLAMI GÜNCELLE =====
    private void toplamGuncelle() {
        BigDecimal toplam = sepetVerisi.stream()
                .map(SepetSatir::getTutar)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        toplamLabel.setText(String.format("%,.2f ₺", toplam));
        if (urunSayisiLabel != null) {
            int adet = sepetVerisi.size();
            urunSayisiLabel.setText(adet == 0 ? "SEPET BOŞ" : adet + " ÜRÜN");
        }
    }

    // ===== SON ÜRÜNÜ SİL =====
    private void sonUrunuSil() {
        if (sepetVerisi.isEmpty()) {
            bildir("Sepet zaten boş!", "#e67e22");
            return;
        }
        sepetVerisi.remove(sepetVerisi.size() - 1);
        toplamGuncelle();
        barkodField.requestFocus();
    }

    // ===== SEPETİ TEMİZLE =====
    private void sepetiTemizle() {
        if (sepetVerisi.isEmpty()) return;
        sepetVerisi.clear();
        toplamGuncelle();
        sonEklenenLabel.setText("—");
        sonEklenenLabel.setTextFill(Color.web(AyarYoneticisi.metinRengi()));
        barkodField.requestFocus();
    }

    // ===== ÖDEME YAP =====
    private void odemeYap(String tip, Button btn) {
        if (sepetVerisi.isEmpty()) {
            bildir("Sepet boş!", "#e74c3c");
            return;
        }
        if (islemYapiliyor) return;
        islemYapiliyor = true;
        btn.setDisable(true);
        btn.setText("İşleniyor...");

        List<Map<String, Object>> sepetListesi = new ArrayList<>();
        for (SepetSatir satir : sepetVerisi) {
            sepetListesi.add(Map.of("barkod", satir.getBarkod(), "adet", satir.getAdet()));
        }

        Map<String, Object> istek = Map.of("odemeTipi", tip, "sepet", sepetListesi);

        new Thread(() -> {
            try {
                Map<String, Object> yanit = ApiClient.post("/api/satis/tamamla", istek);
                Platform.runLater(() -> {
                    String mesaj = yanit.getOrDefault("mesaj", "").toString();
                    if ("Başarılı".equals(mesaj)) {
                        bildir("✅  Satış tamamlandı  —  " + (tip.equals("NAKIT") ? "NAKİT" : "KREDİ KARTI"), "#27ae60");
                        sepetVerisi.clear();
                        toplamGuncelle();
                        sonEklenenLabel.setText("—");
                        sonEklenenLabel.setTextFill(Color.web(AyarYoneticisi.metinRengi()));
                    } else {
                        String hata = yanit.getOrDefault("hata",
                                yanit.getOrDefault("mesaj", "Hata!")).toString();
                        bildir("❌  " + hata, "#e74c3c");
                    }
                    islemYapiliyor = false;
                    btn.setDisable(false);
                    btn.setText(tip.equals("NAKIT") ? "💵  Nakit\n[F5]" : "💳  Kredi Kartı\n[F6]");
                    barkodField.requestFocus();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    bildir("❌  Bağlantı hatası!", "#c0392b");
                    islemYapiliyor = false;
                    btn.setDisable(false);
                    btn.setText(tip.equals("NAKIT") ? "💵  Nakit\n[F5]" : "💳  Kredi Kartı\n[F6]");
                    barkodField.requestFocus();
                });
            }
        }).start();
    }

    // ===== HIZLI FİYAT GÜNCELLE =====
    private void hizliFiyatGuncelle() {
        Stage dialog = new Stage();
        dialog.setTitle("Hızlı Fiyat Güncelleme");
        dialog.initOwner(stage);
        dialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        dialog.setResizable(false);

        // Header
        Label dlgBaslik = new Label("✏  Hızlı Fiyat Güncelleme");
        dlgBaslik.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        dlgBaslik.setTextFill(Color.WHITE);
        HBox dlgHeader = new HBox(dlgBaslik);
        dlgHeader.setPadding(new Insets(13, 18, 13, 18));
        dlgHeader.setStyle("-fx-background-color: #e67e22;");

        // Barkod satırı
        Label bLbl = new Label("Barkod:");
        bLbl.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        bLbl.setMinWidth(90);

        TextField dlgBarkod = new TextField();
        dlgBarkod.setPromptText("Barkod girin veya okutun...");
        dlgBarkod.setPrefHeight(38);
        dlgBarkod.setFont(Font.font("Arial", 13));
        HBox.setHgrow(dlgBarkod, Priority.ALWAYS);

        Button bulBtn = new Button("Ara");
        bulBtn.setPrefHeight(38);
        bulBtn.setPrefWidth(70);
        bulBtn.setStyle("-fx-background-color: #2980b9; -fx-text-fill: white; " +
                "-fx-background-radius: 6; -fx-cursor: hand; -fx-font-weight: bold;");

        HBox barkodSatiri2 = new HBox(10, bLbl, dlgBarkod, bulBtn);
        barkodSatiri2.setAlignment(Pos.CENTER_LEFT);

        // Ürün bilgisi satırı
        Label urunBilgiLabel = new Label("Bir barkod girerek ürün arayın");
        urunBilgiLabel.setFont(Font.font("Arial", 12));
        urunBilgiLabel.setTextFill(Color.web("#7f8c8d"));
        urunBilgiLabel.setWrapText(true);

        // Fiyat satırı
        Label fLbl = new Label("Yeni Fiyat:");
        fLbl.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        fLbl.setMinWidth(90);

        TextField dlgFiyat = new TextField();
        dlgFiyat.setPromptText("0.00");
        dlgFiyat.setPrefHeight(38);
        dlgFiyat.setPrefWidth(130);
        dlgFiyat.setFont(Font.font("Arial", FontWeight.BOLD, 15));
        dlgFiyat.setDisable(true);

        Label tlLbl = new Label("₺");
        tlLbl.setFont(Font.font("Arial", FontWeight.BOLD, 15));
        tlLbl.setTextFill(Color.web("#7f8c8d"));

        HBox fiyatSatiri2 = new HBox(10, fLbl, dlgFiyat, tlLbl);
        fiyatSatiri2.setAlignment(Pos.CENTER_LEFT);

        // Butonlar
        Button guncelleBtn = new Button("💾  Güncelle");
        guncelleBtn.setPrefHeight(40);
        guncelleBtn.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        guncelleBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; " +
                "-fx-background-radius: 7; -fx-cursor: hand; -fx-padding: 0 20;");
        guncelleBtn.setDisable(true);

        Button iptalBtn = new Button("Kapat");
        iptalBtn.setPrefHeight(40);
        iptalBtn.setStyle("-fx-background-color: #bdc3c7; -fx-text-fill: #2c3e50; " +
                "-fx-background-radius: 7; -fx-cursor: hand; -fx-padding: 0 16;");
        iptalBtn.setOnAction(e -> dialog.close());

        HBox butonSatiri = new HBox(10, guncelleBtn, iptalBtn);
        butonSatiri.setAlignment(Pos.CENTER_RIGHT);

        VBox govde = new VBox(14, barkodSatiri2, urunBilgiLabel, fiyatSatiri2, butonSatiri);
        govde.setPadding(new Insets(20, 20, 20, 20));

        VBox root = new VBox(0, dlgHeader, govde);

        // Dahili durum
        final Long[] urunIdRef = {null};
        final String[] barkodRef = {null};
        final String[] isimRef = {null};

        Runnable urunAra = () -> {
            String barkod = dlgBarkod.getText().trim();
            if (barkod.isEmpty()) return;
            bulBtn.setDisable(true);
            bulBtn.setText("...");
            new Thread(() -> {
                try {
                    Map<String, Object> urun = ApiClient.get(
                            "/api/urunler/bul/" + ApiClient.getMarketId() + "/" + barkod);
                    Platform.runLater(() -> {
                        bulBtn.setDisable(false);
                        bulBtn.setText("Ara");
                        if (urun != null && urun.containsKey("id")) {
                            urunIdRef[0] = Long.valueOf(urun.get("id").toString());
                            barkodRef[0] = urun.get("barkod").toString();
                            isimRef[0] = urun.get("isim").toString();
                            double mevcutFiyat = ((Number) urun.get("fiyat")).doubleValue();
                            urunBilgiLabel.setText("✓  " + isimRef[0] + "   —   Mevcut: " +
                                    String.format("%.2f ₺", mevcutFiyat));
                            urunBilgiLabel.setTextFill(Color.web("#27ae60"));
                            dlgFiyat.setText(String.format("%.2f", mevcutFiyat));
                            dlgFiyat.setDisable(false);
                            guncelleBtn.setDisable(false);
                            dlgFiyat.requestFocus();
                            dlgFiyat.selectAll();
                        } else {
                            urunBilgiLabel.setText("❌  Barkod bulunamadı: " + barkod);
                            urunBilgiLabel.setTextFill(Color.web("#e74c3c"));
                            urunIdRef[0] = null;
                            dlgFiyat.setDisable(true);
                            guncelleBtn.setDisable(true);
                        }
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        bulBtn.setDisable(false);
                        bulBtn.setText("Ara");
                        urunBilgiLabel.setText("❌  Bağlantı hatası!");
                        urunBilgiLabel.setTextFill(Color.web("#e74c3c"));
                    });
                }
            }).start();
        };

        bulBtn.setOnAction(e -> urunAra.run());
        dlgBarkod.setOnAction(e -> urunAra.run());

        guncelleBtn.setOnAction(e -> {
            if (urunIdRef[0] == null) return;
            String fiyatStr = dlgFiyat.getText().trim().replace(",", ".");
            double yeniFiyat;
            try {
                yeniFiyat = Double.parseDouble(fiyatStr);
                if (yeniFiyat < 0) throw new NumberFormatException();
            } catch (Exception ex) {
                urunBilgiLabel.setText("❌  Geçersiz fiyat değeri!");
                urunBilgiLabel.setTextFill(Color.web("#e74c3c"));
                return;
            }
            final double f = yeniFiyat;
            final Long id = urunIdRef[0];
            final String brk = barkodRef[0];
            final String isim = isimRef[0];

            guncelleBtn.setDisable(true);
            guncelleBtn.setText("Kaydediliyor...");

            new Thread(() -> {
                try {
                    ApiClient.put("/api/urunler/guncelle/" + id,
                            Map.of("barkod", brk, "isim", isim, "fiyat", f));
                    Platform.runLater(() -> {
                        urunBilgiLabel.setText("✅  Güncellendi:  " + isim +
                                "  →  " + String.format("%.2f ₺", f));
                        urunBilgiLabel.setTextFill(Color.web("#27ae60"));
                        guncelleBtn.setText("💾  Güncelle");
                        // Sıfırla — bir sonraki ürün için hazır
                        dlgBarkod.clear();
                        dlgFiyat.clear();
                        dlgFiyat.setDisable(true);
                        guncelleBtn.setDisable(true);
                        urunIdRef[0] = null;
                        dlgBarkod.requestFocus();
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        urunBilgiLabel.setText("❌  Güncelleme başarısız!");
                        urunBilgiLabel.setTextFill(Color.web("#e74c3c"));
                        guncelleBtn.setDisable(false);
                        guncelleBtn.setText("💾  Güncelle");
                    });
                }
            }).start();
        });

        dlgFiyat.setOnAction(e -> guncelleBtn.fire());

        dialog.setScene(new Scene(root, 440, 255));
        Platform.runLater(dlgBarkod::requestFocus);
        dialog.showAndWait();
    }

    // ===== BULUNAMAYAN BARKODLAR DİYALOGU =====
    private void bulunamayanBarkodlariGoster() {
        Stage dialog = new Stage();
        dialog.setTitle("Bulunamayan Barkodlar");
        dialog.initOwner(stage);
        dialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);

        Label baslik = new Label("Sistemde kayıtlı olmayan barkodlar:");
        baslik.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        baslik.setPadding(new Insets(0, 0, 8, 0));

        TextArea liste = new TextArea(String.join("\n", bulunamayanBarkodlar));
        liste.setEditable(false);
        liste.setPrefHeight(280);
        liste.setFont(Font.font("Monospaced", 13));
        liste.setStyle("-fx-background-color: #f8f9fa;");

        Button temizleBtn2 = new Button("🗑  Listeyi Temizle");
        temizleBtn2.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; " +
                "-fx-background-radius: 5; -fx-cursor: hand;");
        temizleBtn2.setOnAction(e -> {
            bulunamayanBarkodlar.clear();
            bulunamayanBtn.setVisible(false);
            dialog.close();
        });

        Button kapat = new Button("Kapat");
        kapat.setStyle("-fx-background-color: #7f8c8d; -fx-text-fill: white; " +
                "-fx-background-radius: 5; -fx-cursor: hand;");
        kapat.setOnAction(e -> dialog.close());

        HBox butonlar = new HBox(10, temizleBtn2, kapat);
        butonlar.setPadding(new Insets(10, 0, 0, 0));
        butonlar.setAlignment(Pos.CENTER_RIGHT);

        VBox icerik = new VBox(10, baslik, liste, butonlar);
        icerik.setPadding(new Insets(20));

        dialog.setScene(new Scene(icerik, 420, 400));
        dialog.showAndWait();
    }

    // ===== YÖNETİME GEÇ =====
    private void yonetimeGec() {
        YonetimEkrani yonetim = new YonetimEkrani(stage);
        stage.setScene(yonetim.olustur());
    }

    // ===== ÇIKIŞ =====
    private void cikisYap() {
        new Thread(() -> {
            try { ApiClient.cikisYap(); } catch (Exception e) {
                log.debug("Çıkış API çağrısı başarısız (token zaten süresi dolmuş olabilir)", e);
            }
            Platform.runLater(() -> {
                GirisEkrani giris = new GirisEkrani(stage);
                stage.setScene(giris.olustur());
                stage.setWidth(500);
                stage.setHeight(400);
                stage.centerOnScreen();
            });
        }).start();
    }

    // ===== HIZLI ÜRÜNLER =====
    // Dosya formatı: her satır "barkod|butonIsmi"
    private void hizliUrunleriYukle() {
        hizliUrunler.clear();
        try {
            if (java.nio.file.Files.exists(HIZLI_URUNLER_DOSYASI)) {
                for (String satir : java.nio.file.Files.readAllLines(
                        HIZLI_URUNLER_DOSYASI, java.nio.charset.StandardCharsets.UTF_8)) {
                    String[] parcalar = satir.split("\\|", 2);
                    if (parcalar.length == 2 && !parcalar[0].isBlank()) {
                        hizliUrunler.add(new String[]{parcalar[0].trim(), parcalar[1].trim()});
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Hızlı ürünler yüklenemedi", e);
        }
    }

    private void hizliUrunleriKaydet() {
        try {
            java.nio.file.Files.createDirectories(HIZLI_URUNLER_DOSYASI.getParent());
            StringBuilder sb = new StringBuilder();
            for (String[] u : hizliUrunler) sb.append(u[0]).append("|").append(u[1]).append("\n");
            java.nio.file.Files.writeString(HIZLI_URUNLER_DOSYASI, sb.toString(),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.debug("Hızlı ürünler kaydedilemedi", e);
        }
    }

    private void hizliUrunPaneliniYenile() {
        hizliUrunlerPanel.getChildren().clear();
        for (String[] urun : hizliUrunler) {
            String barkod   = urun[0];
            String butonAdi = urun[1];

            Button btn = new Button(butonAdi);
            btn.setPrefWidth(120);
            btn.setPrefHeight(48);
            btn.setFont(Font.font("Arial", FontWeight.BOLD, 11));
            btn.setWrapText(true);
            btn.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
            btn.setStyle(
                    "-fx-background-color: #8e44ad; -fx-text-fill: white; " +
                    "-fx-background-radius: 8; -fx-cursor: hand;");
            btn.setOnMouseEntered(e -> btn.setStyle(
                    "-fx-background-color: #6c3483; -fx-text-fill: white; " +
                    "-fx-background-radius: 8; -fx-cursor: hand;"));
            btn.setOnMouseExited(e -> btn.setStyle(
                    "-fx-background-color: #8e44ad; -fx-text-fill: white; " +
                    "-fx-background-radius: 8; -fx-cursor: hand;"));

            // Sağ tık → kaldır
            javafx.scene.control.ContextMenu menu = new javafx.scene.control.ContextMenu();
            javafx.scene.control.MenuItem silItem = new javafx.scene.control.MenuItem("✕  Kaldır");
            silItem.setOnAction(ev -> {
                hizliUrunler.remove(urun);
                hizliUrunleriKaydet();
                hizliUrunPaneliniYenile();
            });
            menu.getItems().add(silItem);
            btn.setContextMenu(menu);

            btn.setOnAction(e -> hizliUrunuSepeteEkle(barkod, butonAdi));
            hizliUrunlerPanel.getChildren().add(btn);
        }
    }

    private void hizliUrunuSepeteEkle(String barkod, String butonAdi) {
        new Thread(() -> {
            try {
                Map<String, Object> urun = ApiClient.get(
                        "/api/urunler/bul/" + ApiClient.getMarketId() + "/" + barkod);
                Platform.runLater(() -> {
                    if (urun != null && urun.containsKey("id")) {
                        sepeteEkle(urun, 1.0);
                    } else {
                        bildir("❌  \"" + butonAdi + "\" bulunamadı! Ürün silinmiş olabilir.", "#e74c3c");
                    }
                    barkodField.requestFocus();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    bildir("❌  Bağlantı hatası!", "#c0392b");
                    barkodField.requestFocus();
                });
            }
        }).start();
    }

    private void hizliUrunEkleDialog() {
        Stage dialog = new Stage();
        dialog.setTitle("Hızlı Ürün Ekle");
        dialog.initOwner(stage);
        dialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        dialog.setResizable(false);

        // Header
        Label baslikLbl = new Label("🔖  Hızlı Ürün Ekle");
        baslikLbl.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        baslikLbl.setTextFill(Color.WHITE);
        HBox dlgHeader = new HBox(baslikLbl);
        dlgHeader.setPadding(new Insets(12, 18, 12, 18));
        dlgHeader.setStyle("-fx-background-color: #8e44ad;");

        Label aciklamaLbl = new Label(
                "Ürün bilgilerini doldurun. Ürün kataloğa eklenecek ve sol menüde hızlı buton oluşturulacak.");
        aciklamaLbl.setFont(Font.font("Arial", 11));
        aciklamaLbl.setTextFill(Color.web("#7f8c8d"));
        aciklamaLbl.setWrapText(true);

        // Form alanları
        TextField barkodField2   = new TextField();
        TextField urunAdiField   = new TextField();
        TextField fiyatField     = new TextField();
        TextField butonIsmiField = new TextField();

        barkodField2.setPromptText("Benzersiz barkod (ör: BUZ001)");
        urunAdiField.setPromptText("Katalogda görünecek tam ad (ör: Çay Bardağı Buz)");
        fiyatField.setPromptText("0.00");
        butonIsmiField.setPromptText("Buton kısa adı (ör: Buz)");

        for (TextField tf : new TextField[]{barkodField2, urunAdiField, fiyatField, butonIsmiField}) {
            tf.setPrefHeight(36);
            tf.setFont(Font.font("Arial", 13));
        }

        int lblW = 110;
        Label lBarkod   = satirEtiketi("Barkod:", lblW);
        Label lUrunAdi  = satirEtiketi("Ürün Adı:", lblW);
        Label lFiyat    = satirEtiketi("Fiyat (₺):", lblW);
        Label lButon    = satirEtiketi("Buton Adı:", lblW);

        VBox form = new VBox(8,
                satirSatir(lBarkod, barkodField2),
                satirSatir(lUrunAdi, urunAdiField),
                satirSatir(lFiyat, fiyatField),
                satirSatir(lButon, butonIsmiField));

        Label durumLbl = new Label("");
        durumLbl.setFont(Font.font("Arial", 12));
        durumLbl.setWrapText(true);

        Button ekleBtn2 = new Button("✅  Ekle");
        ekleBtn2.setPrefHeight(38);
        ekleBtn2.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        ekleBtn2.setStyle("-fx-background-color: #8e44ad; -fx-text-fill: white; " +
                "-fx-background-radius: 7; -fx-cursor: hand; -fx-padding: 0 18;");

        Button kapatBtn2 = new Button("Kapat");
        kapatBtn2.setPrefHeight(38);
        kapatBtn2.setStyle("-fx-background-color: #bdc3c7; -fx-text-fill: #2c3e50; " +
                "-fx-background-radius: 7; -fx-cursor: hand; -fx-padding: 0 14;");
        kapatBtn2.setOnAction(e -> dialog.close());

        HBox butonSatiri2 = new HBox(10, ekleBtn2, kapatBtn2);
        butonSatiri2.setAlignment(Pos.CENTER_RIGHT);

        Runnable ekle = () -> {
            String barkod    = barkodField2.getText().trim();
            String urunAdi   = urunAdiField.getText().trim();
            String fiyatStr  = fiyatField.getText().trim().replace(",", ".");
            String butonIsmi = butonIsmiField.getText().trim();

            if (barkod.isEmpty() || urunAdi.isEmpty() || fiyatStr.isEmpty() || butonIsmi.isEmpty()) {
                durumLbl.setText("❌  Tüm alanları doldurun!");
                durumLbl.setTextFill(Color.web("#e74c3c"));
                return;
            }
            double fiyat;
            try {
                fiyat = Double.parseDouble(fiyatStr);
                if (fiyat < 0) throw new NumberFormatException();
            } catch (NumberFormatException ex) {
                durumLbl.setText("❌  Geçersiz fiyat!");
                durumLbl.setTextFill(Color.web("#e74c3c"));
                return;
            }
            // Aynı barkod zaten hızlı listede mi?
            for (String[] u : hizliUrunler) {
                if (u[0].equals(barkod)) {
                    durumLbl.setText("⚠  Bu barkod zaten hızlı listede: " + u[1]);
                    durumLbl.setTextFill(Color.web("#e67e22"));
                    return;
                }
            }

            ekleBtn2.setDisable(true);
            ekleBtn2.setText("Kaydediliyor...");
            final double f = fiyat;

            new Thread(() -> {
                try {
                    // Barkod katalogda var mı kontrol et
                    Map<String, Object> mevcutUrun = ApiClient.get(
                            "/api/urunler/bul/" + ApiClient.getMarketId() + "/" + barkod);
                    if (mevcutUrun != null && mevcutUrun.containsKey("id")) {
                        // Mevcut ürün var — kullanıcıya sor
                        Platform.runLater(() -> {
                            String mevIsim   = mevcutUrun.get("isim").toString();
                            String mevFiyat  = String.format("%.2f ₺",
                                    ((Number) mevcutUrun.get("fiyat")).doubleValue());
                            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                            alert.setTitle("Barkod Zaten Mevcut");
                            alert.setHeaderText("Bu barkodda kayıtlı ürün var");
                            alert.setContentText(
                                    "\"" + mevIsim + "\"  (" + mevFiyat + ")\n\n" +
                                    "Bu mevcut ürünü hızlı butona eklemek ister misiniz?\n" +
                                    "Yeni ürün oluşturulmaz, mevcut ürün kullanılır.");
                            ButtonType kullanBtn = new ButtonType(
                                    "✓  Evet, Kullan", ButtonBar.ButtonData.YES);
                            ButtonType iptalBtn = new ButtonType(
                                    "Farklı Barkod Gir", ButtonBar.ButtonData.CANCEL_CLOSE);
                            alert.getButtonTypes().setAll(kullanBtn, iptalBtn);
                            alert.showAndWait().ifPresent(secim -> {
                                if (secim == kullanBtn) {
                                    String btnIsmi = butonIsmiField.getText().trim();
                                    if (btnIsmi.isEmpty()) btnIsmi = mevIsim;
                                    hizliUrunler.add(new String[]{barkod, btnIsmi});
                                    hizliUrunleriKaydet();
                                    hizliUrunPaneliniYenile();
                                    durumLbl.setText("✅  Mevcut ürün hızlı listeye eklendi: " + mevIsim);
                                    durumLbl.setTextFill(Color.web("#27ae60"));
                                    barkodField2.clear();
                                    urunAdiField.clear();
                                    fiyatField.clear();
                                    butonIsmiField.clear();
                                }
                            });
                            ekleBtn2.setDisable(false);
                            ekleBtn2.setText("✅  Ekle");
                        });
                        return;
                    }
                    // Yeni ürün — kataloğa ekle
                    ApiClient.post("/api/urunler/ekle/" + ApiClient.getMarketId(),
                            Map.of("barkod", barkod, "isim", urunAdi, "fiyat", f));
                    // Başarılıysa hızlı listeye de ekle
                    Platform.runLater(() -> {
                        hizliUrunler.add(new String[]{barkod, butonIsmi});
                        hizliUrunleriKaydet();
                        hizliUrunPaneliniYenile();
                        durumLbl.setText("✅  \"" + butonIsmi + "\" eklendi ve kataloğa kaydedildi.");
                        durumLbl.setTextFill(Color.web("#27ae60"));
                        barkodField2.clear();
                        urunAdiField.clear();
                        fiyatField.clear();
                        butonIsmiField.clear();
                        ekleBtn2.setDisable(false);
                        ekleBtn2.setText("✅  Ekle");
                        barkodField2.requestFocus();
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        durumLbl.setText("❌  Kayıt başarısız: " + ex.getMessage());
                        durumLbl.setTextFill(Color.web("#e74c3c"));
                        ekleBtn2.setDisable(false);
                        ekleBtn2.setText("✅  Ekle");
                    });
                }
            }).start();
        };

        ekleBtn2.setOnAction(e -> ekle.run());

        VBox govde = new VBox(12, aciklamaLbl, form, durumLbl, butonSatiri2);
        govde.setPadding(new Insets(18));
        VBox root = new VBox(0, dlgHeader, govde);

        dialog.setScene(new Scene(root, 440, 330));
        Platform.runLater(barkodField2::requestFocus);
        dialog.showAndWait();
        barkodField.requestFocus();
    }

    // Form yardımcıları
    private Label satirEtiketi(String metin, int genislik) {
        Label l = new Label(metin);
        l.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        l.setMinWidth(genislik);
        return l;
    }
    private HBox satirSatir(Label etiket, TextField alan) {
        HBox.setHgrow(alan, Priority.ALWAYS);
        HBox satir = new HBox(8, etiket, alan);
        satir.setAlignment(Pos.CENTER_LEFT);
        return satir;
    }

    // ===== KISAYOL ÇUBUĞU YARDIMCISI (C) =====
    private HBox kisayolCubuguOlustur(String arkaRenk) {
        String[] kisayollar = {
            "[F1] Barkod Odağı", "[F2] Fiyat Güncelle",
            "[F5] Nakit Ödeme", "[F6] Kart Ödeme",
            "[Del] Seçili Satırı Sil", "[Esc] Sepeti Temizle"
        };
        HBox cubuk = new HBox(0);
        cubuk.setAlignment(Pos.CENTER_LEFT);
        cubuk.setStyle("-fx-background-color: " + arkaRenk + ";");
        cubuk.setPadding(new Insets(4, 12, 4, 12));

        for (int i = 0; i < kisayollar.length; i++) {
            String[] parcalar = kisayollar[i].split(" ", 2);
            Label tus = new Label(parcalar[0]);
            tus.setFont(Font.font("Monospaced", FontWeight.BOLD, 10));
            tus.setTextFill(Color.web("#ecf0f1"));
            tus.setStyle("-fx-background-color: #34495e; -fx-background-radius: 3; " +
                    "-fx-padding: 1 5;");

            Label aciklama = new Label(" " + (parcalar.length > 1 ? parcalar[1] : ""));
            aciklama.setFont(Font.font("Arial", 10));
            aciklama.setTextFill(Color.web("#7f8c8d"));

            cubuk.getChildren().addAll(tus, aciklama);
            if (i < kisayollar.length - 1) {
                Label ayrac = new Label("  │  ");
                ayrac.setFont(Font.font("Arial", 10));
                ayrac.setTextFill(Color.web("#34495e"));
                cubuk.getChildren().add(ayrac);
            }
        }
        return cubuk;
    }

    // ===== SES GERİ BİLDİRİMİ =====
    private void ses(String tip) {
        if (!AyarYoneticisi.isSesAcik()) return;
        new Thread(() -> {
            try {
                if ("basarili".equals(tip)) sesBasarali();
                else if ("hata".equals(tip))  sesHata();
            } catch (Exception e) {
                log.warn("Ses çalınamadı: tip={}", tip, e);
                try { java.awt.Toolkit.getDefaultToolkit().beep(); } catch (Exception ignored) {}
            }
        }, "pos-ses").start();
    }

    /** ses2.wav — başarılı barkod ekleme sesi, kısık ses (-8 dB) */
    private void sesBasarali() {
        try (java.io.InputStream raw = getClass().getResourceAsStream("/ses2.wav")) {
            if (raw == null) {
                log.warn("ses2.wav bulunamadı");
                java.awt.Toolkit.getDefaultToolkit().beep();
                return;
            }
            try (javax.sound.sampled.AudioInputStream ais =
                         javax.sound.sampled.AudioSystem.getAudioInputStream(
                                 new java.io.BufferedInputStream(raw));
                 javax.sound.sampled.Clip clip = javax.sound.sampled.AudioSystem.getClip()) {
                clip.open(ais);
                // Ses kısma: -8 dB
                if (clip.isControlSupported(javax.sound.sampled.FloatControl.Type.MASTER_GAIN)) {
                    javax.sound.sampled.FloatControl gain =
                            (javax.sound.sampled.FloatControl) clip.getControl(
                                    javax.sound.sampled.FloatControl.Type.MASTER_GAIN);
                    gain.setValue(Math.max(gain.getMinimum(), gain.getValue() - 8f));
                }
                java.util.concurrent.CountDownLatch bekle = new java.util.concurrent.CountDownLatch(1);
                clip.addLineListener(evt -> {
                    if (javax.sound.sampled.LineEvent.Type.STOP.equals(evt.getType()))
                        bekle.countDown();
                });
                clip.start();
                bekle.await(3, java.util.concurrent.TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            log.warn("ses2.wav çalınamadı", e);
            try { java.awt.Toolkit.getDefaultToolkit().beep(); } catch (Exception ignored) {}
        }
    }

    /** ses.wav — Clip + CountDownLatch ile doğru bekleme */
    private void sesHata() {
        try (java.io.InputStream raw = getClass().getResourceAsStream("/ses.wav")) {
            if (raw == null) {
                log.warn("ses.wav bulunamadı");
                java.awt.Toolkit.getDefaultToolkit().beep();
                return;
            }
            try (javax.sound.sampled.AudioInputStream ais =
                         javax.sound.sampled.AudioSystem.getAudioInputStream(
                                 new java.io.BufferedInputStream(raw));
                 javax.sound.sampled.Clip clip = javax.sound.sampled.AudioSystem.getClip()) {
                clip.open(ais);
                java.util.concurrent.CountDownLatch bekle = new java.util.concurrent.CountDownLatch(1);
                clip.addLineListener(evt -> {
                    if (javax.sound.sampled.LineEvent.Type.STOP.equals(evt.getType()))
                        bekle.countDown();
                });
                clip.start();
                bekle.await(3, java.util.concurrent.TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            log.warn("ses.wav çalınamadı", e);
            try { java.awt.Toolkit.getDefaultToolkit().beep(); } catch (Exception ignored) {}
        }
    }

    // ===== BİLDİRİM POPUP — sadece hatalar ve önemli olaylar için =====
    private void bildir(String mesaj, String renk) {
        if (aktifBildirim != null && aktifBildirim.isShowing()) {
            aktifBildirim.close();
        }

        Stage popup = new Stage();
        popup.initOwner(stage);
        popup.initStyle(javafx.stage.StageStyle.TRANSPARENT);
        popup.setAlwaysOnTop(true);

        Label mesajLbl = new Label(mesaj);
        mesajLbl.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        mesajLbl.setTextFill(Color.WHITE);
        mesajLbl.setWrapText(true);
        mesajLbl.setMaxWidth(320);

        Button kapatBtn = new Button("✕");
        kapatBtn.setStyle(
                "-fx-background-color: transparent; -fx-text-fill: white; " +
                        "-fx-cursor: hand; -fx-font-size: 14px; " +
                        "-fx-font-weight: bold; -fx-padding: 0 4;");
        kapatBtn.setOnAction(e -> popup.close());

        Region bosluk = new Region();
        HBox.setHgrow(bosluk, Priority.ALWAYS);

        HBox kutu = new HBox(8, mesajLbl, bosluk, kapatBtn);
        kutu.setAlignment(Pos.CENTER_LEFT);
        kutu.setPadding(new Insets(13, 16, 13, 18));
        kutu.setStyle(
                "-fx-background-color: " + renk + "f0; " +
                        "-fx-background-radius: 10; " +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.4), 14, 0, 0, 5);");
        kutu.setMinWidth(270);

        javafx.scene.Scene popupScene = new javafx.scene.Scene(kutu);
        popupScene.setFill(Color.TRANSPARENT);
        popup.setScene(popupScene);

        popup.setOnShown(ev -> {
            popup.setX(stage.getX() + stage.getWidth() - popup.getWidth() - 20);
            popup.setY(stage.getY() + stage.getHeight() - popup.getHeight() - 55);
        });

        popup.show();
        aktifBildirim = popup;

        javafx.animation.PauseTransition pt = new javafx.animation.PauseTransition(
                javafx.util.Duration.seconds(5));
        pt.setOnFinished(ev -> { if (popup.isShowing()) popup.close(); });
        pt.play();
    }
}
