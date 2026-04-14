package com.market.pos.ekran;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

/**
 * Yönetim paneli JavaFX ekranı (ADMIN rolü).
 *
 * <p>Ürün ekleme/güncelleme/silme, CSV toplu yükleme, personel yönetimi,
 * satış raporları, yedek alma/geri yükleme ve şifre değiştirme sekmeleri içerir.</p>
 */
public class YonetimEkrani {

    private final Stage stage;
    private Stage aktifBildirim;
    private List<Map<String, Object>> tumUrunler = new ArrayList<>();
    private java.util.Comparator<Map<String, Object>> aktifSiralama = null;

    public YonetimEkrani(Stage stage) {
        this.stage = stage;
    }

    public Scene olustur() {

        // ===== ÜST BAR =====
        Label baslik = new Label("⚙  YÖNETİM PANELİ");
        baslik.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        baslik.setTextFill(Color.WHITE);

        Label kullaniciLabel = new Label("👤  " + ApiClient.getKullaniciAdi());
        kullaniciLabel.setFont(Font.font("Arial", 13));
        kullaniciLabel.setTextFill(Color.web("#bdc3c7"));

        Button kasayaDonBtn = new Button("◀  Kasaya Dön");
        kasayaDonBtn.setPrefHeight(36);
        kasayaDonBtn.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        kasayaDonBtn.setStyle(
                "-fx-background-color: #27ae60; -fx-text-fill: white; " +
                        "-fx-background-radius: 6; -fx-cursor: hand; -fx-padding: 0 16;");
        kasayaDonBtn.setOnAction(e -> {
            KasaEkrani kasa = new KasaEkrani(stage);
            stage.setScene(kasa.olustur());
        });

        Region bosluk = new Region();
        HBox.setHgrow(bosluk, Priority.ALWAYS);

        HBox ustBar = new HBox(12, baslik, bosluk, kullaniciLabel, kasayaDonBtn);
        ustBar.setAlignment(Pos.CENTER_LEFT);
        ustBar.setPadding(new Insets(10, 15, 10, 15));
        ustBar.setStyle("-fx-background-color: #2c3e50;");

        // ===== SEKMELER =====
        TabPane sekmeler = new TabPane();
        sekmeler.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        sekmeler.setStyle(
                "-fx-tab-min-height: 44px; -fx-tab-min-width: 140px; " +
                        "-fx-font-size: 14px; -fx-font-weight: bold;");

        Tab urunlerSekme = new Tab("  📦   Ürünler  ", urunlerPaneliOlustur());
        Tab personelSekme = new Tab("  👥   Personel  ", personelPaneliOlustur());
        Tab ciroSekme = new Tab("  📊   Ciro  ", ciroPaneliOlustur());
        Tab raporSekme = new Tab("  📈   Rapor  ", raporPaneliOlustur());
        Tab yedekSekme = new Tab("  💾   Yedek  ", yedekPaneliOlustur());

        sekmeler.getTabs().addAll(urunlerSekme, personelSekme, ciroSekme, raporSekme, yedekSekme);
        VBox.setVgrow(sekmeler, Priority.ALWAYS);

        VBox anaLayout = new VBox(0, ustBar, sekmeler);
        VBox.setVgrow(sekmeler, Priority.ALWAYS);

        return new Scene(anaLayout, 1280, 800);
    }

