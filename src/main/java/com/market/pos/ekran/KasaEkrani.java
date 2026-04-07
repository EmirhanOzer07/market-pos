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

import java.math.BigDecimal;
import java.util.*;

public class KasaEkrani {

    private final Stage stage;
    private TextField barkodField;
    private Label toplamLabel;
    private Label sonEklenenLabel;
    private TableView<SepetSatir> sepetTablosu;
    private ObservableList<SepetSatir> sepetVerisi;
    private boolean islemYapiliyor = false;
    private Stage aktifBildirim;

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

        // ===== ÜST BAR =====
        Label baslikLabel = new Label("🏪 EXPRESS SATIŞ");
        baslikLabel.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        baslikLabel.setTextFill(Color.WHITE);

        Label kullaniciLabel = new Label("👤 " + ApiClient.getKullaniciAdi());
        kullaniciLabel.setFont(Font.font("Arial", 11));
        kullaniciLabel.setTextFill(Color.web("#bdc3c7"));

        String ustBtnStyle =
                "-fx-background-radius: 5; -fx-cursor: hand; -fx-font-size: 11px; -fx-padding: 4 10;";

        Button yonetimBtn = new Button("⚙ Yönetim");
        yonetimBtn.setStyle("-fx-background-color: #34495e; -fx-text-fill: white; " + ustBtnStyle);
        yonetimBtn.setVisible("ADMIN".equals(ApiClient.getRol()));
        yonetimBtn.setManaged("ADMIN".equals(ApiClient.getRol()));

        bulunamayanBtn = new Button("⚠ Bulunamayan [0]");
        bulunamayanBtn.setStyle("-fx-background-color: #e67e22; -fx-text-fill: white; " + ustBtnStyle);
        bulunamayanBtn.setVisible(false);

        Button cikisBtn = new Button("🚪 Çıkış");
        cikisBtn.setStyle("-fx-background-color: #c0392b; -fx-text-fill: white; " + ustBtnStyle);

        Region ustBosluk = new Region();
        HBox.setHgrow(ustBosluk, Priority.ALWAYS);

        HBox ustBar = new HBox(8, baslikLabel, ustBosluk,
                kullaniciLabel, bulunamayanBtn, yonetimBtn, cikisBtn);
        ustBar.setAlignment(Pos.CENTER_LEFT);
        ustBar.setPadding(new Insets(7, 12, 7, 12));
        ustBar.setStyle("-fx-background-color: #1e2d3d;");

        // ===== SOL PANEL =====
        boolean isAdmin = "ADMIN".equals(ApiClient.getRol());

        Label odemeBaslik = new Label("ÖDEME YÖNTEMİ");
        odemeBaslik.setFont(Font.font("Arial", FontWeight.BOLD, 10));
        odemeBaslik.setTextFill(Color.web("#95a5a6"));
        odemeBaslik.setPadding(new Insets(4, 0, 4, 0));

        Button nakitBtn = new Button("💵  Nakit\n[F5]");
        nakitBtn.setPrefWidth(240);
        nakitBtn.setPrefHeight(72);
        nakitBtn.setFont(Font.font("Arial", FontWeight.BOLD, 15));
        nakitBtn.setStyle(
                "-fx-background-color: #27ae60; -fx-text-fill: white; " +
                        "-fx-background-radius: 10; -fx-cursor: hand;");
        nakitBtn.setWrapText(true);
        nakitBtn.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        Button kartBtn = new Button("💳  Kredi Kartı\n[F6]");
        kartBtn.setPrefWidth(240);
        kartBtn.setPrefHeight(72);
        kartBtn.setFont(Font.font("Arial", FontWeight.BOLD, 15));
        kartBtn.setStyle(
                "-fx-background-color: #2980b9; -fx-text-fill: white; " +
                        "-fx-background-radius: 10; -fx-cursor: hand;");
        kartBtn.setWrapText(true);
        kartBtn.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        Separator sep1 = new Separator();
        sep1.setPadding(new Insets(6, 0, 6, 0));

        Label islemBaslik = new Label("HIZLI İŞLEMLER");
        islemBaslik.setFont(Font.font("Arial", FontWeight.BOLD, 10));
        islemBaslik.setTextFill(Color.web("#95a5a6"));
        islemBaslik.setPadding(new Insets(4, 0, 4, 0));

        Button sonSilBtn = new Button("⌫  Son Satırı Sil");
        sonSilBtn.setPrefWidth(240);
        sonSilBtn.setPrefHeight(40);
        sonSilBtn.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        sonSilBtn.setStyle(
                "-fx-background-color: #e67e22; -fx-text-fill: white; " +
                        "-fx-background-radius: 8; -fx-cursor: hand;");

