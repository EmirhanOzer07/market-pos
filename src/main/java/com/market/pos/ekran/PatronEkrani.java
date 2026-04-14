package com.market.pos.ekran;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Patron (süper yönetici) paneli JavaFX ekranı.
 *
 * <p>Şifre doğrulaması sonrası market lisanslarını uzatma, davetiye kodu üretme
 * ve tüm marketleri listeleme işlevlerini sağlar. Ctrl+Shift+P kısayoluyla açılır.</p>
 */
public class PatronEkrani {

    /**
     * Dinamik port — ConfigManager.portBul() ile seçilen port System property olarak
     * yazılır; ApiClient ile aynı değeri kullanır. Hardcoded 8080 yerine.
     */
    private static final String BASE_URL =
            "http://localhost:" + System.getProperty("server.port", "8080");

    private static final java.net.http.HttpClient HTTP_CLIENT =
            java.net.http.HttpClient.newHttpClient();
    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper()
                    .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                    .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final Stage stage;
    private Stage aktifBildirim;

    /** Doğrulanan patron şifresi; her istekte tekrar sorulmaz. */
    private String dogrulanmisSifre = null;

    private VBox loginPanel;
    private VBox anaPanel;

    // Market sekmesi
    private ObservableList<Map<String, Object>> marketVerisi;
    private TableView<Map<String, Object>> marketTablosu;
    private Label marketSayisiLabel;
    private Label uyariLabel;

    // Davetiye geçmişi sekmesi
    private ObservableList<Map<String, Object>> davetiyeVerisi;
    private TableView<Map<String, Object>> davetiyeTablosu;

    // Sol panel davetiye alanı
    private Label davetiyeKodLabel;
    private Label davetiyeSonTarihLabel;
    private Button kopyalaBtn;

    public PatronEkrani(Stage stage) {
        this.stage = stage;
    }

    public Scene olustur() {
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

        loginPanel = olusturLoginPanel();
        anaPanel   = olusturAnaPanel();
        anaPanel.setVisible(false);
        anaPanel.setManaged(false);

        StackPane icerik = new StackPane(loginPanel, anaPanel);
        VBox.setVgrow(icerik, Priority.ALWAYS);

        VBox root = new VBox(0, ustBar, icerik);
        VBox.setVgrow(icerik, Priority.ALWAYS);

        return new Scene(root, 1100, 720);
    }

    // ===================================================================
    // GİRİŞ PANELI
    // ===================================================================