    // ================================================
    // ÜRÜNLER PANELİ
    // ================================================
    private VBox urunlerPaneliOlustur() {

        ObservableList<Map<String, Object>> urunVerisi =
                FXCollections.observableArrayList();
        // SortedList — kolon başlığına tıklayarak sıralama
        javafx.collections.transformation.SortedList<Map<String, Object>> sortedVerisi =
                new javafx.collections.transformation.SortedList<>(urunVerisi);
        TableView<Map<String, Object>> tablo = new TableView<>(sortedVerisi);
        sortedVerisi.comparatorProperty().bind(tablo.comparatorProperty());
        tablo.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tablo.setStyle("-fx-font-size: 13px;");
        tablo.setFixedCellSize(38);
        VBox.setVgrow(tablo, Priority.ALWAYS);

        // ===== ARAMA =====
        TextField aramaField = new TextField();
        aramaField.setPromptText("🔍  Ürün adı veya barkod ara...");
        aramaField.setPrefHeight(38);
        aramaField.setPrefWidth(320);
        aramaField.setFont(Font.font("Arial", 13));
        aramaField.setStyle(
                "-fx-border-color: #3498db; -fx-border-width: 1.5; " +
                        "-fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 6;");
        aramaField.textProperty().addListener((obs, eski, yeni) ->
                aramayaGoreFiltrele(yeni, urunVerisi, tablo));

        Label urunSayisiLabel = new Label("0 ürün");
        urunSayisiLabel.setFont(Font.font("Arial", 13));
        urunSayisiLabel.setTextFill(Color.web("#7f8c8d"));

        // ===== SIRALAMA =====
        ComboBox<String> siralamaBox = new ComboBox<>();
        siralamaBox.getItems().addAll(
                "Varsayılan", "İsim A→Z", "İsim Z→A",
                "Barkod A→Z", "Barkod Z→A", "Fiyat ↑", "Fiyat ↓");
        siralamaBox.setValue("Varsayılan");
        siralamaBox.setPrefHeight(38);
        siralamaBox.setStyle("-fx-background-radius: 6; -fx-font-size: 13px;");

        siralamaBox.valueProperty().addListener((obs, eski, yeni) -> {
            switch (yeni != null ? yeni : "Varsayılan") {
                case "İsim A→Z"   -> aktifSiralama = java.util.Comparator.comparing(
                        u -> u.get("isim").toString().toLowerCase());
                case "İsim Z→A"   -> aktifSiralama = java.util.Comparator
                        .<Map<String, Object>, String>comparing(u -> u.get("isim").toString().toLowerCase())
                        .reversed();
                case "Barkod A→Z" -> aktifSiralama = (a, b) -> barkodKarsilastir(a, b);
                case "Barkod Z→A" -> aktifSiralama = (a, b) -> barkodKarsilastir(b, a);
                case "Fiyat ↑"    -> aktifSiralama = java.util.Comparator.comparingDouble(
                        u -> ((Number) u.get("fiyat")).doubleValue());
                case "Fiyat ↓"    -> aktifSiralama = java.util.Comparator
                        .comparingDouble((Map<String, Object> u) -> ((Number) u.get("fiyat")).doubleValue())
                        .reversed();
                default           -> aktifSiralama = null;
            }
            if (aktifSiralama != null) tumUrunler.sort(aktifSiralama);
            aramayaGoreFiltrele(aramaField.getText(), urunVerisi, tablo);
        });

        // ===== KOLONLAR =====
        TableColumn<Map<String, Object>, Integer> siraKol =
                new TableColumn<>("#");
        siraKol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : String.valueOf(getIndex() + 1));
                setStyle("-fx-alignment: CENTER;");
            }
        });
        siraKol.setPrefWidth(50);

        TableColumn<Map<String, Object>, String> barkodKol =
                new TableColumn<>("Barkod");
        barkodKol.setCellValueFactory(d ->
                new javafx.beans.property.SimpleStringProperty(
                        d.getValue().get("barkod").toString()));
        barkodKol.setPrefWidth(160);

        TableColumn<Map<String, Object>, String> isimKol =
                new TableColumn<>("Ürün Adı");
        isimKol.setCellValueFactory(d ->
                new javafx.beans.property.SimpleStringProperty(
                        d.getValue().get("isim").toString()));
        isimKol.setPrefWidth(340);

        TableColumn<Map<String, Object>, String> fiyatKol =
                new TableColumn<>("Fiyat (TL) ↕");
        fiyatKol.setCellValueFactory(d ->
                new javafx.beans.property.SimpleStringProperty(
                        String.format("%.2f",
                                ((Number) d.getValue().get("fiyat"))
                                        .doubleValue())));
        fiyatKol.setPrefWidth(110);
        fiyatKol.setStyle("-fx-alignment: CENTER-RIGHT;");
        // Numerik sıralama
        fiyatKol.setComparator((a, b) -> {
            try {
                return Double.compare(
                        Double.parseDouble(a.replace(",", ".")),
                        Double.parseDouble(b.replace(",", ".")));
            } catch (Exception e) { return a.compareTo(b); }
        });

        TableColumn<Map<String, Object>, Void> duzenleKol =
                new TableColumn<>("Düzenle");
        duzenleKol.setPrefWidth(100);
        duzenleKol.setCellFactory(col -> new TableCell<>() {
            final Button btn = new Button("✏  Düzenle");
            {
                btn.setStyle(
                        "-fx-background-color: #f39c12; -fx-text-fill: white; " +
                                "-fx-background-radius: 5; -fx-cursor: hand; " +
                                "-fx-padding: 4 10; -fx-font-size: 12px;");
                btn.setOnAction(e -> fiyatGuncellemeDialoguAc(
                        getTableView().getItems().get(getIndex()),
                        urunVerisi, tablo));
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });

        TableColumn<Map<String, Object>, Void> silKol =
                new TableColumn<>("Sil");
        silKol.setPrefWidth(80);
        silKol.setCellFactory(col -> new TableCell<>() {
            final Button btn = new Button("🗑  Sil");
            {
                btn.setStyle(
                        "-fx-background-color: #e74c3c; -fx-text-fill: white; " +
                                "-fx-background-radius: 5; -fx-cursor: hand; " +
                                "-fx-padding: 4 10; -fx-font-size: 12px;");
                btn.setOnAction(e -> {
                    Map<String, Object> urun =
                            getTableView().getItems().get(getIndex());
                    onaylaVeSil(urun.get("isim").toString(),
                            () -> urunSil(
                                    Long.valueOf(urun.get("id").toString()),
                                    urun.get("isim").toString(),
                                    urunVerisi, tablo));
                });
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });

        barkodKol.setSortable(true);
        isimKol.setSortable(true);
        fiyatKol.setSortable(true);
        fiyatKol.setComparator((a, b) -> {
            try {
                return Double.compare(Double.parseDouble(a.replace(",", ".")),
                        Double.parseDouble(b.replace(",", ".")));
            } catch (Exception e) { return a.compareTo(b); }
        });

        tablo.getColumns().addAll(
                siraKol, barkodKol, isimKol, fiyatKol, duzenleKol, silKol);

        tablo.setRowFactory(tv -> {
            TableRow<Map<String, Object>> satir = new TableRow<>();
            satir.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !satir.isEmpty()) {
                    fiyatGuncellemeDialoguAc(satir.getItem(), urunVerisi, tablo);
                }
            });
            return satir;
        });

        urunVerisi.addListener((javafx.collections.ListChangeListener<Map<String, Object>>) c ->
                urunSayisiLabel.setText(urunVerisi.size() + " ürün gösteriliyor"));

        // ===== EKLEME FORMU =====
        TextField yeniBarkod = new TextField();
        yeniBarkod.setPromptText("Barkod");
        yeniBarkod.setPrefWidth(160);
        yeniBarkod.setPrefHeight(38);
        yeniBarkod.setFont(Font.font("Arial", 13));

        TextField yeniIsim = new TextField();
        yeniIsim.setPromptText("Ürün Adı");
        yeniIsim.setPrefWidth(260);
        yeniIsim.setPrefHeight(38);
        yeniIsim.setFont(Font.font("Arial", 13));

        TextField yeniFiyat = new TextField();
        yeniFiyat.setPromptText("Fiyat");
        yeniFiyat.setPrefWidth(110);
        yeniFiyat.setPrefHeight(38);
        yeniFiyat.setFont(Font.font("Arial", 13));

        Button ekleBtn = new Button("➕  Ürün Ekle");
        ekleBtn.setPrefHeight(38);
        ekleBtn.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        ekleBtn.setStyle(
                "-fx-background-color: #27ae60; -fx-text-fill: white; " +
                        "-fx-background-radius: 6; -fx-cursor: hand; -fx-padding: 0 18;");

        Button excelBtn = new Button("📊  Excel / CSV Yükle");
        excelBtn.setPrefHeight(38);
        excelBtn.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        excelBtn.setStyle(
                "-fx-background-color: #1a6b35; -fx-text-fill: white; " +
                        "-fx-background-radius: 6; -fx-cursor: hand; -fx-padding: 0 18;");

        Button yenileBtn = new Button("🔄  Yenile");
        yenileBtn.setPrefHeight(38);
        yenileBtn.setFont(Font.font("Arial", 13));
        yenileBtn.setStyle(
                "-fx-background-color: #3498db; -fx-text-fill: white; " +
                        "-fx-background-radius: 6; -fx-cursor: hand; -fx-padding: 0 16;");

        Region formBosluk = new Region();
        HBox.setHgrow(formBosluk, Priority.ALWAYS);

        HBox ekleFormu = new HBox(10,
                new Label("Barkod:"), yeniBarkod,
                new Label("Ürün Adı:"), yeniIsim,
                new Label("Fiyat:"), yeniFiyat,
                ekleBtn, formBosluk, excelBtn, yenileBtn);
        ekleFormu.setAlignment(Pos.CENTER_LEFT);
        ekleFormu.setPadding(new Insets(12, 15, 12, 15));
        ekleFormu.setStyle(
                "-fx-background-color: #f0f3f4; " +
                        "-fx-border-color: #dee2e6; -fx-border-width: 0 0 1 0;");

        HBox aramaSatiri = new HBox(12, aramaField, siralamaBox, urunSayisiLabel);
        aramaSatiri.setAlignment(Pos.CENTER_LEFT);
        aramaSatiri.setPadding(new Insets(8, 15, 8, 15));
        aramaSatiri.setStyle(
                "-fx-background-color: white; " +
                        "-fx-border-color: #dee2e6; -fx-border-width: 0 0 1 0;");

        ekleBtn.setOnAction(e ->
                urunEkle(yeniBarkod, yeniIsim, yeniFiyat, urunVerisi, tablo));
        excelBtn.setOnAction(e -> excelYukle(urunVerisi, tablo));
        yenileBtn.setOnAction(e -> urunleriYukle(urunVerisi, tablo, urunSayisiLabel));

        // Enter ile ekleme
        yeniFiyat.setOnAction(e ->
                urunEkle(yeniBarkod, yeniIsim, yeniFiyat, urunVerisi, tablo));

        VBox panel = new VBox(0, ekleFormu, aramaSatiri, tablo);
        VBox.setVgrow(tablo, Priority.ALWAYS);
        urunleriYukle(urunVerisi, tablo, urunSayisiLabel);
        return panel;
    }

    /** Barkodları sayısal önce, sayısal değilse alfabetik sıralar. */
    private int barkodKarsilastir(Map<String, Object> a, Map<String, Object> b) {
        String ba = a.get("barkod").toString();
        String bb = b.get("barkod").toString();
        try {
            return Long.compare(Long.parseLong(ba), Long.parseLong(bb));
        } catch (NumberFormatException e) {
            return ba.compareTo(bb);
        }
    }

    private void aramayaGoreFiltrele(String aranan,
                                     ObservableList<Map<String, Object>> veri,
                                     TableView<Map<String, Object>> tablo) {
        veri.clear();
        if (aranan == null || aranan.isEmpty()) {
            veri.addAll(tumUrunler);
        } else {
            String k = aranan.toLowerCase();
            for (Map<String, Object> u : tumUrunler) {
                if (u.get("isim").toString().toLowerCase().contains(k) ||
                        u.get("barkod").toString().toLowerCase().contains(k))
                    veri.add(u);
            }
        }
        tablo.refresh();
    }

    private void urunleriYukle(ObservableList<Map<String, Object>> veri,
                               TableView<Map<String, Object>> tablo,
                               Label sayiLabel) {
        new Thread(() -> {
            try {
                List liste = ApiClient.getList(
                        "/api/urunler/liste/" + ApiClient.getMarketId());
                Platform.runLater(() -> {
                    tumUrunler.clear();
                    for (Object o : liste)
                        tumUrunler.add((Map<String, Object>) o);
                    if (aktifSiralama != null) tumUrunler.sort(aktifSiralama);
                    veri.clear();
                    veri.addAll(tumUrunler);
                    tablo.refresh();
                    sayiLabel.setText(veri.size() + " ürün");
                    bildir("✓ " + veri.size() + " ürün yüklendi.", "#27ae60");
                });
            } catch (Exception ex) {
                Platform.runLater(() ->
                        bildir("❌ Ürünler yüklenemedi!", "#e74c3c"));
            }
        }).start();
    }

    private void urunEkle(TextField barkodF, TextField isimF,
                          TextField fiyatF,
                          ObservableList<Map<String, Object>> veri,
                          TableView<Map<String, Object>> tablo) {
        String barkod = barkodF.getText().trim();
        String isim = isimF.getText().trim();
        String fiyatStr = fiyatF.getText().trim().replace(",", ".");

        if (barkod.isEmpty() || isim.isEmpty() || fiyatStr.isEmpty()) {
            bildir("❌ Lütfen tüm alanları doldurun!", "#e74c3c");
            return;
        }
        double fiyat;
        try {
            fiyat = Double.parseDouble(fiyatStr);
            if (fiyat < 0) throw new Exception();
        } catch (Exception e) {
            bildir("❌ Geçersiz fiyat!", "#e74c3c");
            return;
        }
        final double f = fiyat;
        new Thread(() -> {
            try {
                ApiClient.post("/api/urunler/ekle/" + ApiClient.getMarketId(),
                        Map.of("barkod", barkod, "isim", isim, "fiyat", f));
                Platform.runLater(() -> {
                    bildir("✓ Eklendi: " + isim, "#27ae60");
                    barkodF.clear(); isimF.clear(); fiyatF.clear();
                    barkodF.requestFocus();
                    urunleriYukle(veri, tablo,
                            new Label()); // sayı label referansı yok burada
                });
            } catch (Exception ex) {
                Platform.runLater(() ->
                        bildir("❌ Ürün eklenemedi!", "#e74c3c"));
            }
        }).start();
    }

    private void fiyatGuncellemeDialoguAc(Map<String, Object> urun,
                                          ObservableList<Map<String, Object>> veri,
                                          TableView<Map<String, Object>> tablo) {

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Ürün Düzenle");
        dialog.setHeaderText(
                "Ürün: " + urun.get("isim").toString() +
                        "\nBarkod: " + urun.get("barkod").toString());

        ButtonType kaydetTipi = new ButtonType(
                "💾  Kaydet", ButtonBar.ButtonData.OK_DONE);
        ButtonType iptalTipi = new ButtonType(
                "İptal", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes()
                .addAll(kaydetTipi, iptalTipi);

        TextField isimField = new TextField(urun.get("isim").toString());
        isimField.setPrefWidth(320);
        isimField.setPrefHeight(38);
        isimField.setFont(Font.font("Arial", 14));

        TextField fiyatField = new TextField(
                String.format("%.2f",
                        ((Number) urun.get("fiyat")).doubleValue()));
        fiyatField.setPrefWidth(160);
        fiyatField.setPrefHeight(38);
        fiyatField.setFont(Font.font("Arial", 14));

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(14);
        grid.setPadding(new Insets(24, 24, 16, 24));

        Label isimLbl = new Label("Ürün Adı:");
        isimLbl.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        Label fiyatLbl = new Label("Fiyat (TL):");
        fiyatLbl.setFont(Font.font("Arial", FontWeight.BOLD, 13));

        grid.add(isimLbl, 0, 0);
        grid.add(isimField, 1, 0);
        grid.add(fiyatLbl, 0, 1);
        grid.add(fiyatField, 1, 1);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().setPrefWidth(520);

        // Kaydet butonunu Enter ile de tetikle
        fiyatField.setOnAction(e -> {
            dialog.getDialogPane().lookupButton(kaydetTipi)
                    .fireEvent(new javafx.event.ActionEvent());
        });

        dialog.showAndWait().ifPresent(result -> {
            if (result != kaydetTipi) return;
            String yeniIsim = isimField.getText().trim();
            String fiyatStr = fiyatField.getText().trim().replace(",", ".");
            if (yeniIsim.isEmpty()) {
                bildir("❌ Ürün adı boş olamaz!", "#e74c3c");
                return;
            }
            double fiyat;
            try {
                fiyat = Double.parseDouble(fiyatStr);
            } catch (Exception e) {
                bildir("❌ Geçersiz fiyat!", "#e74c3c");
                return;
            }
            final double f = fiyat;
            Long id = Long.valueOf(urun.get("id").toString());
            String barkod = urun.get("barkod").toString();
            new Thread(() -> {
                try {
                    ApiClient.put("/api/urunler/guncelle/" + id,
                            Map.of("barkod", barkod,
                                    "isim", yeniIsim, "fiyat", f));
                    Platform.runLater(() -> {
                        bildir("✓ Güncellendi: " + yeniIsim, "#27ae60");
                        urunleriYukle(veri, tablo, new Label());
                    });
                } catch (Exception ex) {
                    Platform.runLater(() ->
                            bildir("❌ Güncellenemedi!", "#e74c3c"));
                }
            }).start();
        });
    }

    private void onaylaVeSil(String isim, Runnable onOnayla) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Silme Onayı");
        alert.setHeaderText("\"" + isim + "\" silinecek!");
        alert.setContentText("Bu işlem geri alınamaz. Devam etmek istiyor musunuz?");

        ButtonType evetBtn = new ButtonType(
                "✓  Evet, Sil", ButtonBar.ButtonData.OK_DONE);
        ButtonType hayirBtn = new ButtonType(
                "İptal", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(evetBtn, hayirBtn);

        alert.showAndWait().ifPresent(r -> {
            if (r == evetBtn) onOnayla.run();
        });
    }

    private void urunSil(Long id, String isim,
                         ObservableList<Map<String, Object>> veri,
                         TableView<Map<String, Object>> tablo) {
        new Thread(() -> {
            try {
                ApiClient.delete("/api/urunler/sil/" + id);
                Platform.runLater(() -> {
                    bildir("✓ Silindi: " + isim, "#27ae60");
                    urunleriYukle(veri, tablo, new Label());
                });
            } catch (Exception ex) {
                String mesaj = ex.getMessage();
                Platform.runLater(() ->
                        bildir("❌ " + (mesaj != null ? mesaj : "Bağlantı hatası!"), "#e74c3c"));
            }
        }).start();
    }

    // ===== EXCEL/CSV YÜKLEME =====
    private void excelYukle(ObservableList<Map<String, Object>> veri,
                            TableView<Map<String, Object>> tablo) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Excel veya CSV Dosyası Seç");
        fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter(
                        "Desteklenen Dosyalar", "*.csv", "*.xlsx", "*.xls"),
                new FileChooser.ExtensionFilter("CSV", "*.csv"),
                new FileChooser.ExtensionFilter("Excel", "*.xlsx", "*.xls"));

        File dosya = fc.showOpenDialog(stage);
        if (dosya == null) return;

        // Yükleme ilerleme dialogu
        Alert yukleniyorAlert = new Alert(Alert.AlertType.INFORMATION);
        yukleniyorAlert.setTitle("Dosya Yükleniyor");
        yukleniyorAlert.setHeaderText("⏳  Lütfen bekleyin...");
        yukleniyorAlert.setContentText(
                "Dosya okunuyor: " + dosya.getName() + "\n" +
                        "Bu işlem birkaç saniye sürebilir.");
        yukleniyorAlert.getButtonTypes().clear();