        Button temizleBtn = new Button("🗑  Sepeti Temizle");
        temizleBtn.setPrefWidth(240);
        temizleBtn.setPrefHeight(40);
        temizleBtn.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        temizleBtn.setStyle(
                "-fx-background-color: #7f8c8d; -fx-text-fill: white; " +
                        "-fx-background-radius: 8; -fx-cursor: hand;");

        // Hızlı fiyat güncelleme — tüm roller
        Button fiyatGuncelleBtn = new Button("✏  Hızlı Fiyat Güncelle  [F2]");
        fiyatGuncelleBtn.setPrefWidth(240);
        fiyatGuncelleBtn.setPrefHeight(40);
        fiyatGuncelleBtn.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        fiyatGuncelleBtn.setStyle(
                "-fx-background-color: #f39c12; -fx-text-fill: white; " +
                        "-fx-background-radius: 8; -fx-cursor: hand;");

        Separator sep2 = new Separator();
        sep2.setPadding(new Insets(6, 0, 6, 0));

        // Hızlı ürünler bölümü
        Separator sep3 = new Separator();
        sep3.setPadding(new Insets(4, 0, 4, 0));

        Label hizliBaslikLbl = new Label("HIZLI ÜRÜNLER");
        hizliBaslikLbl.setFont(Font.font("Arial", FontWeight.BOLD, 10));
        hizliBaslikLbl.setTextFill(Color.web("#95a5a6"));

        Button hizliEkleBtn = new Button("+");
        hizliEkleBtn.setPrefSize(22, 22);
        hizliEkleBtn.setStyle(
                "-fx-background-color: #27ae60; -fx-text-fill: white; " +
                "-fx-background-radius: 11; -fx-cursor: hand; " +
                "-fx-font-size: 14px; -fx-font-weight: bold; -fx-padding: 0;");
        hizliEkleBtn.setTooltip(new Tooltip("Hızlı ürün ekle"));

        Region hizliAra = new Region();
        HBox.setHgrow(hizliAra, Priority.ALWAYS);
        HBox hizliBaslikSatiri = new HBox(4, hizliBaslikLbl, hizliAra, hizliEkleBtn);
        hizliBaslikSatiri.setAlignment(Pos.CENTER_LEFT);

        hizliUrunlerPanel = new FlowPane(4, 4);
        hizliUrunlerPanel.setPrefWrapLength(252);

        // Son eklenen ürün bilgisi
        Label sonEklenenBaslik = new Label("SON EKLENEN");
        sonEklenenBaslik.setFont(Font.font("Arial", FontWeight.BOLD, 9));
        sonEklenenBaslik.setTextFill(Color.web("#95a5a6"));

        sonEklenenLabel = new Label("—");
        sonEklenenLabel.setFont(Font.font("Arial", 12));
        sonEklenenLabel.setTextFill(Color.web("#2c3e50"));
        sonEklenenLabel.setWrapText(true);
        sonEklenenLabel.setMaxWidth(230);

        VBox sonEklenenKutu = new VBox(3, sonEklenenBaslik, sonEklenenLabel);
        sonEklenenKutu.setStyle(
                "-fx-background-color: #eaf4fb; -fx-background-radius: 7; " +
                        "-fx-border-color: #aed6f1; -fx-border-radius: 7; " +
                        "-fx-border-width: 1; -fx-padding: 8;");

        // Toplam kutu
        Label toplamBaslik = new Label("GENEL TOPLAM");
        toplamBaslik.setFont(Font.font("Arial", FontWeight.BOLD, 10));
        toplamBaslik.setTextFill(Color.web("#bdc3c7"));

        toplamLabel = new Label("0,00 TL");
        toplamLabel.setFont(Font.font("Arial", FontWeight.BOLD, 28));
        toplamLabel.setTextFill(Color.web("#2ecc71"));

        VBox toplamKutu = new VBox(4, toplamBaslik, toplamLabel);
        toplamKutu.setStyle(
                "-fx-background-color: #1a252f; -fx-background-radius: 10; " +
                        "-fx-padding: 16; " +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 6, 0, 0, 2);");
        toplamKutu.setAlignment(Pos.CENTER);

        Region solBosluk = new Region();
        VBox.setVgrow(solBosluk, Priority.ALWAYS);

        VBox solPanel = new VBox(8,
                odemeBaslik, nakitBtn, kartBtn,
                sep1,
                islemBaslik, sonSilBtn, temizleBtn, sep2, fiyatGuncelleBtn,
                sep3, hizliBaslikSatiri, hizliUrunlerPanel,
                sonEklenenKutu,
                solBosluk, toplamKutu);
        solPanel.setPadding(new Insets(14));
        solPanel.setStyle(
                "-fx-background-color: #f4f6f7; " +
                        "-fx-border-color: #d5d8dc; -fx-border-width: 0 1 0 0;");
        solPanel.setPrefWidth(270);
        solPanel.setMinWidth(270);
        solPanel.setMaxWidth(270);

