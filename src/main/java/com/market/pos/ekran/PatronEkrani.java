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
import javafx.stage.Stage;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class PatronEkrani {

    private static final java.net.http.HttpClient HTTP_CLIENT =
            java.net.http.HttpClient.newHttpClient();
    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper()
                    .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                    .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final Stage stage;
    private Stage aktifBildirim;

    // Doğrulandıktan sonra hafızada tutulur — her işlemde tekrar sorulmaz
    private String dogrulanmisSifre = null;

    // UI referansları
    private VBox loginPanel;
    private VBox anaPanel;
    private ObservableList<Map<String, Object>> marketVerisi;
    private TableView<Map<String, Object>> marketTablosu;
    private Label davetiyeKodLabel;
    private Label marketSayisiLabel;

    public PatronEkrani(Stage stage) {
        this.stage = stage;
    }

    public Scene olustur() {

        // ===== ÜST BAR =====
        Label baslik = new Label("🔐  PATRON PANELİ");
        baslik.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        baslik.setTextFill(Color.WHITE);

        Button geriBtn = new Button("◀  Geri");
        geriBtn.setPrefHeight(34);
        geriBtn.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        geriBtn.setStyle("-fx-background-color: #34495e; -fx-text-fill: white; " +
                "-fx-background-radius: 6; -fx-cursor: hand; -fx-padding: 0 16;");
        geriBtn.setOnAction(e -> {
            GirisEkrani giris = new GirisEkrani(stage);
            stage.setScene(giris.olustur());
            stage.setWidth(500);
            stage.setHeight(400);
            stage.centerOnScreen();
        });

        Region ustBosluk = new Region();
        HBox.setHgrow(ustBosluk, Priority.ALWAYS);

        HBox ustBar = new HBox(12, baslik, ustBosluk, geriBtn);
        ustBar.setAlignment(Pos.CENTER_LEFT);
        ustBar.setPadding(new Insets(10, 15, 10, 15));
        ustBar.setStyle("-fx-background-color: #1a252f;");

        // ===== GİRİŞ PANELİ (şifre doğrulanana kadar görünür) =====
        loginPanel = olusturLoginPanel();

        // ===== ANA PANEL (şifre doğrulandıktan sonra görünür) =====
        anaPanel = olusturAnaPanel();
        anaPanel.setVisible(false);
        anaPanel.setManaged(false);

        StackPane icerik = new StackPane(loginPanel, anaPanel);
        VBox.setVgrow(icerik, Priority.ALWAYS);

        VBox root = new VBox(0, ustBar, icerik);
        VBox.setVgrow(icerik, Priority.ALWAYS);

        return new Scene(root, 1100, 720);
    }

    // ===== GİRİŞ PANELİ =====
    private VBox olusturLoginPanel() {
        // Kilit ikonu
        Label kilit = new Label("🔐");
        kilit.setFont(Font.font(52));

        Label baslik = new Label("Patron Girişi");
        baslik.setFont(Font.font("Arial", FontWeight.BOLD, 22));
        baslik.setTextFill(Color.web("#1a252f"));

        Label aciklama = new Label("Bu panel sadece yetkili kullanıcılara açıktır.");
        aciklama.setFont(Font.font("Arial", 13));
        aciklama.setTextFill(Color.web("#7f8c8d"));

        PasswordField sifreField = new PasswordField();
        sifreField.setPromptText("Patron şifresi...");
        sifreField.setPrefHeight(46);
        sifreField.setPrefWidth(340);
        sifreField.setFont(Font.font("Arial", 15));
        sifreField.setStyle("-fx-border-color: #bdc3c7; -fx-border-radius: 8; " +
                "-fx-background-radius: 8; -fx-padding: 8;");

        Button dogrulaBtn = new Button("  Giriş Yap  ");
        dogrulaBtn.setPrefHeight(46);
        dogrulaBtn.setPrefWidth(340);
        dogrulaBtn.setFont(Font.font("Arial", FontWeight.BOLD, 15));
        dogrulaBtn.setStyle("-fx-background-color: #1a252f; -fx-text-fill: white; " +
                "-fx-background-radius: 8; -fx-cursor: hand;");

        Label hataMesaji = new Label("");
        hataMesaji.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        hataMesaji.setTextFill(Color.web("#e74c3c"));

        VBox kart = new VBox(16, kilit, baslik, aciklama, sifreField, dogrulaBtn, hataMesaji);
        kart.setAlignment(Pos.CENTER);
        kart.setPadding(new Insets(44, 52, 44, 52));
        kart.setMaxWidth(460);
        kart.setStyle("-fx-background-color: white; -fx-background-radius: 14; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.12), 20, 0, 0, 6);");

        VBox panel = new VBox(kart);
        panel.setAlignment(Pos.CENTER);
        panel.setStyle("-fx-background-color: #ecf0f1;");
        VBox.setVgrow(panel, Priority.ALWAYS);

        Runnable girisYap = () -> {
            String sifre = sifreField.getText().trim();
            if (sifre.isEmpty()) {
                hataMesaji.setText("Şifre boş olamaz!");
                return;
            }
            dogrulaBtn.setDisable(true);
            dogrulaBtn.setText("Kontrol ediliyor...");
            hataMesaji.setText("");

            new Thread(() -> {
                try {
                    Map<String, Object> yanit = ApiClient.post(
                            "/api/superadmin/dogrula", Map.of("sifresi", sifre));
                    Platform.runLater(() -> {
                        dogrulaBtn.setDisable(false);
                        dogrulaBtn.setText("  Giriş Yap  ");
                        if (Boolean.TRUE.equals(yanit.get("basarili"))) {
                            dogrulanmisSifre = sifre;
                            loginPanel.setVisible(false);
                            loginPanel.setManaged(false);
                            anaPanel.setVisible(true);
                            anaPanel.setManaged(true);
                            marketleriYukle();
                        } else {
                            hataMesaji.setText("❌  Hatalı şifre! Lütfen tekrar deneyin.");
                            sifreField.clear();
                            sifreField.requestFocus();
                        }
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        dogrulaBtn.setDisable(false);
                        dogrulaBtn.setText("  Giriş Yap  ");
                        hataMesaji.setText("❌  Bağlantı hatası: " + ex.getMessage());
                    });
                }
            }).start();
        };

        dogrulaBtn.setOnAction(e -> girisYap.run());
        sifreField.setOnAction(e -> girisYap.run());

        return panel;
    }

    // ===== ANA PANEL =====
    private VBox olusturAnaPanel() {

        // ===== SOL PANEL — DAVETİYE =====
        Label davetiyeBaslik = new Label("🎟  Davetiye Kodu");
        davetiyeBaslik.setFont(Font.font("Arial", FontWeight.BOLD, 15));
        davetiyeBaslik.setTextFill(Color.web("#2c3e50"));

        Label davetiyeAcik = new Label("Yeni market kaydı için\ntek kullanımlık davetiye üretin.");
        davetiyeAcik.setFont(Font.font("Arial", 12));
        davetiyeAcik.setTextFill(Color.web("#7f8c8d"));

        Button davetiyeUretBtn = new Button("➕  Yeni Davetiye Üret");
        davetiyeUretBtn.setPrefWidth(220);
        davetiyeUretBtn.setPrefHeight(42);
        davetiyeUretBtn.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        davetiyeUretBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; " +
                "-fx-background-radius: 8; -fx-cursor: hand;");

        davetiyeKodLabel = new Label("");
        davetiyeKodLabel.setFont(Font.font("Monospaced", FontWeight.BOLD, 16));
        davetiyeKodLabel.setTextFill(Color.web("#27ae60"));
        davetiyeKodLabel.setWrapText(true);
        davetiyeKodLabel.setStyle("-fx-background-color: #eafaf1; -fx-background-radius: 7; " +
                "-fx-padding: 10 14; -fx-border-color: #27ae6055; -fx-border-radius: 7;");
        davetiyeKodLabel.setVisible(false);
        davetiyeKodLabel.setMaxWidth(220);

        VBox davetiyeKutu = new VBox(12, davetiyeBaslik, davetiyeAcik, davetiyeUretBtn, davetiyeKodLabel);
        davetiyeKutu.setPadding(new Insets(20));
        davetiyeKutu.setStyle("-fx-background-color: white; -fx-background-radius: 10; " +
                "-fx-border-color: #dee2e6; -fx-border-radius: 10;");

        // ===== SOL PANEL — ÇIKIŞ =====
        Button cikisBtn = new Button("🔒  Kilitle");
        cikisBtn.setPrefWidth(220);
        cikisBtn.setPrefHeight(38);
        cikisBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; " +
                "-fx-background-radius: 8; -fx-cursor: hand; -fx-font-weight: bold;");
        cikisBtn.setOnAction(e -> {
            dogrulanmisSifre = null;
            anaPanel.setVisible(false);
            anaPanel.setManaged(false);
            loginPanel.setVisible(true);
            loginPanel.setManaged(true);
        });

        Region solBosluk = new Region();
        VBox.setVgrow(solBosluk, Priority.ALWAYS);

        VBox solPanel = new VBox(16, davetiyeKutu, solBosluk, cikisBtn);
        solPanel.setPadding(new Insets(20));
        solPanel.setPrefWidth(260);
        solPanel.setMinWidth(260);
        solPanel.setMaxWidth(260);
        solPanel.setStyle("-fx-background-color: #f4f6f7; " +
                "-fx-border-color: #d5d8dc; -fx-border-width: 0 1 0 0;");

        // ===== SAĞ PANEL — MARKET YÖNETİMİ =====

        // Başlık satırı
        Label marketBaslik = new Label("🏪  Market Yönetimi");
        marketBaslik.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        marketBaslik.setTextFill(Color.web("#1a252f"));

        marketSayisiLabel = new Label("");
        marketSayisiLabel.setFont(Font.font("Arial", 12));
        marketSayisiLabel.setTextFill(Color.web("#7f8c8d"));

        Button yenileBtn = new Button("🔄  Yenile");
        yenileBtn.setPrefHeight(34);
        yenileBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; " +
                "-fx-background-radius: 6; -fx-cursor: hand; -fx-font-weight: bold; -fx-padding: 0 16;");
        yenileBtn.setOnAction(e -> marketleriYukle());

        Region sagBosluk = new Region();
        HBox.setHgrow(sagBosluk, Priority.ALWAYS);

        HBox sagBaslikSatiri = new HBox(12, marketBaslik, marketSayisiLabel, sagBosluk, yenileBtn);
        sagBaslikSatiri.setAlignment(Pos.CENTER_LEFT);
        sagBaslikSatiri.setPadding(new Insets(16, 16, 10, 16));

        // ===== MARKET TABLOSU =====
        marketVerisi = FXCollections.observableArrayList();
        marketTablosu = new TableView<>(marketVerisi);
        marketTablosu.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        marketTablosu.setStyle("-fx-font-size: 13px;");
        marketTablosu.setFixedCellSize(42);
        marketTablosu.setPlaceholder(new Label("Market bulunamadı"));
        VBox.setVgrow(marketTablosu, Priority.ALWAYS);

        // Alternating row colors
        marketTablosu.setRowFactory(tv -> new TableRow<Map<String, Object>>() {
            @Override
            protected void updateItem(Map<String, Object> item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setStyle("");
                } else {
                    setStyle(getIndex() % 2 == 0
                            ? "-fx-background-color: #ffffff;"
                            : "-fx-background-color: #f8fafc;");
                }
            }
        });

        // # kolonu
        TableColumn<Map<String, Object>, Integer> siraKol = new TableColumn<>("#");
        siraKol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : String.valueOf(getIndex() + 1));
                setStyle("-fx-alignment: CENTER; -fx-text-fill: #95a5a6;");
            }
        });
        siraKol.setPrefWidth(45);
        siraKol.setMaxWidth(45);

        // Market Adı kolonu
        TableColumn<Map<String, Object>, String> adKol = new TableColumn<>("Market Adı");
        adKol.setCellValueFactory(d ->
                new javafx.beans.property.SimpleStringProperty(
                        d.getValue().getOrDefault("marketAdi", "—").toString()));
        adKol.setPrefWidth(220);

        // Lisans Bitiş kolonu
        TableColumn<Map<String, Object>, String> lisansKol = new TableColumn<>("Lisans Bitiş");
        lisansKol.setCellValueFactory(d -> {
            Object val = d.getValue().get("lisansBitisTarihi");
            return new javafx.beans.property.SimpleStringProperty(
                    val != null ? tarihFormat(val.toString()) : "—");
        });
        lisansKol.setPrefWidth(130);
        lisansKol.setStyle("-fx-alignment: CENTER;");

        // Kalan Gün kolonu
        TableColumn<Map<String, Object>, String> kalanKol = new TableColumn<>("Kalan");
        kalanKol.setCellValueFactory(d -> {
            Object val = d.getValue().get("lisansBitisTarihi");
            if (val == null) return new javafx.beans.property.SimpleStringProperty("—");
            try {
                LocalDate bitis = LocalDate.parse(val.toString());
                long gun = ChronoUnit.DAYS.between(LocalDate.now(), bitis);
                return new javafx.beans.property.SimpleStringProperty(
                        gun >= 0 ? gun + " gün" : Math.abs(gun) + " gün geçti");
            } catch (Exception e) {
                return new javafx.beans.property.SimpleStringProperty("—");
            }
        });
        kalanKol.setPrefWidth(110);
        kalanKol.setStyle("-fx-alignment: CENTER;");
        kalanKol.setCellFactory(col -> new TableCell<Map<String, Object>, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || "—".equals(item)) {
                    setText(null);
                    setStyle("-fx-alignment: CENTER;");
                } else if (item.contains("geçti")) {
                    setText(item);
                    setStyle("-fx-alignment: CENTER; -fx-text-fill: #e74c3c; -fx-font-weight: bold;");
                } else {
                    long gun = Long.parseLong(item.replace(" gün", ""));
                    String renk = gun <= 30 ? "#e67e22" : gun <= 90 ? "#f39c12" : "#27ae60";
                    setText(item);
                    setStyle("-fx-alignment: CENTER; -fx-text-fill: " + renk + "; -fx-font-weight: bold;");
                }
            }
        });

        // Durum kolonu
        TableColumn<Map<String, Object>, String> durumKol = new TableColumn<>("Durum");
        durumKol.setPrefWidth(110);
        durumKol.setStyle("-fx-alignment: CENTER;");
        durumKol.setCellValueFactory(d -> {
            Object val = d.getValue().get("lisansBitisTarihi");
            if (val == null) return new javafx.beans.property.SimpleStringProperty("Bilinmiyor");
            try {
                LocalDate bitis = LocalDate.parse(val.toString());
                return new javafx.beans.property.SimpleStringProperty(
                        bitis.isBefore(LocalDate.now()) ? "Süresi Doldu" : "Aktif");
            } catch (Exception e) {
                return new javafx.beans.property.SimpleStringProperty("—");
            }
        });
        durumKol.setCellFactory(col -> new TableCell<Map<String, Object>, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("-fx-alignment: CENTER;");
                } else if ("Aktif".equals(item)) {
                    setText("✓  Aktif");
                    setStyle("-fx-alignment: CENTER; -fx-text-fill: #27ae60; -fx-font-weight: bold;");
                } else {
                    setText("✕  " + item);
                    setStyle("-fx-alignment: CENTER; -fx-text-fill: #e74c3c; -fx-font-weight: bold;");
                }
            }
        });

        // Lisans Uzat butonu kolonu
        TableColumn<Map<String, Object>, Void> uzatKol = new TableColumn<>("İşlem");
        uzatKol.setPrefWidth(140);
        uzatKol.setCellFactory(col -> new TableCell<>() {
            final Button btn = new Button("📅  +1 Yıl Uzat");
            {
                btn.setStyle("-fx-background-color: #2980b9; -fx-text-fill: white; " +
                        "-fx-background-radius: 6; -fx-cursor: hand; -fx-font-size: 11px; " +
                        "-fx-padding: 4 10;");
                btn.setOnMouseEntered(e -> btn.setStyle(
                        "-fx-background-color: #1a6ea8; -fx-text-fill: white; " +
                        "-fx-background-radius: 6; -fx-cursor: hand; -fx-font-size: 11px; " +
                        "-fx-padding: 4 10;"));
                btn.setOnMouseExited(e -> btn.setStyle(
                        "-fx-background-color: #2980b9; -fx-text-fill: white; " +
                        "-fx-background-radius: 6; -fx-cursor: hand; -fx-font-size: 11px; " +
                        "-fx-padding: 4 10;"));
                btn.setOnAction(e -> {
                    Map<String, Object> market = getTableView().getItems().get(getIndex());
                    lisansUzat(market);
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });

        marketTablosu.getColumns().addAll(siraKol, adKol, lisansKol, kalanKol, durumKol, uzatKol);

        VBox sagPanel = new VBox(0, sagBaslikSatiri, marketTablosu);
        VBox.setVgrow(marketTablosu, Priority.ALWAYS);
        VBox.setVgrow(sagPanel, Priority.ALWAYS);

        // ===== ANA LAYOUT =====
        HBox govde = new HBox(0, solPanel, sagPanel);
        HBox.setHgrow(sagPanel, Priority.ALWAYS);
        VBox.setVgrow(govde, Priority.ALWAYS);

        VBox anaLayout = new VBox(0, govde);
        VBox.setVgrow(govde, Priority.ALWAYS);
        anaLayout.setStyle("-fx-background-color: #f4f6f7;");

        // ===== OLAYLAR =====
        davetiyeUretBtn.setOnAction(e -> davetiyeUret());

        return anaLayout;
    }

    // ===== MARKETLERİ YÜKLE =====
    private void marketleriYukle() {
        if (dogrulanmisSifre == null) return;
        marketSayisiLabel.setText("Yükleniyor...");

        new Thread(() -> {
            try {
                java.net.http.HttpRequest istek = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create("http://localhost:8080/api/superadmin/marketler"))
                        .header("X-Admin-Secret", dogrulanmisSifre)
                        .GET()
                        .build();

                java.net.http.HttpResponse<String> yanit = HTTP_CLIENT.send(
                        istek, java.net.http.HttpResponse.BodyHandlers.ofString());

                if (yanit.statusCode() == 200) {
                    List<Map<String, Object>> liste = MAPPER.readValue(
                            yanit.body(), new com.fasterxml.jackson.core.type.TypeReference<>() {});
                    Platform.runLater(() -> {
                        marketVerisi.clear();
                        marketVerisi.addAll(liste);
                        marketTablosu.refresh();
                        int aktif = (int) liste.stream().filter(m -> {
                            Object v = m.get("lisansBitisTarihi");
                            if (v == null) return false;
                            try { return !LocalDate.parse(v.toString()).isBefore(LocalDate.now()); }
                            catch (Exception e) { return false; }
                        }).count();
                        marketSayisiLabel.setText(liste.size() + " market  |  " +
                                aktif + " aktif  |  " + (liste.size() - aktif) + " süresi dolmuş");
                    });
                } else {
                    Platform.runLater(() -> bildir("❌ Marketler yüklenemedi! (HTTP " + yanit.statusCode() + ")", "#e74c3c"));
                }
            } catch (Exception ex) {
                Platform.runLater(() -> bildir("❌ Bağlantı hatası!", "#e74c3c"));
            }
        }).start();
    }

    // ===== LİSANS UZAT =====
    private void lisansUzat(Map<String, Object> market) {
        if (dogrulanmisSifre == null) return;

        Long marketId = Long.valueOf(market.getOrDefault("id", 0).toString());
        String marketAdi = market.getOrDefault("marketAdi", "?").toString();

        Alert onay = new Alert(Alert.AlertType.CONFIRMATION);
        onay.setTitle("Lisans Uzatma Onayı");
        onay.setHeaderText("\"" + marketAdi + "\" marketinin lisansı 1 yıl uzatılacak.");
        onay.setContentText("Bu işlem geri alınamaz. Devam etmek istiyor musunuz?");
        onay.getButtonTypes().setAll(
                new ButtonType("✓  Uzat", ButtonBar.ButtonData.OK_DONE),
                new ButtonType("İptal", ButtonBar.ButtonData.CANCEL_CLOSE));

        onay.showAndWait().ifPresent(result -> {
            if (result.getButtonData() != ButtonBar.ButtonData.OK_DONE) return;

            new Thread(() -> {
                try {
                    java.net.http.HttpRequest istek = java.net.http.HttpRequest.newBuilder()
                            .uri(java.net.URI.create(
                                    "http://localhost:8080/api/superadmin/lisans-uzat/" + marketId))
                            .header("X-Admin-Secret", dogrulanmisSifre)
                            .PUT(java.net.http.HttpRequest.BodyPublishers.noBody())
                            .build();

                    java.net.http.HttpResponse<String> yanit = HTTP_CLIENT.send(
                            istek, java.net.http.HttpResponse.BodyHandlers.ofString());

                    Platform.runLater(() -> {
                        if (yanit.statusCode() == 200) {
                            bildir("✅  " + marketAdi + " lisansı uzatıldı!", "#27ae60");
                            marketleriYukle(); // Tabloyu yenile
                        } else {
                            bildir("❌  Lisans uzatılamadı: " + yanit.body(), "#e74c3c");
                        }
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> bildir("❌  Bağlantı hatası!", "#e74c3c"));
                }
            }).start();
        });
    }

    // ===== DAVETİYE ÜRET =====
    private void davetiyeUret() {
        if (dogrulanmisSifre == null) return;

        new Thread(() -> {
            try {
                Map<String, Object> yanit = ApiClient.post(
                        "/api/superadmin/davetiye-uret",
                        Map.of("sifresi", dogrulanmisSifre));
                Platform.runLater(() -> {
                    if (yanit.containsKey("kod")) {
                        String kod = yanit.get("kod").toString();
                        davetiyeKodLabel.setText("🎟  " + kod);
                        davetiyeKodLabel.setVisible(true);
                        bildir("✅  Davetiye üretildi: " + kod, "#27ae60");
                    } else {
                        String hata = yanit.getOrDefault("error", "Hata!").toString();
                        bildir("❌  " + hata, "#e74c3c");
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> bildir("❌  Bağlantı hatası!", "#e74c3c"));
            }
        }).start();
    }

    // ===== YARDIMCI: Tarih formatla =====
    private String tarihFormat(String iso) {
        try {
            return LocalDate.parse(iso)
                    .format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        } catch (Exception e) {
            return iso;
        }
    }

    // ===== BİLDİRİM POPUP =====
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
            mesajLbl.setMaxWidth(340);

            Button kapatBtn = new Button("✕");
            kapatBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: white; " +
                    "-fx-cursor: hand; -fx-font-size: 14px; -fx-font-weight: bold; -fx-padding: 0 4;");
            kapatBtn.setOnAction(e -> popup.close());

            Region bosluk = new Region();
            HBox.setHgrow(bosluk, Priority.ALWAYS);

            HBox kutu = new HBox(8, mesajLbl, bosluk, kapatBtn);
            kutu.setAlignment(Pos.CENTER_LEFT);
            kutu.setPadding(new Insets(13, 16, 13, 18));
            kutu.setStyle("-fx-background-color: " + renk + "f0; " +
                    "-fx-background-radius: 10; " +
                    "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.4), 14, 0, 0, 5);");
            kutu.setMinWidth(280);

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
        });
    }
}