    private VBox olusturLoginPanel() {
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
                            davetiyeGecmisiniYukle();
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
            }, "patron-giris").start();
        };

        dogrulaBtn.setOnAction(e -> girisYap.run());
        sifreField.setOnAction(e -> girisYap.run());

        return panel;
    }

    // ===================================================================
    // ANA PANEL
    // ===================================================================

    private VBox olusturAnaPanel() {

        // ----- Sol panel — Davetiye kutusu -----
        Label davetiyeBaslik = new Label("🎟  Davetiye Kodu");
        davetiyeBaslik.setFont(Font.font("Arial", FontWeight.BOLD, 15));
        davetiyeBaslik.setTextFill(Color.web("#2c3e50"));

        Label davetiyeAcik = new Label("Yeni market kaydı için\ntek kullanımlık davetiye üretin.");
        davetiyeAcik.setFont(Font.font("Arial", 12));
        davetiyeAcik.setTextFill(Color.web("#7f8c8d"));

        Button davetiyeUretBtn = new Button("➕  Yeni Davetiye Üret");
        davetiyeUretBtn.setPrefWidth(224);
        davetiyeUretBtn.setPrefHeight(42);
        davetiyeUretBtn.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        davetiyeUretBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; " +
                "-fx-background-radius: 8; -fx-cursor: hand;");
        davetiyeUretBtn.setOnAction(e -> davetiyeUret());

        davetiyeKodLabel = new Label("");
        davetiyeKodLabel.setFont(Font.font("Monospaced", FontWeight.BOLD, 14));
        davetiyeKodLabel.setTextFill(Color.web("#27ae60"));
        davetiyeKodLabel.setWrapText(true);
        davetiyeKodLabel.setMaxWidth(Double.MAX_VALUE);
        davetiyeKodLabel.setStyle("-fx-background-color: #eafaf1; -fx-background-radius: 7; " +
                "-fx-padding: 8 12; -fx-border-color: #27ae6055; -fx-border-radius: 7;");
        davetiyeKodLabel.setVisible(false);
        davetiyeKodLabel.setManaged(false);

        kopyalaBtn = new Button("📋  Kopyala");
        kopyalaBtn.setPrefWidth(224);
        kopyalaBtn.setPrefHeight(32);
        kopyalaBtn.setFont(Font.font("Arial", 12));
        kopyalaBtn.setStyle("-fx-background-color: #ecf0f1; -fx-text-fill: #2c3e50; " +
                "-fx-background-radius: 6; -fx-cursor: hand; " +
                "-fx-border-color: #bdc3c7; -fx-border-radius: 6;");
        kopyalaBtn.setVisible(false);
        kopyalaBtn.setManaged(false);
        kopyalaBtn.setOnAction(e -> {
            String kod = davetiyeKodLabel.getText();
            if (!kod.isEmpty()) {
                ClipboardContent content = new ClipboardContent();
                content.putString(kod);
                Clipboard.getSystemClipboard().setContent(content);
                kopyalaBtn.setText("✓  Kopyalandı!");
                new Thread(() -> {
                    try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                    Platform.runLater(() -> kopyalaBtn.setText("📋  Kopyala"));
                }, "kopyala-reset").start();
            }
        });

        davetiyeSonTarihLabel = new Label("");
        davetiyeSonTarihLabel.setFont(Font.font("Arial", 11));
        davetiyeSonTarihLabel.setTextFill(Color.web("#7f8c8d"));
        davetiyeSonTarihLabel.setVisible(false);
        davetiyeSonTarihLabel.setManaged(false);

        VBox davetiyeKutu = new VBox(10, davetiyeBaslik, davetiyeAcik,
                davetiyeUretBtn, davetiyeKodLabel, kopyalaBtn, davetiyeSonTarihLabel);
        davetiyeKutu.setPadding(new Insets(18));
        davetiyeKutu.setStyle("-fx-background-color: white; -fx-background-radius: 10; " +
                "-fx-border-color: #dee2e6; -fx-border-radius: 10;");

        Button cikisBtn = new Button("🔒  Kilitle");
        cikisBtn.setPrefWidth(224);
        cikisBtn.setPrefHeight(38);
        cikisBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; " +
                "-fx-background-radius: 8; -fx-cursor: hand; -fx-font-weight: bold;");
        cikisBtn.setOnAction(e -> kilitle());

        Region solBosluk = new Region();
        VBox.setVgrow(solBosluk, Priority.ALWAYS);

        VBox solPanel = new VBox(16, davetiyeKutu, solBosluk, cikisBtn);
        solPanel.setPadding(new Insets(18));
        solPanel.setPrefWidth(268);
        solPanel.setMinWidth(268);
        solPanel.setMaxWidth(268);
        solPanel.setStyle("-fx-background-color: #f4f6f7; " +
                "-fx-border-color: #d5d8dc; -fx-border-width: 0 1 0 0;");

        // ----- Sağ panel — TabPane -----
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab marketTab   = new Tab("🏪  Market Yönetimi",  olusturMarketTabIcerigi());
        Tab davetiyeTab = new Tab("🎟  Davetiye Geçmişi", olusturDavetiyeTabIcerigi());
        tabPane.getTabs().addAll(marketTab, davetiyeTab);

        // Davetiye sekmesine geçince otomatik yenile
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, eski, yeni) -> {
            if (yeni == davetiyeTab) davetiyeGecmisiniYukle();
        });

        HBox.setHgrow(tabPane, Priority.ALWAYS);

        HBox govde = new HBox(0, solPanel, tabPane);
        HBox.setHgrow(tabPane, Priority.ALWAYS);
        VBox.setVgrow(govde, Priority.ALWAYS);

        VBox anaLayout = new VBox(0, govde);
        VBox.setVgrow(govde, Priority.ALWAYS);
        anaLayout.setStyle("-fx-background-color: #f4f6f7;");

        return anaLayout;
    }

    private void kilitle() {
        dogrulanmisSifre = null;
        davetiyeKodLabel.setText("");
        davetiyeKodLabel.setVisible(false);
        davetiyeKodLabel.setManaged(false);
        kopyalaBtn.setVisible(false);
        kopyalaBtn.setManaged(false);
        davetiyeSonTarihLabel.setVisible(false);
        davetiyeSonTarihLabel.setManaged(false);
        anaPanel.setVisible(false);
        anaPanel.setManaged(false);
        loginPanel.setVisible(true);
        loginPanel.setManaged(true);
    }

    // ===================================================================
    // MARKET YÖNETİMİ SEKMESİ
    // ===================================================================

    private VBox olusturMarketTabIcerigi() {
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

        HBox baslikSatiri = new HBox(12, marketBaslik, marketSayisiLabel, sagBosluk, yenileBtn);
        baslikSatiri.setAlignment(Pos.CENTER_LEFT);
        baslikSatiri.setPadding(new Insets(14, 16, 8, 16));

        // 30 gün uyarı bandı — varsayılan gizli
        uyariLabel = new Label();
        uyariLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        uyariLabel.setMaxWidth(Double.MAX_VALUE);
        uyariLabel.setStyle("-fx-background-color: #fef9e7; -fx-text-fill: #e67e22; " +
                "-fx-padding: 7 16; -fx-border-color: #f39c12; -fx-border-width: 0 0 1 0;");
        uyariLabel.setVisible(false);
        uyariLabel.setManaged(false);

        marketVerisi  = FXCollections.observableArrayList();
        marketTablosu = new TableView<>(marketVerisi);
        marketTablosu.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        marketTablosu.setStyle("-fx-font-size: 13px;");
        marketTablosu.setFixedCellSize(42);
        marketTablosu.setPlaceholder(new Label("Market bulunamadı"));
        VBox.setVgrow(marketTablosu, Priority.ALWAYS);

        marketTablosu.setRowFactory(tv -> new TableRow<Map<String, Object>>() {
            @Override
            protected void updateItem(Map<String, Object> item, boolean empty) {
                super.updateItem(item, empty);
                setStyle(empty || item == null ? ""
                        : getIndex() % 2 == 0 ? "-fx-background-color: #ffffff;"
                        : "-fx-background-color: #f8fafc;");
            }
        });

        // Sıra #
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

        // Market Adı
        TableColumn<Map<String, Object>, String> adKol = new TableColumn<>("Market Adı");
        adKol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                d.getValue().getOrDefault("marketAdi", "—").toString()));
        adKol.setPrefWidth(200);

        // Lisans Bitiş
        TableColumn<Map<String, Object>, String> lisansKol = new TableColumn<>("Lisans Bitiş");
        lisansKol.setCellValueFactory(d -> {
            Object val = d.getValue().get("lisansBitisTarihi");
            return new javafx.beans.property.SimpleStringProperty(
                    val != null ? tarihFormat(val.toString()) : "—");
        });
        lisansKol.setPrefWidth(130);
        lisansKol.setStyle("-fx-alignment: CENTER;");

        // Kalan gün — renkli
        TableColumn<Map<String, Object>, String> kalanKol = new TableColumn<>("Kalan");
        kalanKol.setPrefWidth(110);
        kalanKol.setStyle("-fx-alignment: CENTER;");
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
        kalanKol.setCellFactory(col -> new TableCell<Map<String, Object>, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || "—".equals(item)) {
                    setText(null); setStyle("-fx-alignment: CENTER;"); return;
                }
                if (item.contains("geçti")) {
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

        // Durum
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
                if (empty || item == null) { setText(null); setStyle("-fx-alignment: CENTER;"); return; }
                if ("Aktif".equals(item)) {
                    setText("✓  Aktif");
                    setStyle("-fx-alignment: CENTER; -fx-text-fill: #27ae60; -fx-font-weight: bold;");
                } else {
                    setText("✕  " + item);
                    setStyle("-fx-alignment: CENTER; -fx-text-fill: #e74c3c; -fx-font-weight: bold;");
                }
            }
        });

        // İşlem — özel süre dialog'u
        TableColumn<Map<String, Object>, Void> uzatKol = new TableColumn<>("İşlem");
        uzatKol.setPrefWidth(160);
        uzatKol.setCellFactory(col -> new TableCell<>() {
            final Button btn = new Button("📅  Süre Uzat...");
            {
                final String normal = "-fx-background-color: #2980b9; -fx-text-fill: white; " +
                        "-fx-background-radius: 6; -fx-cursor: hand; -fx-font-size: 11px; -fx-padding: 4 10;";
                final String hover  = "-fx-background-color: #1a6ea8; -fx-text-fill: white; " +
                        "-fx-background-radius: 6; -fx-cursor: hand; -fx-font-size: 11px; -fx-padding: 4 10;";
                btn.setStyle(normal);
                btn.setOnMouseEntered(e -> btn.setStyle(hover));
                btn.setOnMouseExited(e  -> btn.setStyle(normal));
                btn.setOnAction(e -> {
                    Map<String, Object> market = getTableView().getItems().get(getIndex());
                    lisansUzatDialogGoster(market);
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });

        marketTablosu.getColumns().addAll(siraKol, adKol, lisansKol, kalanKol, durumKol, uzatKol);

        VBox icerik = new VBox(0, baslikSatiri, uyariLabel, marketTablosu);
        VBox.setVgrow(marketTablosu, Priority.ALWAYS);
        VBox.setVgrow(icerik, Priority.ALWAYS);
        icerik.setStyle("-fx-background-color: white;");
        return icerik;
    }

    // ===================================================================
    // DAVETİYE GEÇMİŞİ SEKMESİ
    // ===================================================================

    private VBox olusturDavetiyeTabIcerigi() {
        Label baslik = new Label("🎟  Tüm Davetiye Kodları");
        baslik.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        baslik.setTextFill(Color.web("#1a252f"));

        Button yenileBtn = new Button("🔄  Yenile");
        yenileBtn.setPrefHeight(34);
        yenileBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; " +
                "-fx-background-radius: 6; -fx-cursor: hand; -fx-font-weight: bold; -fx-padding: 0 16;");
        yenileBtn.setOnAction(e -> davetiyeGecmisiniYukle());

        Region bosluk = new Region();
        HBox.setHgrow(bosluk, Priority.ALWAYS);

        HBox baslikSatiri = new HBox(12, baslik, bosluk, yenileBtn);
        baslikSatiri.setAlignment(Pos.CENTER_LEFT);
        baslikSatiri.setPadding(new Insets(14, 16, 8, 16));

        davetiyeVerisi  = FXCollections.observableArrayList();
        davetiyeTablosu = new TableView<>(davetiyeVerisi);
        davetiyeTablosu.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        davetiyeTablosu.setStyle("-fx-font-size: 13px;");
        davetiyeTablosu.setFixedCellSize(42);
        davetiyeTablosu.setPlaceholder(new Label("Davetiye kodu bulunamadı"));
        VBox.setVgrow(davetiyeTablosu, Priority.ALWAYS);

        davetiyeTablosu.setRowFactory(tv -> new TableRow<Map<String, Object>>() {
            @Override
            protected void updateItem(Map<String, Object> item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setStyle(""); return; }
                boolean kullanildi = Boolean.TRUE.equals(item.get("kullanildiMi"));
                if (kullanildi) {
                    setStyle(getIndex() % 2 == 0
                            ? "-fx-background-color: #f8f8f8; -fx-opacity: 0.75;"
                            : "-fx-background-color: #f2f2f2; -fx-opacity: 0.75;");
                } else {
                    setStyle(getIndex() % 2 == 0
                            ? "-fx-background-color: #ffffff;"
                            : "-fx-background-color: #f8fafc;");
                }
            }
        });

        // Kod
        TableColumn<Map<String, Object>, String> kodKol = new TableColumn<>("Kod");
        kodKol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                d.getValue().getOrDefault("kod", "—").toString()));
        kodKol.setStyle("-fx-font-family: Monospaced;");
        kodKol.setPrefWidth(180);

        // Son Kullanma
        TableColumn<Map<String, Object>, String> sonTarihKol = new TableColumn<>("Son Kullanma");
        sonTarihKol.setCellValueFactory(d -> {
            Object val = d.getValue().get("sonKullanmaTarihi");
            return new javafx.beans.property.SimpleStringProperty(
                    val != null ? tarihFormat(val.toString()) : "Süresiz");
        });
        sonTarihKol.setPrefWidth(130);
        sonTarihKol.setStyle("-fx-alignment: CENTER;");

        // Durum
        TableColumn<Map<String, Object>, String> durumKol = new TableColumn<>("Durum");
        durumKol.setPrefWidth(150);
        durumKol.setStyle("-fx-alignment: CENTER;");
        durumKol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                d.getValue().getOrDefault("durum", "—").toString()));
        durumKol.setCellFactory(col -> new TableCell<Map<String, Object>, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle("-fx-alignment: CENTER;"); return; }
                switch (item) {
                    case "AKTIF"        -> { setText("✓  Aktif");        setStyle("-fx-alignment: CENTER; -fx-text-fill: #27ae60; -fx-font-weight: bold;"); }
                    case "KULLANILDI"   -> { setText("✔  Kullanıldı");   setStyle("-fx-alignment: CENTER; -fx-text-fill: #7f8c8d;"); }
                    case "SURESI_DOLDU" -> { setText("✕  Süresi Doldu"); setStyle("-fx-alignment: CENTER; -fx-text-fill: #e74c3c; -fx-font-weight: bold;"); }
                    default             -> { setText(item);               setStyle("-fx-alignment: CENTER;"); }
                }
            }
        });

        // İşlem — yalnızca AKTİF kodlarda İptal Et butonu görünür
        TableColumn<Map<String, Object>, Void> islemKol = new TableColumn<>("İşlem");
        islemKol.setPrefWidth(130);
        islemKol.setCellFactory(col -> new TableCell<>() {
            final Button iptalBtn = new Button("✕  İptal Et");
            {
                iptalBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; " +
                        "-fx-background-radius: 6; -fx-cursor: hand; -fx-font-size: 11px; -fx-padding: 4 10;");
                iptalBtn.setOnAction(e -> {
                    if (getIndex() < getTableView().getItems().size()) {
                        davetiyeIptalEt(getTableView().getItems().get(getIndex()));
                    }
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null); return;
                }
                Map<String, Object> row = getTableView().getItems().get(getIndex());
                String durum = row == null ? "" : row.getOrDefault("durum", "").toString();
                setGraphic("AKTIF".equals(durum) ? iptalBtn : null);
            }
        });

        davetiyeTablosu.getColumns().addAll(kodKol, sonTarihKol, durumKol, islemKol);

        VBox icerik = new VBox(0, baslikSatiri, davetiyeTablosu);
        VBox.setVgrow(davetiyeTablosu, Priority.ALWAYS);
        VBox.setVgrow(icerik, Priority.ALWAYS);
        icerik.setStyle("-fx-background-color: white;");
        return icerik;
    }

    // ===================================================================
    // İŞLEM METOTLARİ
    // ===================================================================

    private void marketleriYukle() {
        if (dogrulanmisSifre == null) return;
        marketSayisiLabel.setText("Yükleniyor...");
        uyariLabel.setVisible(false);
        uyariLabel.setManaged(false);

        new Thread(() -> {
            try {
                java.net.http.HttpRequest istek = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create(BASE_URL + "/api/superadmin/marketler"))
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

                        long aktif = liste.stream().filter(m -> {
                            Object v = m.get("lisansBitisTarihi");
                            if (v == null) return false;
                            try { return !LocalDate.parse(v.toString()).isBefore(LocalDate.now()); }
                            catch (Exception ignored) { return false; }
                        }).count();
                        marketSayisiLabel.setText(liste.size() + " market  |  " +
                                aktif + " aktif  |  " + (liste.size() - aktif) + " süresi dolmuş");

                        // 30 gün içinde bitecek aktif lisans uyarısı
                        long yaklasan = liste.stream().filter(m -> {
                            Object v = m.get("lisansBitisTarihi");
                            if (v == null) return false;
                            try {
                                LocalDate bitis = LocalDate.parse(v.toString());
                                long gun = ChronoUnit.DAYS.between(LocalDate.now(), bitis);
                                return gun >= 0 && gun <= 30;
                            } catch (Exception ignored) { return false; }
                        }).count();

                        if (yaklasan > 0) {
                            uyariLabel.setText("⚠  " + yaklasan +
                                    " marketin lisansı 30 gün içinde bitiyor!");
                            uyariLabel.setVisible(true);
                            uyariLabel.setManaged(true);
                        }
                    });
                } else {
                    Platform.runLater(() ->
                            bildir("❌ Marketler yüklenemedi! (HTTP " + yanit.statusCode() + ")", "#e74c3c"));
                }
            } catch (Exception ex) {
                Platform.runLater(() -> bildir("❌ Bağlantı hatası!", "#e74c3c"));
            }
        }, "patron-market-yukle").start();
    }

    /** Özel süre seçici dialog — +1 Yıl sabit yerine kullanıcı seçer. */
    private void lisansUzatDialogGoster(Map<String, Object> market) {
        if (dogrulanmisSifre == null) return;
        String marketAdi = market.getOrDefault("marketAdi", "?").toString();

        ComboBox<String> sureCB = new ComboBox<>();
        sureCB.getItems().addAll("3 Ay", "6 Ay", "1 Yıl", "2 Yıl");
        sureCB.setValue("1 Yıl");
        sureCB.setPrefWidth(200);

        VBox dialogIcerik = new VBox(10, new Label("Uzatılacak süreyi seçin:"), sureCB);
        dialogIcerik.setPadding(new Insets(10, 0, 0, 0));

        Dialog<Integer> dialog = new Dialog<>();
        dialog.initOwner(stage);
        dialog.setTitle("Lisans Uzatma");
        dialog.setHeaderText("\"" + marketAdi + "\" için süre uzatma");
        dialog.getDialogPane().setContent(dialogIcerik);
        dialog.getDialogPane().getButtonTypes().addAll(
                new ButtonType("✓  Uzat", ButtonBar.ButtonData.OK_DONE),
                ButtonType.CANCEL);

        dialog.setResultConverter(bt -> {
            if (bt.getButtonData() != ButtonBar.ButtonData.OK_DONE) return null;
            return switch (sureCB.getValue()) {
                case "3 Ay"  -> 3;
                case "6 Ay"  -> 6;
                case "2 Yıl" -> 24;
                default      -> 12; // "1 Yıl"
            };
        });

        dialog.showAndWait().ifPresent(ay -> {
            if (ay == null) return;
            Long marketId = Long.valueOf(market.getOrDefault("id", 0).toString());
            lisansUzat(marketId, marketAdi, ay);
        });
    }

    private void lisansUzat(Long marketId, String marketAdi, int ay) {
        new Thread(() -> {
            try {
                java.net.http.HttpRequest istek = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create(
                                BASE_URL + "/api/superadmin/lisans-uzat/" + marketId + "?ay=" + ay))
                        .header("X-Admin-Secret", dogrulanmisSifre)
                        .PUT(java.net.http.HttpRequest.BodyPublishers.noBody())
                        .build();

                java.net.http.HttpResponse<String> yanit = HTTP_CLIENT.send(
                        istek, java.net.http.HttpResponse.BodyHandlers.ofString());

                Platform.runLater(() -> {
                    if (yanit.statusCode() == 200) {
                        bildir("✅  " + marketAdi + " lisansı " + ay + " ay uzatıldı!", "#27ae60");
                        marketleriYukle();
                    } else {
                        bildir("❌  Lisans uzatılamadı: " + yanit.body(), "#e74c3c");
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> bildir("❌  Bağlantı hatası!", "#e74c3c"));
            }
        }, "patron-lisans-uzat").start();
    }

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
                        LocalDate sonTarih = LocalDate.now().plusDays(30);

                        davetiyeKodLabel.setText(kod);
                        davetiyeKodLabel.setVisible(true);
                        davetiyeKodLabel.setManaged(true);

                        kopyalaBtn.setText("📋  Kopyala");
                        kopyalaBtn.setVisible(true);
                        kopyalaBtn.setManaged(true);

                        davetiyeSonTarihLabel.setText(
                                "📅 Son kullanma: " + tarihFormat(sonTarih.toString()) + " (30 gün)");
                        davetiyeSonTarihLabel.setVisible(true);
                        davetiyeSonTarihLabel.setManaged(true);

                        bildir("✅  Davetiye üretildi: " + kod, "#27ae60");
                    } else {
                        String hata = yanit.getOrDefault("error", "Hata!").toString();
                        bildir("❌  " + hata, "#e74c3c");
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> bildir("❌  Bağlantı hatası!", "#e74c3c"));
            }
        }, "patron-davetiye-uret").start();
    }

    private void davetiyeGecmisiniYukle() {
        if (dogrulanmisSifre == null || davetiyeVerisi == null) return;

        new Thread(() -> {
            try {
                java.net.http.HttpRequest istek = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create(BASE_URL + "/api/superadmin/davetiyeler"))
                        .header("X-Admin-Secret", dogrulanmisSifre)
                        .GET()
                        .build();

                java.net.http.HttpResponse<String> yanit = HTTP_CLIENT.send(
                        istek, java.net.http.HttpResponse.BodyHandlers.ofString());

                if (yanit.statusCode() == 200) {
                    List<Map<String, Object>> liste = MAPPER.readValue(
                            yanit.body(), new com.fasterxml.jackson.core.type.TypeReference<>() {});
                    Platform.runLater(() -> {
                        davetiyeVerisi.clear();
                        davetiyeVerisi.addAll(liste);
                        if (davetiyeTablosu != null) davetiyeTablosu.refresh();
                    });
                } else {
                    Platform.runLater(() ->
                            bildir("❌ Davetiye geçmişi yüklenemedi! (HTTP " + yanit.statusCode() + ")", "#e74c3c"));
                }
            } catch (Exception ex) {
                Platform.runLater(() -> bildir("❌ Bağlantı hatası!", "#e74c3c"));
            }
        }, "patron-davetiye-yukle").start();
    }

    private void davetiyeIptalEt(Map<String, Object> davetiye) {
        if (dogrulanmisSifre == null) return;
        String kod = davetiye.getOrDefault("kod", "").toString();
        if (kod.isEmpty()) return;

        Alert onay = new Alert(Alert.AlertType.CONFIRMATION);
        onay.initOwner(stage);
        onay.setTitle("Davetiye İptali");
        onay.setHeaderText("\"" + kod + "\" kodu iptal edilecek.");
        onay.setContentText("Bu işlem geri alınamaz. Devam etmek istiyor musunuz?");
        onay.getButtonTypes().setAll(
                new ButtonType("✕  İptal Et", ButtonBar.ButtonData.OK_DONE),
                new ButtonType("Vazgeç", ButtonBar.ButtonData.CANCEL_CLOSE));

        onay.showAndWait().ifPresent(result -> {
            if (result.getButtonData() != ButtonBar.ButtonData.OK_DONE) return;

            new Thread(() -> {
                try {
                    java.net.http.HttpRequest istek = java.net.http.HttpRequest.newBuilder()
                            .uri(java.net.URI.create(BASE_URL + "/api/superadmin/davetiye/" + kod))
                            .header("X-Admin-Secret", dogrulanmisSifre)
                            .DELETE()
                            .build();

                    java.net.http.HttpResponse<String> yanit = HTTP_CLIENT.send(
                            istek, java.net.http.HttpResponse.BodyHandlers.ofString());

                    Platform.runLater(() -> {
                        if (yanit.statusCode() == 200) {
                            bildir("✅  " + kod + " iptal edildi.", "#27ae60");
                            davetiyeGecmisiniYukle();
                        } else {
                            bildir("❌  İptal edilemedi!", "#e74c3c");
                        }
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> bildir("❌  Bağlantı hatası!", "#e74c3c"));
                }
            }, "patron-davetiye-iptal").start();
        });
    }

    // ===================================================================
    // YARDIMCI METOTLARİ
    // ===================================================================

    private String tarihFormat(String iso) {
        try {
            return LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        } catch (Exception e) {
            return iso;
        }
    }

    private void bildir(String mesaj, String renk) {
        Platform.runLater(() -> {
            if (aktifBildirim != null && aktifBildirim.isShowing()) aktifBildirim.close();

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