        // ===== BARKOD ALANI =====
        Label barkodLabel = new Label("Barkod");
        barkodLabel.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        barkodLabel.setTextFill(Color.web("#34495e"));

        Label f1Hint = new Label("[F1]");
        f1Hint.setFont(Font.font("Arial", 11));
        f1Hint.setTextFill(Color.web("#aab7b8"));

        barkodField = new TextField();
        barkodField.setPromptText("Barkod okutun veya  2 * barkod  yazın...");
        barkodField.setPrefHeight(42);
        barkodField.setFont(Font.font("Arial", 14));
        barkodField.setStyle(
                "-fx-border-color: #2980b9; -fx-border-width: 2; " +
                        "-fx-border-radius: 7; -fx-background-radius: 7; -fx-padding: 6 10;");
        HBox.setHgrow(barkodField, Priority.ALWAYS);

        Button ekleBtn = new Button("EKLE");
        ekleBtn.setPrefHeight(42);
        ekleBtn.setPrefWidth(80);
        ekleBtn.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        ekleBtn.setStyle(
                "-fx-background-color: #2980b9; -fx-text-fill: white; " +
                        "-fx-background-radius: 7; -fx-cursor: hand;");

        HBox barkodSatiri = new HBox(8, barkodLabel, f1Hint, barkodField, ekleBtn);
        barkodSatiri.setAlignment(Pos.CENTER_LEFT);
        barkodSatiri.setPadding(new Insets(10, 14, 10, 14));
        barkodSatiri.setStyle(
                "-fx-background-color: white; " +
                        "-fx-border-color: #d5d8dc; -fx-border-width: 0 0 1 0;");

        // ===== SEPET TABLOSU =====
        sepetVerisi = FXCollections.observableArrayList();
        sepetTablosu = new TableView<>(sepetVerisi);
        sepetTablosu.setPlaceholder(new Label("Sepet boş  —  barkod okutun veya F1'e basın"));
        sepetTablosu.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        sepetTablosu.setStyle("-fx-font-size: 13px;");
        sepetTablosu.setFixedCellSize(38);
        VBox.setVgrow(sepetTablosu, Priority.ALWAYS);