// Kapatma için gizli buton ekle
        yukleniyorAlert.getButtonTypes().add(ButtonType.CLOSE);
        javafx.scene.Node kapat = yukleniyorAlert.getDialogPane()
                .lookupButton(ButtonType.CLOSE);
        if (kapat != null) kapat.setVisible(false);
        yukleniyorAlert.show();

        bildir("⏳  Dosya yükleniyor: " + dosya.getName(), "#3498db");

        new Thread(() -> {
            try {
                byte[] dosyaBytes = Files.readAllBytes(dosya.toPath());
                String sinir = "----FormBoundary" + System.currentTimeMillis();
                String dosyaAdi = dosya.getName();
                String contentType = dosyaAdi.toLowerCase().endsWith(".csv") ?
                        "text/csv" : "application/octet-stream";

                byte[] on = ("--" + sinir + "\r\n" +
                        "Content-Disposition: form-data; name=\"dosya\"; " +
                        "filename=\"" + dosyaAdi + "\"\r\n" +
                        "Content-Type: " + contentType + "\r\n\r\n").getBytes();
                byte[] son = ("\r\n--" + sinir + "--\r\n").getBytes();
                byte[] govde = new byte[on.length + dosyaBytes.length + son.length];
                System.arraycopy(on, 0, govde, 0, on.length);
                System.arraycopy(dosyaBytes, 0, govde, on.length, dosyaBytes.length);
                System.arraycopy(son, 0, govde,
                        on.length + dosyaBytes.length, son.length);

                java.net.http.HttpRequest istek =
                        java.net.http.HttpRequest.newBuilder()
                                .uri(java.net.URI.create(
                                        "http://localhost:8080/api/urunler/yukle/" +
                                                ApiClient.getMarketId()))
                                .header("Authorization", "Bearer " + ApiClient.getToken())
                                .header("Content-Type",
                                        "multipart/form-data; boundary=" + sinir)
                                .POST(java.net.http.HttpRequest.BodyPublishers
                                        .ofByteArray(govde))
                                .build();

                java.net.http.HttpResponse<String> yanit =
                        java.net.http.HttpClient.newHttpClient()
                                .send(istek,
                                        java.net.http.HttpResponse.BodyHandlers.ofString());

                Platform.runLater(() -> {
                    // Yükleme dialogunu kapat ve ana pencereyi öne getir
                    yukleniyorAlert.getDialogPane().getScene().getWindow().hide();
                    stage.toFront();

                    if (yanit.statusCode() == 200) {
                        try {
                            com.fasterxml.jackson.databind.ObjectMapper mapper =
                                    new com.fasterxml.jackson.databind.ObjectMapper();
                            Map<String, Object> sonuc =
                                    mapper.readValue(yanit.body(), Map.class);

                            int eklenen    = ((Number) sonuc.getOrDefault("eklenen", 0)).intValue();
                            int guncellenen= ((Number) sonuc.getOrDefault("guncellenen", 0)).intValue();
                            int hataSayisi = ((Number) sonuc.getOrDefault("hataSayisi", 0)).intValue();
                            int toplam     = ((Number) sonuc.getOrDefault("toplamIslem", 0)).intValue();
                            @SuppressWarnings("unchecked")
                            List<Map<String, Object>> cakismalar =
                                    (List<Map<String, Object>>) sonuc.getOrDefault("cakismalar", new ArrayList<>());

                            // Sonuç özeti — showAndWait ile öne çıkar
                            StringBuilder icerik = new StringBuilder();
                            icerik.append("📊  İşlenen: ").append(toplam).append(" satır\n");
                            icerik.append("✅  Eklenen: ").append(eklenen).append(" ürün\n");
                            icerik.append("⏩  Atlanan (aynı): ").append(
                                    toplam - eklenen - guncellenen - hataSayisi - cakismalar.size()
                                    < 0 ? 0 : toplam - eklenen - guncellenen - hataSayisi - cakismalar.size()
                            ).append(" ürün\n");
                            if (!cakismalar.isEmpty())
                                icerik.append("⚠  Çakışma: ").append(cakismalar.size()).append(" ürün (sorulacak)\n");
                            if (hataSayisi > 0) {
                                icerik.append("❌  Hatalı satır: ").append(hataSayisi).append("\n");
                                Object hatalarObj = sonuc.get("hatalar");
                                if (hatalarObj instanceof List<?> hataList) {
                                    int goster = Math.min(hataList.size(), 5);
                                    for (int i = 0; i < goster; i++)
                                        icerik.append("  • ").append(hataList.get(i)).append("\n");
                                    if (hataList.size() > 5)
                                        icerik.append("  ... ve ").append(hataList.size()-5).append(" hata daha.");
                                }
                            }

                            Alert sonucAlert = new Alert(Alert.AlertType.INFORMATION);
                            sonucAlert.initOwner(stage);
                            sonucAlert.setTitle("Dosya Yükleme Sonucu");
                            sonucAlert.setHeaderText("✅  " + dosyaAdi + " işlendi!");
                            sonucAlert.setContentText(icerik.toString());
                            sonucAlert.getDialogPane().setPrefWidth(440);
                            sonucAlert.showAndWait(); // ← Öne çıkar, kapanana kadar bekle

                            urunleriYukle(veri, tablo, new Label());

                            // Çakışmalar varsa kullanıcıya sor
                            if (!cakismalar.isEmpty()) {
                                cakismayiCoz(cakismalar, 0, veri, tablo);
                            } else {
                                bildir("✓ Eklenen: " + eklenen +
                                        "  |  Hatalı: " + hataSayisi, "#27ae60");
                            }

                        } catch (Exception e) {
                            bildir("✓ Dosya yüklendi!", "#27ae60");
                            urunleriYukle(veri, tablo, new Label());
                        }
                    } else {
                        Alert hataAlert = new Alert(Alert.AlertType.ERROR);
                        hataAlert.initOwner(stage);
                        hataAlert.setTitle("Yükleme Hatası");
                        hataAlert.setHeaderText("❌  Dosya yüklenemedi!");
                        hataAlert.setContentText(yanit.body());
                        hataAlert.showAndWait();
                        bildir("❌ Yükleme hatası!", "#e74c3c");
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    yukleniyorAlert.close();
                    bildir("❌ Dosya okunamadı: " + ex.getMessage(), "#e74c3c");
                });
            }
        }).start();
    }

    // ===== ÇAKIŞMA ÇÖZÜCÜ — her çakışma için kullanıcıya sorar =====
    @SuppressWarnings("unchecked")
    private void cakismayiCoz(List<Map<String, Object>> cakismalar, int index,
                               ObservableList<Map<String, Object>> veri,
                               TableView<Map<String, Object>> tablo) {
        if (index >= cakismalar.size()) {
            urunleriYukle(veri, tablo, new Label());
            bildir("✓ Tüm çakışmalar çözüldü.", "#27ae60");
            return;
        }

        Map<String, Object> c = cakismalar.get(index);
        String barkod   = c.get("barkod").toString();
        String dbIsim   = c.get("dbIsim").toString();
        String csvIsim  = c.get("csvIsim").toString();
        String dbFiyat  = String.format("%.2f ₺", ((Number) c.get("dbFiyat")).doubleValue());
        String csvFiyat = String.format("%.2f ₺", ((Number) c.get("csvFiyat")).doubleValue());
        Long id = Long.valueOf(c.get("id").toString());

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.initOwner(stage);
        alert.setTitle("Çakışma " + (index + 1) + "/" + cakismalar.size());
        alert.setHeaderText("Barkod çakışması: " + barkod);
        alert.setContentText(
                "Sistemdeki:\n  " + dbIsim + "  —  " + dbFiyat + "\n\n" +
                "CSV'den gelen:\n  " + csvIsim + "  —  " + csvFiyat + "\n\n" +
                "Hangisi sisteminizde kalsın?"
        );
        alert.getDialogPane().setPrefWidth(420);

        ButtonType csvBtn = new ButtonType("CSV'den Gelen", ButtonBar.ButtonData.YES);
        ButtonType dbBtn  = new ButtonType("Sistemdekini Tut", ButtonBar.ButtonData.NO);
        alert.getButtonTypes().setAll(csvBtn, dbBtn);

        Optional<ButtonType> secim = alert.showAndWait();

        if (secim.isPresent() && secim.get() == csvBtn) {
            // CSV'den gelen değeri uygula
            new Thread(() -> {
                try {
                    ApiClient.put("/api/urunler/guncelle/" + id,
                            Map.of("barkod", barkod,
                                   "isim",   c.get("csvIsim"),
                                   "fiyat",  c.get("csvFiyat")));
                } catch (Exception ignored) {}
                Platform.runLater(() -> cakismayiCoz(cakismalar, index + 1, veri, tablo));
            }).start();
        } else {
            // Sistemdekini tut — bir sonrakine geç
            cakismayiCoz(cakismalar, index + 1, veri, tablo);
        }
    }

    // ================================================
    // PERSONEL PANELİ
    // ================================================
    private VBox personelPaneliOlustur() {

        ObservableList<Map<String, Object>> personelVerisi =
                FXCollections.observableArrayList();
        TableView<Map<String, Object>> tablo = new TableView<>(personelVerisi);
        tablo.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tablo.setStyle("-fx-font-size: 13px;");
        tablo.setFixedCellSize(38);
        VBox.setVgrow(tablo, Priority.ALWAYS);

        TableColumn<Map<String, Object>, Integer> siraKol =
                new TableColumn<>("#");
        siraKol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : String.valueOf(getIndex() + 1));
                setStyle("-fx-alignment: CENTER;");
            }
        });
        siraKol.setPrefWidth(50);

        TableColumn<Map<String, Object>, String> adiKol =
                new TableColumn<>("Kullanıcı Adı");
        adiKol.setCellValueFactory(d ->
                new javafx.beans.property.SimpleStringProperty(
                        d.getValue().get("kullaniciAdi").toString()));
        adiKol.setPrefWidth(280);

        TableColumn<Map<String, Object>, String> rolKol =
                new TableColumn<>("Rol");
        rolKol.setCellValueFactory(d ->
                new javafx.beans.property.SimpleStringProperty(
                        d.getValue().get("rol").toString()));
        rolKol.setPrefWidth(150);

        TableColumn<Map<String, Object>, Void> silKol =
                new TableColumn<>("İşlem");
        silKol.setPrefWidth(120);
        silKol.setCellFactory(col -> new TableCell<>() {
            final Button btn = new Button("🗑  Kaldır");
            {
                btn.setStyle(
                        "-fx-background-color: #e74c3c; -fx-text-fill: white; " +
                                "-fx-background-radius: 5; -fx-cursor: hand; " +
                                "-fx-padding: 4 12; -fx-font-size: 12px;");
                btn.setOnAction(e -> {
                    Map<String, Object> p =
                            getTableView().getItems().get(getIndex());
                    if ("ADMIN".equals(p.get("rol").toString())) {
                        bildir("❌ Admin hesabı silinemez!", "#e74c3c");
                        return;
                    }
                    onaylaVeSil(p.get("kullaniciAdi").toString(),
                            () -> personelSil(
                                    Long.valueOf(p.get("id").toString()),
                                    p.get("kullaniciAdi").toString(),
                                    personelVerisi, tablo));
                });
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });

        tablo.getColumns().addAll(siraKol, adiKol, rolKol, silKol);

        // Kasiyer ekleme formu
        TextField kasiyerAdi = new TextField();
        kasiyerAdi.setPromptText("Kullanıcı Adı");
        kasiyerAdi.setPrefWidth(220);
        kasiyerAdi.setPrefHeight(38);
        kasiyerAdi.setFont(Font.font("Arial", 13));

        PasswordField kasiyerSifre = new PasswordField();
        kasiyerSifre.setPromptText("Şifre (min. 8 karakter)");
        kasiyerSifre.setPrefWidth(240);
        kasiyerSifre.setPrefHeight(38);
        kasiyerSifre.setFont(Font.font("Arial", 13));

        Button ekleBtn = new Button("➕  Kasiyer Ekle");
        ekleBtn.setPrefHeight(38);
        ekleBtn.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        ekleBtn.setStyle(
                "-fx-background-color: #27ae60; -fx-text-fill: white; " +
                        "-fx-background-radius: 6; -fx-cursor: hand; -fx-padding: 0 18;");

        Button yenileBtn = new Button("🔄  Yenile");
        yenileBtn.setPrefHeight(38);
        yenileBtn.setFont(Font.font("Arial", 13));
        yenileBtn.setStyle(
                "-fx-background-color: #3498db; -fx-text-fill: white; " +
                        "-fx-background-radius: 6; -fx-cursor: hand; -fx-padding: 0 16;");

        Button sifreDegistirBtn = new Button("🔑  Şifremi Değiştir");
        sifreDegistirBtn.setPrefHeight(38);
        sifreDegistirBtn.setFont(Font.font("Arial", 13));
        sifreDegistirBtn.setStyle(
                "-fx-background-color: #8e44ad; -fx-text-fill: white; " +
                        "-fx-background-radius: 6; -fx-cursor: hand; -fx-padding: 0 16;");
        sifreDegistirBtn.setOnAction(e -> sifreDegistirDialoguAc());

        Region formBosluk2 = new Region();
        HBox.setHgrow(formBosluk2, Priority.ALWAYS);

        HBox ekleFormu = new HBox(12,
                new Label("Kullanıcı Adı:"), kasiyerAdi,
                new Label("Şifre:"), kasiyerSifre,
                ekleBtn, yenileBtn, formBosluk2, sifreDegistirBtn);
        ekleFormu.setAlignment(Pos.CENTER_LEFT);
        ekleFormu.setPadding(new Insets(12, 15, 12, 15));
        ekleFormu.setStyle(
                "-fx-background-color: #f0f3f4; " +
                        "-fx-border-color: #dee2e6; -fx-border-width: 0 0 1 0;");

        ekleBtn.setOnAction(e ->
                kasiyerEkle(kasiyerAdi, kasiyerSifre, personelVerisi, tablo));
        yenileBtn.setOnAction(e -> personelYukle(personelVerisi, tablo));
        kasiyerSifre.setOnAction(e ->
                kasiyerEkle(kasiyerAdi, kasiyerSifre, personelVerisi, tablo));

        VBox panel = new VBox(0, ekleFormu, tablo);
        VBox.setVgrow(tablo, Priority.ALWAYS);
        personelYukle(personelVerisi, tablo);
        return panel;
    }

    private void personelYukle(ObservableList<Map<String, Object>> veri,
                               TableView<Map<String, Object>> tablo) {
        new Thread(() -> {
            try {
                List liste = ApiClient.getList(
                        "/api/kullanicilar/liste/" + ApiClient.getMarketId());
                Platform.runLater(() -> {
                    veri.clear();
                    for (Object o : liste) veri.add((Map<String, Object>) o);
                    tablo.refresh();
                    bildir("✓ " + veri.size() + " personel yüklendi.", "#27ae60");
                });
            } catch (Exception ex) {
                Platform.runLater(() ->
                        bildir("❌ Personel yüklenemedi!", "#e74c3c"));
            }
        }).start();
    }

    private void kasiyerEkle(TextField adiF, PasswordField sifreF,
                             ObservableList<Map<String, Object>> veri,
                             TableView<Map<String, Object>> tablo) {
        String adi = adiF.getText().trim();
        String sifre = sifreF.getText().trim();

        if (adi.isEmpty() || sifre.isEmpty()) {
            bildir("❌ Kullanıcı adı ve şifre boş olamaz!", "#e74c3c");
            return;
        }
        if (sifre.length() < 8) {
            bildir("❌ Şifre en az 8 karakter olmalı!", "#e74c3c");
            return;
        }
        new Thread(() -> {
            try {
                Map<String, Object> yanit = ApiClient.post(
                        "/api/kullanicilar/ekle/" + ApiClient.getMarketId(),
                        Map.of("kullaniciAdi", adi, "sifre", sifre));
                Platform.runLater(() -> {
                    String mesaj = yanit.getOrDefault("mesaj", "").toString();
                    if (mesaj.contains("Başarılı")) {
                        bildir("✓ Kasiyer eklendi: " + adi, "#27ae60");
                        adiF.clear();
                        sifreF.clear();
                        adiF.requestFocus();
                        personelYukle(veri, tablo);
                    } else {
                        bildir("❌ HATA: " + mesaj, "#e74c3c");
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() ->
                        bildir("❌ Bağlantı hatası!", "#e74c3c"));
            }
        }).start();
    }

    private void personelSil(Long id, String adi,
                             ObservableList<Map<String, Object>> veri,
                             TableView<Map<String, Object>> tablo) {
        new Thread(() -> {
            try {
                int status = ApiClient.delete("/api/kullanicilar/sil/" + id);
                Platform.runLater(() -> {
                    if (status == 200) {
                        bildir("✓ Silindi: " + adi, "#27ae60");
                        personelYukle(veri, tablo);
                    } else {
                        bildir("❌ Silinemedi!", "#e74c3c");
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() ->
                        bildir("❌ Bağlantı hatası!", "#e74c3c"));
            }
        }).start();
    }

    private void sifreDegistirDialoguAc() {
        PasswordField eskiSifreF = new PasswordField();
        eskiSifreF.setPromptText("Mevcut şifreniz");
        eskiSifreF.setPrefHeight(36);

        PasswordField yeniSifreF = new PasswordField();
        yeniSifreF.setPromptText("Yeni şifre (min. 8 karakter)");
        yeniSifreF.setPrefHeight(36);

        PasswordField yeniSifreTekrarF = new PasswordField();
        yeniSifreTekrarF.setPromptText("Yeni şifre (tekrar)");
        yeniSifreTekrarF.setPrefHeight(36);

        Label hataMesaji = new Label("");
        hataMesaji.setTextFill(Color.web("#e74c3c"));
        hataMesaji.setFont(Font.font("Arial", 12));
        hataMesaji.setWrapText(true);

        VBox icerik = new VBox(10,
                new Label("Mevcut şifre:"), eskiSifreF,
                new Label("Yeni şifre:"), yeniSifreF,
                new Label("Yeni şifre tekrar:"), yeniSifreTekrarF,
                hataMesaji);
        icerik.setPadding(new Insets(10, 0, 0, 0));
        icerik.setPrefWidth(320);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Şifre Değiştir");
        dialog.setHeaderText("Şifrenizi değiştirin — " + ApiClient.getKullaniciAdi());
        dialog.getDialogPane().setContent(icerik);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Button okBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.setText("Değiştir");
        okBtn.setStyle("-fx-background-color: #8e44ad; -fx-text-fill: white;");

        dialog.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.OK) return;

            String eski = eskiSifreF.getText();
            String yeni = yeniSifreF.getText();
            String tekrar = yeniSifreTekrarF.getText();

            if (eski.isBlank() || yeni.isBlank()) {
                bildir("❌ Alanlar boş bırakılamaz!", "#e74c3c");
                return;
            }
            if (!yeni.equals(tekrar)) {
                bildir("❌ Yeni şifreler eşleşmiyor!", "#e74c3c");
                return;
            }
            if (yeni.length() < 8) {
                bildir("❌ Yeni şifre en az 8 karakter olmalı!", "#e74c3c");
                return;
            }

            new Thread(() -> {
                try {
                    Map<String, Object> yanit = ApiClient.put(
                            "/api/kullanicilar/sifre-degistir",
                            Map.of("eskiSifre", eski, "yeniSifre", yeni));
                    Platform.runLater(() -> {
                        if (yanit.containsKey("hata")) {
                            bildir("❌ " + yanit.get("hata"), "#e74c3c");
                        } else {
                            bildir("✓ Şifreniz başarıyla değiştirildi.", "#27ae60");
                        }
                    });
                } catch (Exception ex) {
                    Platform.runLater(() ->
                            bildir("❌ Hata: " + ex.getMessage(), "#e74c3c"));
                }
            }).start();
        });
    }

    // ================================================
    // CİRO PANELİ
    // ================================================
    private VBox ciroPaneliOlustur() {

        // Kart kutusu oluşturucu
        VBox toplamKart = kartOlustur(
                "TOPLAM CİRO", "--", "#27ae60", "#e8f8f0");
        VBox nakitKart = kartOlustur(
                "💵  NAKİT", "--", "#2980b9", "#eaf4fb");
        VBox kartKart = kartOlustur(
                "💳  KREDİ KARTI", "--", "#8e44ad", "#f5eef8");
        VBox satisKart = kartOlustur(
                "🛒  SATIŞ SAYISI", "--", "#e67e22", "#fef9e7");

        Label toplamDeger = (Label) ((VBox) toplamKart).getChildren().get(1);
        Label nakitDeger = (Label) ((VBox) nakitKart).getChildren().get(1);
        Label kartDeger = (Label) ((VBox) kartKart).getChildren().get(1);
        Label satisDeger = (Label) ((VBox) satisKart).getChildren().get(1);

        HBox kartlar = new HBox(20,
                toplamKart, nakitKart, kartKart, satisKart);
        kartlar.setPadding(new Insets(30));
        HBox.setHgrow(toplamKart, Priority.ALWAYS);
        HBox.setHgrow(nakitKart, Priority.ALWAYS);
        HBox.setHgrow(kartKart, Priority.ALWAYS);
        HBox.setHgrow(satisKart, Priority.ALWAYS);

        Button yenileBtn = new Button("🔄  Yenile");
        yenileBtn.setPrefHeight(42);
        yenileBtn.setPrefWidth(160);
        yenileBtn.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        yenileBtn.setStyle(
                "-fx-background-color: #3498db; -fx-text-fill: white; " +
                        "-fx-background-radius: 8; -fx-cursor: hand;");

        HBox butonSatiri = new HBox(yenileBtn);
        butonSatiri.setPadding(new Insets(0, 30, 20, 30));

        VBox panel = new VBox(0, kartlar, butonSatiri);

        Runnable yukle = () -> new Thread(() -> {
            try {
                Map<String, Object> ozet = ApiClient.get(
                        "/api/satis/ozet/" + ApiClient.getMarketId());
                Platform.runLater(() -> {
                    double toplam = ((Number) ozet
                            .getOrDefault("toplamCiro", 0)).doubleValue();
                    double nakit = ((Number) ozet
                            .getOrDefault("nakitCiro", 0)).doubleValue();
                    double kart = ((Number) ozet
                            .getOrDefault("kartCiro", 0)).doubleValue();
                    int sayisi = ((Number) ozet
                            .getOrDefault("satisSayisi", 0)).intValue();

                    toplamDeger.setText(
                            String.format("%,.2f TL", toplam));
                    nakitDeger.setText(
                            String.format("%,.2f TL", nakit));
                    kartDeger.setText(
                            String.format("%,.2f TL", kart));
                    satisDeger.setText(sayisi + " adet");
                    bildir("✓ Ciro bilgileri güncellendi.", "#27ae60");
                });
            } catch (Exception ex) {
                Platform.runLater(() ->
                        bildir("❌ Ciro yüklenemedi!", "#e74c3c"));
            }
        }).start();

        yenileBtn.setOnAction(e -> yukle.run());
        yukle.run();
        return panel;
    }

    private VBox kartOlustur(String baslik, String deger,
                             String renkHex, String arkaplanHex) {
        Label baslikLbl = new Label(baslik);
        baslikLbl.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        baslikLbl.setTextFill(Color.web("#7f8c8d"));

        Label degerLbl = new Label(deger);
        degerLbl.setFont(Font.font("Arial", FontWeight.BOLD, 28));
        degerLbl.setTextFill(Color.web(renkHex));

        VBox kart = new VBox(10, baslikLbl, degerLbl);
        kart.setPadding(new Insets(20, 24, 20, 24));
        kart.setStyle(
                "-fx-background-color: " + arkaplanHex + "; " +
                        "-fx-background-radius: 10; " +
                        "-fx-border-color: " + renkHex + "44; " +
                        "-fx-border-radius: 10; -fx-border-width: 1.5; " +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.06), 8, 0, 0, 2);");
        return kart;
    }

    // ===== RAPOR PANELİ =====
    private VBox raporPaneliOlustur() {

        // Hızlı seçim butonları
        Button bugunBtn   = raporHizliBtn("Bugün",    "#2980b9");
        Button haftaBtn   = raporHizliBtn("Bu Hafta", "#8e44ad");
        Button ayBtn      = raporHizliBtn("Bu Ay",    "#27ae60");
        Button yilBtn     = raporHizliBtn("Bu Yıl",   "#e67e22");

        Label baslangicLbl = new Label("Başlangıç:");
        baslangicLbl.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        javafx.scene.control.DatePicker baslangicPicker = new javafx.scene.control.DatePicker(
                java.time.LocalDate.now().withDayOfMonth(1));
        baslangicPicker.setPrefHeight(36);

        Label bitisLbl = new Label("Bitiş:");
        bitisLbl.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        javafx.scene.control.DatePicker bitisPicker = new javafx.scene.control.DatePicker(
                java.time.LocalDate.now());
        bitisPicker.setPrefHeight(36);

        Button raporBtn = new Button("📈  Rapor Al");
        raporBtn.setPrefHeight(36);
        raporBtn.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        raporBtn.setStyle("-fx-background-color: #2c3e50; -fx-text-fill: white; " +
                "-fx-background-radius: 7; -fx-cursor: hand; -fx-padding: 0 18;");

        HBox filtreSatiri = new HBox(12,
                bugunBtn, haftaBtn, ayBtn, yilBtn,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                baslangicLbl, baslangicPicker,
                bitisLbl, bitisPicker,
                raporBtn);
        filtreSatiri.setAlignment(Pos.CENTER_LEFT);
        filtreSatiri.setPadding(new Insets(20, 24, 16, 24));
        filtreSatiri.setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #dee2e6; " +
                "-fx-border-width: 0 0 1 0;");

        // Sonuç kartları
        VBox toplamKart2 = kartOlustur("TOPLAM CİRO",   "--", "#27ae60", "#e8f8f0");
        VBox nakitKart2  = kartOlustur("💵  NAKİT",     "--", "#2980b9", "#eaf4fb");
        VBox kartKart2   = kartOlustur("💳  KART",      "--", "#8e44ad", "#f5eef8");
        VBox satisKart2  = kartOlustur("🛒  SATIŞ SAYISI","--","#e67e22","#fef9e7");

        Label toplamDeger2 = (Label) toplamKart2.getChildren().get(1);
        Label nakitDeger2  = (Label) nakitKart2.getChildren().get(1);
        Label kartDeger2   = (Label) kartKart2.getChildren().get(1);
        Label satisDeger2  = (Label) satisKart2.getChildren().get(1);

        HBox kartlarSatiri = new HBox(20, toplamKart2, nakitKart2, kartKart2, satisKart2);
        kartlarSatiri.setPadding(new Insets(24));
        HBox.setHgrow(toplamKart2, Priority.ALWAYS);
        HBox.setHgrow(nakitKart2,  Priority.ALWAYS);
        HBox.setHgrow(kartKart2,   Priority.ALWAYS);
        HBox.setHgrow(satisKart2,  Priority.ALWAYS);

        Label durumLbl = new Label("Tarih aralığı seçip 'Rapor Al' butonuna basın.");
        durumLbl.setFont(Font.font("Arial", 13));
        durumLbl.setTextFill(Color.web("#7f8c8d"));
        durumLbl.setPadding(new Insets(0, 24, 0, 24));

        VBox panel = new VBox(0, filtreSatiri, kartlarSatiri, durumLbl);
        VBox.setVgrow(panel, Priority.ALWAYS);

        // Rapor yükle
        Runnable raporYukle = () -> {
            java.time.LocalDate bas = baslangicPicker.getValue();
            java.time.LocalDate bit = bitisPicker.getValue();
            if (bas == null || bit == null || bas.isAfter(bit)) {
                bildir("❌ Geçerli bir tarih aralığı seçin!", "#e74c3c");
                return;
            }
            raporBtn.setDisable(true);
            raporBtn.setText("Yükleniyor...");
            new Thread(() -> {
                try {
                    Map<String, Object> rapor = ApiClient.get(
                            "/api/satis/rapor/" + ApiClient.getMarketId() +
                            "?baslangic=" + bas + "&bitis=" + bit);
                    Platform.runLater(() -> {
                        if (rapor == null) {
                            bildir("❌ Rapor alınamadı!", "#e74c3c");
                        } else {
                            toplamDeger2.setText(String.format("%,.2f TL",
                                    ((Number) rapor.getOrDefault("toplamCiro", 0)).doubleValue()));
                            nakitDeger2.setText(String.format("%,.2f TL",
                                    ((Number) rapor.getOrDefault("nakitCiro", 0)).doubleValue()));
                            kartDeger2.setText(String.format("%,.2f TL",
                                    ((Number) rapor.getOrDefault("kartCiro", 0)).doubleValue()));
                            satisDeger2.setText(
                                    ((Number) rapor.getOrDefault("satisSayisi", 0)).intValue() + " adet");
                            durumLbl.setText("📅  " + bas + " — " + bit + " tarih aralığı raporu");
                            durumLbl.setTextFill(Color.web("#2c3e50"));
                        }
                        raporBtn.setDisable(false);
                        raporBtn.setText("📈  Rapor Al");
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        bildir("❌ Bağlantı hatası!", "#e74c3c");
                        raporBtn.setDisable(false);
                        raporBtn.setText("📈  Rapor Al");
                    });
                }
            }).start();
        };

        raporBtn.setOnAction(e -> raporYukle.run());

        // Hızlı buton işlemleri
        bugunBtn.setOnAction(e -> {
            java.time.LocalDate bugun = java.time.LocalDate.now();
            baslangicPicker.setValue(bugun);
            bitisPicker.setValue(bugun);
            raporYukle.run();
        });
        haftaBtn.setOnAction(e -> {
            java.time.LocalDate bugun = java.time.LocalDate.now();
            baslangicPicker.setValue(bugun.with(java.time.DayOfWeek.MONDAY));
            bitisPicker.setValue(bugun);
            raporYukle.run();
        });
        ayBtn.setOnAction(e -> {
            java.time.LocalDate bugun = java.time.LocalDate.now();
            baslangicPicker.setValue(bugun.withDayOfMonth(1));
            bitisPicker.setValue(bugun);
            raporYukle.run();
        });
        yilBtn.setOnAction(e -> {
            java.time.LocalDate bugun = java.time.LocalDate.now();
            baslangicPicker.setValue(bugun.withDayOfYear(1));
            bitisPicker.setValue(bugun);
            raporYukle.run();
        });

        return panel;
    }

    private Button raporHizliBtn(String metin, String renk) {
        Button btn = new Button(metin);
        btn.setPrefHeight(34);
        btn.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        btn.setStyle("-fx-background-color: " + renk + "; -fx-text-fill: white; " +
                "-fx-background-radius: 6; -fx-cursor: hand; -fx-padding: 0 14;");
        return btn;
    }

    // ================================================
    // YEDEK PANELİ
    // ================================================
    private VBox yedekPaneliOlustur() {

        // ── Uyarı bandı ──
        Label uyariLbl = new Label(
                "⚠  UYARI: Geri yükleme seçilen tarihe ait anlık görüntüye döner. " +
                "Bu tarihten sonra yapılan tüm satış ve ürün değişiklikleri kaybolur. " +
                "Uygulama otomatik olarak yeniden başlayacaktır.");
        uyariLbl.setFont(Font.font("Arial", 13));
        uyariLbl.setTextFill(Color.web("#7d6608"));
        uyariLbl.setWrapText(true);

        HBox uyariBant = new HBox(uyariLbl);
        uyariBant.setPadding(new Insets(12, 16, 12, 16));
        uyariBant.setStyle(
                "-fx-background-color: #fef9e7; " +
                "-fx-border-color: #f0c000; -fx-border-width: 0 0 1 0;");

        // ── Tablo ──
        ObservableList<Map<String, Object>> veri = FXCollections.observableArrayList();
        TableView<Map<String, Object>> tablo = new TableView<>(veri);
        tablo.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tablo.setStyle("-fx-font-size: 13px;");
        tablo.setFixedCellSize(42);
        tablo.setPlaceholder(new Label("Yedek bulunamadı."));
        VBox.setVgrow(tablo, Priority.ALWAYS);

        TableColumn<Map<String, Object>, String> turKol = new TableColumn<>("Tür");
        turKol.setPrefWidth(90);
        turKol.setCellValueFactory(d ->
                new javafx.beans.property.SimpleStringProperty(
                        d.getValue().get("tur").toString()));
        turKol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                boolean gunluk = "GÜNLÜK".equals(item);
                setStyle("-fx-alignment: CENTER; -fx-font-weight: bold; " +
                        "-fx-text-fill: " + (gunluk ? "#1a5276" : "#6e2f01") + ";");
            }
        });

        TableColumn<Map<String, Object>, String> tarihKol = new TableColumn<>("Tarih & Saat");
        tarihKol.setPrefWidth(160);
        tarihKol.setCellValueFactory(d ->
                new javafx.beans.property.SimpleStringProperty(
                        formatliTarih(d.getValue().get("tarih").toString())));

        TableColumn<Map<String, Object>, String> aciklamaKol = new TableColumn<>("Açıklama");
        aciklamaKol.setPrefWidth(140);
        aciklamaKol.setCellValueFactory(d ->
                new javafx.beans.property.SimpleStringProperty(
                        d.getValue().getOrDefault("aciklama", "").toString()));

        TableColumn<Map<String, Object>, String> boyutKol = new TableColumn<>("Boyut");
        boyutKol.setPrefWidth(80);
        boyutKol.setCellValueFactory(d -> {
            long kb = ((Number) d.getValue().getOrDefault("boyutKB", 0)).longValue();
            String boyut = kb >= 1024
                    ? String.format("%.1f MB", kb / 1024.0)
                    : kb + " KB";
            return new javafx.beans.property.SimpleStringProperty(boyut);
        });
        boyutKol.setStyle("-fx-alignment: CENTER-RIGHT;");

        TableColumn<Map<String, Object>, Void> islemKol = new TableColumn<>("İşlem");
        islemKol.setPrefWidth(130);
        islemKol.setCellFactory(col -> new TableCell<>() {
            final Button btn = new Button("↩  Geri Yükle");
            {
                btn.setStyle(
                        "-fx-background-color: #c0392b; -fx-text-fill: white; " +
                        "-fx-background-radius: 5; -fx-cursor: hand; " +
                        "-fx-padding: 5 12; -fx-font-size: 12px; -fx-font-weight: bold;");
                btn.setOnAction(e -> {
                    Map<String, Object> satir = getTableView().getItems().get(getIndex());
                    yedekGeriYukle(satir);
                });
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });

        tablo.getColumns().addAll(turKol, tarihKol, aciklamaKol, boyutKol, islemKol);

        // ── Üst araç çubuğu ──
        Label baslik = new Label("Mevcut Yedekler");
        baslik.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        baslik.setTextFill(Color.web("#2c3e50"));

        Label sayiLbl = new Label();
        sayiLbl.setFont(Font.font("Arial", 13));
        sayiLbl.setTextFill(Color.web("#7f8c8d"));

        Button yenileBtn = new Button("🔄  Yenile");
        yenileBtn.setPrefHeight(36);
        yenileBtn.setFont(Font.font("Arial", 13));
        yenileBtn.setStyle(
                "-fx-background-color: #3498db; -fx-text-fill: white; " +
                "-fx-background-radius: 6; -fx-cursor: hand; -fx-padding: 0 16;");

        Region bosluk = new Region();
        HBox.setHgrow(bosluk, Priority.ALWAYS);

        HBox araçCubugu = new HBox(12, baslik, sayiLbl, bosluk, yenileBtn);
        araçCubugu.setAlignment(Pos.CENTER_LEFT);
        araçCubugu.setPadding(new Insets(12, 15, 12, 15));
        araçCubugu.setStyle(
                "-fx-background-color: #f8f9fa; " +
                "-fx-border-color: #dee2e6; -fx-border-width: 0 0 1 0;");

        Runnable yukle = () -> new Thread(() -> {
            try {
                java.util.List liste = ApiClient.getList("/api/yedek/liste");
                Platform.runLater(() -> {
                    veri.clear();
                    for (Object o : liste) veri.add((Map<String, Object>) o);
                    tablo.refresh();
                    sayiLbl.setText(veri.size() + " yedek");
                });
            } catch (Exception ex) {
                Platform.runLater(() -> bildir("❌ Yedek listesi yüklenemedi!", "#e74c3c"));
            }
        }).start();

        yenileBtn.setOnAction(e -> yukle.run());
        yukle.run();

        VBox panel = new VBox(0, uyariBant, araçCubugu, tablo, excelBolumuOlustur());
        VBox.setVgrow(tablo, Priority.ALWAYS);
        return panel;
    }

    // ── Excel Yedek Bölümü ──────────────────────────────────────────────────
    private VBox excelBolumuOlustur() {

        // Başlık çubuğu
        Label baslik = new Label("📊  Excel Ürün Listesi Yedeği");
        baslik.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        baslik.setTextFill(Color.web("#1a5276"));

        Label aciklama = new Label(
                "Her gece 23:59'da otomatik alınır • Son 20 dosya saklanır • " +
                "Google Drive klasörünü aşağıdaki yola yönlendirin");
        aciklama.setFont(Font.font("Arial", 12));
        aciklama.setTextFill(Color.web("#7f8c8d"));

        // Klasör yolu etiketi (endpoint'ten gelecek)
        Label klasorLbl = new Label("Klasör: yükleniyor...");
        klasorLbl.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        klasorLbl.setTextFill(Color.web("#2c3e50"));
        klasorLbl.setWrapText(true);

        Button klasorAcBtn = new Button("📁  Klasörü Aç");
        klasorAcBtn.setFont(Font.font("Arial", 12));
        klasorAcBtn.setStyle(
                "-fx-background-color: #7f8c8d; -fx-text-fill: white; " +
                "-fx-background-radius: 6; -fx-cursor: hand; -fx-padding: 4 12;");

        Button excelAlBtn = new Button("📥  Excel Yedek Al");
        excelAlBtn.setFont(Font.font("Arial", 12));
        excelAlBtn.setStyle(
                "-fx-background-color: #27ae60; -fx-text-fill: white; " +
                "-fx-background-radius: 6; -fx-cursor: hand; -fx-padding: 4 14;");

        Button yenileExcelBtn = new Button("🔄");
        yenileExcelBtn.setFont(Font.font("Arial", 12));
        yenileExcelBtn.setStyle(
                "-fx-background-color: #3498db; -fx-text-fill: white; " +
                "-fx-background-radius: 6; -fx-cursor: hand; -fx-padding: 4 10;");

        Region bosluk = new Region();
        HBox.setHgrow(bosluk, Priority.ALWAYS);

        HBox araçCubugu = new HBox(10, excelAlBtn, klasorAcBtn, bosluk, yenileExcelBtn);
        araçCubugu.setAlignment(Pos.CENTER_LEFT);
        araçCubugu.setPadding(new Insets(8, 15, 8, 15));

        // Küçük Excel listesi tablosu
        ObservableList<Map<String, Object>> excelVeri = FXCollections.observableArrayList();
        TableView<Map<String, Object>> excelTablo = new TableView<>(excelVeri);
        excelTablo.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        excelTablo.setStyle("-fx-font-size: 12px;");
        excelTablo.setFixedCellSize(36);
        excelTablo.setPrefHeight(180);
        excelTablo.setMaxHeight(180);
        excelTablo.setPlaceholder(new Label("Henüz Excel yedeği yok."));

        TableColumn<Map<String, Object>, String> dosyaKol = new TableColumn<>("Dosya Adı");
        dosyaKol.setCellValueFactory(d ->
                new javafx.beans.property.SimpleStringProperty(
                        d.getValue().get("dosyaAdi").toString()));

        TableColumn<Map<String, Object>, String> excelTarihKol = new TableColumn<>("Tarih");
        excelTarihKol.setPrefWidth(160);
        excelTarihKol.setCellValueFactory(d ->
                new javafx.beans.property.SimpleStringProperty(
                        formatliTarih(d.getValue().getOrDefault("tarih", "").toString())));

        TableColumn<Map<String, Object>, String> excelBoyutKol = new TableColumn<>("Boyut");
        excelBoyutKol.setPrefWidth(80);
        excelBoyutKol.setCellValueFactory(d -> {
            long kb = ((Number) d.getValue().getOrDefault("boyutKB", 0)).longValue();
            return new javafx.beans.property.SimpleStringProperty(
                    kb >= 1024 ? String.format("%.1f MB", kb / 1024.0) : kb + " KB");
        });

        excelTablo.getColumns().addAll(dosyaKol, excelTarihKol, excelBoyutKol);

        // Listeyi yükle
        final String[] klasorYolu = {""};
        Runnable excelListeYukle = () -> new Thread(() -> {
            try {
                java.util.List liste = ApiClient.getList("/api/yedek/excel/liste");
                Platform.runLater(() -> {
                    excelVeri.clear();
                    for (Object o : liste) excelVeri.add((Map<String, Object>) o);
                    excelTablo.refresh();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> bildir("❌ Excel listesi yüklenemedi!", "#e74c3c"));
            }
        }).start();

        // Klasör yolunu al (excel yedeği al endpoint'inden)
        excelAlBtn.setOnAction(e -> {
            excelAlBtn.setDisable(true);
            excelAlBtn.setText("⏳  Hazırlanıyor...");
            new Thread(() -> {
                try {
                    Map<String, Object> sonuc = (Map<String, Object>)
                            ApiClient.post("/api/yedek/excel", Map.of());
                    Platform.runLater(() -> {
                        bildir("✓ " + sonuc.get("mesaj"), "#27ae60");
                        Object klasor = sonuc.get("klasor");
                        if (klasor != null) {
                            klasorLbl.setText("Klasör: " + klasor);
                            klasorYolu[0] = klasor.toString();
                        }
                        excelAlBtn.setDisable(false);
                        excelAlBtn.setText("📥  Excel Yedek Al");
                        excelListeYukle.run();
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        bildir("❌ Excel yedeği alınamadı!", "#e74c3c");
                        excelAlBtn.setDisable(false);
                        excelAlBtn.setText("📥  Excel Yedek Al");
                    });
                }
            }).start();
        });

        klasorAcBtn.setOnAction(e -> {
            String yol = klasorYolu[0];
            if (yol.isBlank()) {
                yol = System.getProperty("user.home")
                        + "\\AppData\\Local\\MarketPOS\\yedek\\excel";
            }
            try {
                java.awt.Desktop.getDesktop().open(new java.io.File(yol));
            } catch (Exception ex) {
                bildir("❌ Klasör açılamadı: " + ex.getMessage(), "#e74c3c");
            }
        });

        yenileExcelBtn.setOnAction(e -> excelListeYukle.run());
        excelListeYukle.run();

        // Layout
        VBox bilgiBandi = new VBox(4, baslik, aciklama, klasorLbl);
        bilgiBandi.setPadding(new Insets(12, 15, 8, 15));
        bilgiBandi.setStyle(
                "-fx-background-color: #eaf4fb; " +
                "-fx-border-color: #aed6f1; -fx-border-width: 1 0 0 0;");

        VBox bolum = new VBox(0, bilgiBandi, araçCubugu, excelTablo);
        bolum.setStyle("-fx-border-color: #dee2e6; -fx-border-width: 1 0 0 0;");
        return bolum;
    }

    /** "2026-04-09 14:30:00" → "09 Nis 2026  14:30" */
    private String formatliTarih(String ham) {
        if (ham == null || ham.isBlank()) return "";
        try {
            // ham: "2026-04-09 14:30:00"
            java.time.LocalDateTime ldt = java.time.LocalDateTime.parse(
                    ham, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String[] aylar = {"", "Oca", "Şub", "Mar", "Nis", "May", "Haz",
                               "Tem", "Ağu", "Eyl", "Eki", "Kas", "Ara"};
            return String.format("%02d %s %d  %02d:%02d",
                    ldt.getDayOfMonth(), aylar[ldt.getMonthValue()], ldt.getYear(),
                    ldt.getHour(), ldt.getMinute());
        } catch (Exception e) {
            return ham;
        }
    }

    private void yedekGeriYukle(Map<String, Object> satir) {
        String dosyaAdi  = satir.get("dosyaAdi").toString();
        String tur       = satir.get("tur").toString();
        String tarih     = formatliTarih(satir.get("tarih").toString());
        String aciklama  = satir.getOrDefault("aciklama", "").toString();
        String format    = satir.getOrDefault("format", "ESKİ").toString();

        // Eski binary format — geri yüklenemiyor
        if ("ESKİ".equals(format)) {
            Alert hata = new Alert(Alert.AlertType.ERROR);
            hata.initOwner(stage);
            hata.setTitle("Eski Format");
            hata.setHeaderText("Bu yedek geri yüklenemiyor");
            hata.setContentText(
                    "\"" + dosyaAdi + "\" eski binary formatta oluşturulmuş.\n\n" +
                    "Yeni format yedekler otomatik olarak alınmaya başlandı.\n" +
                    "Lütfen yeni oluşturulan bir yedeği kullanın.");
            hata.showAndWait();
            return;
        }

        String icerik = "Seçilen yedek:\n"
                + "  Tür: " + tur + "\n"
                + "  Tarih: " + tarih + "\n"
                + (aciklama.isEmpty() ? "" : "  Açıklama: " + aciklama + "\n")
                + "\n⚠  Bu tarihten sonraki TÜM veriler kaybolacak!\n"
                + "Uygulama otomatik olarak yeniden başlayacaktır.\n\n"
                + "Devam etmek istiyor musunuz?";

        Alert onay = new Alert(Alert.AlertType.CONFIRMATION);
        onay.initOwner(stage);
        onay.setTitle("Yedek Geri Yükleme Onayı");
        onay.setHeaderText("⚠  Bu işlem geri alınamaz!");
        onay.setContentText(icerik);
        onay.getDialogPane().setPrefWidth(480);

        ButtonType evetBtn  = new ButtonType("✓  Evet, Geri Yükle", ButtonBar.ButtonData.OK_DONE);
        ButtonType hayirBtn = new ButtonType("İptal", ButtonBar.ButtonData.CANCEL_CLOSE);
        onay.getButtonTypes().setAll(evetBtn, hayirBtn);

        onay.showAndWait().ifPresent(secim -> {
            if (secim != evetBtn) return;

            // İkinci onay — önemli işlem olduğu için çift onay
            Alert kesinOnay = new Alert(Alert.AlertType.WARNING);
            kesinOnay.initOwner(stage);
            kesinOnay.setTitle("Son Onay");
            kesinOnay.setHeaderText("Emin misiniz?");
            kesinOnay.setContentText(
                    "\"" + tarih + "\" tarihli yedeğe dönülecek.\n" +
                    "Uygulama kapanıp yeniden açılacak.");
            ButtonType kesinEvet = new ButtonType("🔁  Evet, Başlat", ButtonBar.ButtonData.OK_DONE);
            ButtonType kesinIptal = new ButtonType("Vazgeç", ButtonBar.ButtonData.CANCEL_CLOSE);
            kesinOnay.getButtonTypes().setAll(kesinEvet, kesinIptal);

            kesinOnay.showAndWait().ifPresent(k -> {
                if (k != kesinEvet) return;
                bildir("⏳  Geri yükleme başlatılıyor...", "#2980b9");
                new Thread(() -> {
                    try {
                        ApiClient.post("/api/yedek/geri-yukle",
                                java.util.Map.of("dosyaAdi", dosyaAdi));
                        // Cevap gelirse (1500ms içinde) — uygulama zaten kapanıyor
                        Platform.runLater(() ->
                                bildir("✓ Başlatıldı. Uygulama kapanıyor...", "#27ae60"));
                    } catch (Exception ex) {
                        // Connection refused = uygulama kapandı — normal
                        String hata = ex.getMessage();
                        if (hata == null || hata.contains("refused") || hata.contains("reset")) {
                            // Bağlantı koptu = uygulama kapanıyor, bu beklenen davranış
                            Platform.runLater(() ->
                                    bildir("✓ Başlatıldı. Uygulama yeniden başlıyor...", "#27ae60"));
                        } else {
                            Platform.runLater(() ->
                                    bildir("❌ HATA: " + hata, "#e74c3c"));
                        }
                    }
                }).start();
            });
        });
    }

    // ===== BİLDİRİM — kapatılabilir popup pencere, 4 saniye sonra kapanır =====
    private void bildir(String mesaj, String renk) {
        Platform.runLater(() -> {
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
            kutu.setPadding(new Insets(12, 14, 12, 16));
            kutu.setStyle(
                    "-fx-background-color: " + renk + "ee; " +
                            "-fx-background-radius: 8; " +
                            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.35), 12, 0, 0, 4);");
            kutu.setMinWidth(260);

            javafx.scene.Scene popupScene = new javafx.scene.Scene(kutu);
            popupScene.setFill(Color.TRANSPARENT);
            popup.setScene(popupScene);

            popup.setOnShown(ev -> {
                popup.setX(stage.getX() + stage.getWidth() - popup.getWidth() - 18);
                popup.setY(stage.getY() + stage.getHeight() - popup.getHeight() - 50);
            });

            popup.show();
            aktifBildirim = popup;

            javafx.animation.PauseTransition pt = new javafx.animation.PauseTransition(
                    javafx.util.Duration.seconds(4));
            pt.setOnFinished(ev -> { if (popup.isShowing()) popup.close(); });
            pt.play();
        });
    }
}