        // Alternating row colors
        sepetTablosu.setRowFactory(tv -> new TableRow<SepetSatir>() {
            @Override
            protected void updateItem(SepetSatir item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setStyle("");
                } else {
                    setStyle(getIndex() % 2 == 0
                            ? "-fx-background-color: #ffffff;"
                            : "-fx-background-color: #f7f9fc;");
                }
            }
        });

        // Sıra No
        TableColumn<SepetSatir, Integer> siraKol = new TableColumn<>("#");
        siraKol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : String.valueOf(getIndex() + 1));
                setStyle("-fx-alignment: CENTER; -fx-text-fill: #95a5a6;");
            }
        });
        siraKol.setPrefWidth(40);

        TableColumn<SepetSatir, String> barkodKol = new TableColumn<>("Barkod");
        barkodKol.setCellValueFactory(new PropertyValueFactory<>("barkod"));
        barkodKol.setPrefWidth(120);
        barkodKol.setStyle("-fx-text-fill: #7f8c8d;");

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
            @Override
            protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : String.format("%,.2f", item));
            }
        });

        TableColumn<SepetSatir, BigDecimal> tutarKol = new TableColumn<>("Tutar ₺");
        tutarKol.setCellValueFactory(new PropertyValueFactory<>("tutar"));
        tutarKol.setPrefWidth(105);
        tutarKol.setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-weight: bold;");
        tutarKol.setCellFactory(col -> new TableCell<SepetSatir, BigDecimal>() {
            @Override
            protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : String.format("%,.2f", item));
                setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-weight: bold; -fx-text-fill: #1a5276;");
            }
        });

        // Sil butonu kolonu
        TableColumn<SepetSatir, Void> silKol = new TableColumn<>("");
        silKol.setPrefWidth(48);
        silKol.setCellFactory(col -> new TableCell<>() {
            final Button btn = new Button("✕");
            {
                btn.setStyle(
                        "-fx-background-color: #fadbd8; -fx-text-fill: #e74c3c; " +
                                "-fx-background-radius: 4; -fx-cursor: hand; " +
                                "-fx-padding: 2 7; -fx-font-size: 11px; -fx-font-weight: bold;");
                btn.setOnMouseEntered(e -> btn.setStyle(
                        "-fx-background-color: #e74c3c; -fx-text-fill: white; " +
                                "-fx-background-radius: 4; -fx-cursor: hand; " +
                                "-fx-padding: 2 7; -fx-font-size: 11px; -fx-font-weight: bold;"));
                btn.setOnMouseExited(e -> btn.setStyle(
                        "-fx-background-color: #fadbd8; -fx-text-fill: #e74c3c; " +
                                "-fx-background-radius: 4; -fx-cursor: hand; " +
                                "-fx-padding: 2 7; -fx-font-size: 11px; -fx-font-weight: bold;"));
                btn.setOnAction(e -> {
                    SepetSatir satir = getTableView().getItems().get(getIndex());
                    sepetVerisi.remove(satir);
                    toplamGuncelle();
                    barkodField.requestFocus();
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });

        sepetTablosu.getColumns().addAll(
                siraKol, barkodKol, urunKol, adetKol, fiyatKol, tutarKol, silKol);

        // ===== SAĞ İÇ LAYOUT =====
        VBox sagIcLayout = new VBox(0, barkodSatiri, sepetTablosu);
        VBox.setVgrow(sepetTablosu, Priority.ALWAYS);
        VBox.setVgrow(sagIcLayout, Priority.ALWAYS);

        // ===== ANA LAYOUT =====
        HBox icerik = new HBox(0, solPanel, sagIcLayout);
        HBox.setHgrow(sagIcLayout, Priority.ALWAYS);
        VBox.setVgrow(icerik, Priority.ALWAYS);

        VBox anaLayout = new VBox(0, ustBar, icerik);
        VBox.setVgrow(icerik, Priority.ALWAYS);

        // ===== OLAYLAR =====
        barkodField.setOnAction(e -> barkodOkut());
        ekleBtn.setOnAction(e -> barkodOkut());
        nakitBtn.setOnAction(e -> odemeYap("NAKIT", nakitBtn));
        kartBtn.setOnAction(e -> odemeYap("KART", kartBtn));
        sonSilBtn.setOnAction(e -> sonUrunuSil());
        temizleBtn.setOnAction(e -> sepetiTemizle());
        yonetimBtn.setOnAction(e -> yonetimeGec());
        cikisBtn.setOnAction(e -> cikisYap());
        bulunamayanBtn.setOnAction(e -> bulunamayanBarkodlariGoster());
        fiyatGuncelleBtn.setOnAction(e -> hizliFiyatGuncelle());
        hizliEkleBtn.setOnAction(e -> hizliUrunEkleDialog());

        // Hover efektleri
        nakitBtn.setOnMouseEntered(e -> nakitBtn.setStyle(
                "-fx-background-color: #1e8449; -fx-text-fill: white; -fx-background-radius: 10; -fx-cursor: hand;"));
        nakitBtn.setOnMouseExited(e -> nakitBtn.setStyle(
                "-fx-background-color: #27ae60; -fx-text-fill: white; -fx-background-radius: 10; -fx-cursor: hand;"));
        kartBtn.setOnMouseEntered(e -> kartBtn.setStyle(
                "-fx-background-color: #1a6ea8; -fx-text-fill: white; -fx-background-radius: 10; -fx-cursor: hand;"));
        kartBtn.setOnMouseExited(e -> kartBtn.setStyle(
                "-fx-background-color: #2980b9; -fx-text-fill: white; -fx-background-radius: 10; -fx-cursor: hand;"));

        Scene scene = new Scene(anaLayout, 1280, 800);

        // Kısayol tuşları
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

        // Popup yok — sol panelde sessiz güncelleme
        String adetStr = miktar == (long) miktar
                ? String.valueOf((long) miktar)
                : String.valueOf(miktar);
        sonEklenenLabel.setText(urunAdi + "\n× " + adetStr + "  =  " +
                String.format("%,.2f ₺", fiyat.multiply(BigDecimal.valueOf(miktar))));
        sonEklenenLabel.setTextFill(Color.web("#1a5276"));
    }

    // ===== TOPLAMI GÜNCELLE =====
    private void toplamGuncelle() {
        BigDecimal toplam = sepetVerisi.stream()
                .map(SepetSatir::getTutar)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        toplamLabel.setText(String.format("%,.2f ₺", toplam));
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
        sonEklenenLabel.setTextFill(Color.web("#2c3e50"));
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
                        sonEklenenLabel.setTextFill(Color.web("#2c3e50"));
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
            try { ApiClient.cikisYap(); } catch (Exception ignored) {}
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
        } catch (Exception ignored) {}
    }

    private void hizliUrunleriKaydet() {
        try {
            java.nio.file.Files.createDirectories(HIZLI_URUNLER_DOSYASI.getParent());
            StringBuilder sb = new StringBuilder();
            for (String[] u : hizliUrunler) sb.append(u[0]).append("|").append(u[1]).append("\n");
            java.nio.file.Files.writeString(HIZLI_URUNLER_DOSYASI, sb.toString(),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
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
