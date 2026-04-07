package com.market.pos.ekran;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Base64;
import java.util.Map;

public class GirisEkrani {

    private static final Path GIRIS_DOSYASI = Paths.get(
            System.getProperty("user.home"), "AppData", "Local", "MarketPOS", "giris.dat");

    private final Stage stage;
    private TextField kullaniciAdiField;
    private PasswordField sifreField;
    private Label hataMesaji;
    private Button girisBtn;

    public GirisEkrani(Stage stage) {
        this.stage = stage;
    }

    public Scene olustur() {

        // ===== BAŞLIK =====
        Label baslik = new Label("🏪 MARKET POS SİSTEMİ");
        baslik.setFont(Font.font("Arial", FontWeight.BOLD, 22));
        baslik.setTextFill(Color.web("#2c3e50"));

        Label altBaslik = new Label("Sisteme giriş yapın");
        altBaslik.setFont(Font.font("Arial", 13));
        altBaslik.setTextFill(Color.web("#7f8c8d"));

        VBox baslikKutu = new VBox(5, baslik, altBaslik);
        baslikKutu.setAlignment(Pos.CENTER);
        baslikKutu.setPadding(new Insets(0, 0, 20, 0));

        // ===== KULLANICI ADI =====
        Label kAdiLabel = new Label("Kullanıcı Adı");
        kAdiLabel.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        kAdiLabel.setTextFill(Color.web("#2c3e50"));

        kullaniciAdiField = new TextField();
        kullaniciAdiField.setPromptText("Kullanıcı adınızı girin...");
        kullaniciAdiField.setPrefHeight(40);
        kullaniciAdiField.setFont(Font.font("Arial", 14));
        kullaniciAdiField.setStyle(
                "-fx-border-color: #bdc3c7; -fx-border-radius: 6; " +
                        "-fx-background-radius: 6; -fx-padding: 8;");

        // ===== ŞİFRE =====
        Label sifreLabel = new Label("Şifre");
        sifreLabel.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        sifreLabel.setTextFill(Color.web("#2c3e50"));

        sifreField = new PasswordField();
        sifreField.setPromptText("Şifrenizi girin...");
        sifreField.setPrefHeight(40);
        sifreField.setFont(Font.font("Arial", 14));
        sifreField.setStyle(
                "-fx-border-color: #bdc3c7; -fx-border-radius: 6; " +
                        "-fx-background-radius: 6; -fx-padding: 8;");

        // ===== GİRİŞ BUTONU =====
        girisBtn = new Button("GİRİŞ YAP");
        girisBtn.setPrefWidth(Double.MAX_VALUE);
        girisBtn.setPrefHeight(45);
        girisBtn.setFont(Font.font("Arial", FontWeight.BOLD, 15));
        girisBtn.setStyle(
                "-fx-background-color: #27ae60; -fx-text-fill: white; " +
                        "-fx-background-radius: 6; -fx-cursor: hand;");
        girisBtn.setOnMouseEntered(e ->
                girisBtn.setStyle(
                        "-fx-background-color: #219a52; -fx-text-fill: white; " +
                                "-fx-background-radius: 6; -fx-cursor: hand;"));
        girisBtn.setOnMouseExited(e ->
                girisBtn.setStyle(
                        "-fx-background-color: #27ae60; -fx-text-fill: white; " +
                                "-fx-background-radius: 6; -fx-cursor: hand;"));

        // ===== KAYDET LİNKİ =====
        Hyperlink kayitliSilLink = new Hyperlink("Kayıtlı giriş bilgilerini temizle");
        kayitliSilLink.setFont(Font.font("Arial", 11));
        kayitliSilLink.setTextFill(Color.web("#95a5a6"));
        kayitliSilLink.setStyle("-fx-underline: true; -fx-cursor: hand;");
        kayitliSilLink.setVisible(false);
        kayitliSilLink.setOnAction(e -> {
            girisKaydetmeyiSil();
            kullaniciAdiField.clear();
            sifreField.clear();
            kayitliSilLink.setVisible(false);
            hataMesaji.setText("Kayıtlı giriş bilgileri silindi.");
            hataMesaji.setTextFill(Color.web("#27ae60"));
        });

        // ===== KAYIT OL BUTONU =====
        Button kayitOlBtn = new Button("Davetiye kodum var — Kayıt Ol");
        kayitOlBtn.setPrefWidth(Double.MAX_VALUE);
        kayitOlBtn.setPrefHeight(38);
        kayitOlBtn.setFont(Font.font("Arial", 13));
        kayitOlBtn.setStyle(
                "-fx-background-color: transparent; -fx-text-fill: #2980b9; " +
                        "-fx-cursor: hand; -fx-underline: true; " +
                        "-fx-border-color: #2980b9; -fx-border-radius: 6; " +
                        "-fx-background-radius: 6;");
        kayitOlBtn.setOnMouseEntered(e ->
                kayitOlBtn.setStyle(
                        "-fx-background-color: #eaf4fb; -fx-text-fill: #2980b9; " +
                                "-fx-cursor: hand; -fx-underline: true; " +
                                "-fx-border-color: #2980b9; -fx-border-radius: 6; " +
                                "-fx-background-radius: 6;"));
        kayitOlBtn.setOnMouseExited(e ->
                kayitOlBtn.setStyle(
                        "-fx-background-color: transparent; -fx-text-fill: #2980b9; " +
                                "-fx-cursor: hand; -fx-underline: true; " +
                                "-fx-border-color: #2980b9; -fx-border-radius: 6; " +
                                "-fx-background-radius: 6;"));
        kayitOlBtn.setOnAction(e -> kayitEkraniAc());

        // ===== HATA MESAJI =====
        hataMesaji = new Label("");
        hataMesaji.setFont(Font.font("Arial", 13));
        hataMesaji.setTextFill(Color.web("#e74c3c"));
        hataMesaji.setWrapText(true);

        // ===== FORM LAYOUT =====
        VBox form = new VBox(10,
                baslikKutu,
                kAdiLabel, kullaniciAdiField,
                sifreLabel, sifreField,
                girisBtn,
                kayitliSilLink,
                kayitOlBtn,
                hataMesaji
        );
        form.setPadding(new Insets(40));
        form.setMaxWidth(380);
        form.setStyle(
                "-fx-background-color: white; -fx-background-radius: 12; " +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 20, 0, 0, 4);");

        // ===== ANA LAYOUT =====
        StackPane anaLayout = new StackPane(form);
        anaLayout.setStyle("-fx-background-color: #ecf0f1;");
        anaLayout.setPadding(new Insets(60));

        // ===== OLAYLAR =====
        girisBtn.setOnAction(e -> girisYap());
        sifreField.setOnAction(e -> girisYap()); // Enter ile giriş
        kullaniciAdiField.setOnAction(e -> sifreField.requestFocus());

        Scene scene = new Scene(anaLayout, 500, 420);

        // ✅ Gizli patron paneli kısayolu — Ctrl+Shift+P
        scene.setOnKeyPressed(e -> {
            if (e.isControlDown() && e.isShiftDown() &&
                    e.getCode() == javafx.scene.input.KeyCode.P) {
                PatronEkrani patron = new PatronEkrani(stage);
                stage.setScene(patron.olustur());
                stage.setWidth(700);
                stage.setHeight(700);
                stage.centerOnScreen();
            }
        });

        // ===== KAYITLI GİRİŞ — Sadece doldur, otomatik giriş yapma =====
        // Kullanıcı formu dolu görür, isterse Enter'a basar veya değiştirir
        String[] kayitli = girisKaydet_oku();
        if (kayitli != null) {
            kullaniciAdiField.setText(kayitli[0]);
            sifreField.setText(kayitli[1]);
            kayitliSilLink.setVisible(true);
            // Şifre alanına focus — kullanıcı Enter'a basınca giriş yapar
            Platform.runLater(sifreField::requestFocus);
        }

        // ===== OTOMATİK GÜNCELLEME KONTROLÜ =====
        // Arka planda sessizce GitHub'ı kontrol et — UI donmasın
        new Thread(() -> {
            GuncellemeService.GuncellemeBilgisi bilgi = GuncellemeService.guncellemeVarMi();
            if (bilgi != null) {
                Platform.runLater(() -> guncellemeDiyaloguGoster(bilgi));
            }
        }, "guncelleme-kontrol").start();

        return scene;
    }

    // ===== GÜNCELLEME DİYALOGU =====
    private void guncellemeDiyaloguGoster(GuncellemeService.GuncellemeBilgisi bilgi) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(stage);
        alert.setTitle("Güncelleme Mevcut");
        alert.setHeaderText("Yeni sürüm: v" + bilgi.yeniSurum()
                + "   (Mevcut: v" + GuncellemeService.MEVCUT_SURUM + ")");

        String icerik = "Yeni bir Market POS sürümü yayınlandı.";
        if (!bilgi.surumNotu().isBlank()) {
            String not = bilgi.surumNotu();
            if (not.length() > 300) not = not.substring(0, 300) + "...";
            icerik += "\n\nYenilikler:\n" + not;
        }
        alert.setContentText(icerik);

        ButtonType simdiGuncelle = new ButtonType("Şimdi Güncelle");
        ButtonType sonra         = new ButtonType("Sonra", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(simdiGuncelle, sonra);

        alert.showAndWait().ifPresent(secim -> {
            if (secim == simdiGuncelle) {
                guncellemeBaslat(bilgi.indirmeUrl());
            }
        });
    }

    private void guncellemeBaslat(String indirmeUrl) {
        // İlerleme dialogu
        ProgressBar progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(300);

        Label durumLabel = new Label("İndiriliyor...");
        durumLabel.setFont(Font.font("Arial", 13));

        VBox icerik = new VBox(12, durumLabel, progressBar);
        icerik.setAlignment(Pos.CENTER);
        icerik.setPadding(new Insets(20));

        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(stage);
        dialog.setTitle("Güncelleniyor");
        dialog.setHeaderText("Market POS güncelleniyor, lütfen bekleyin...");
        dialog.getDialogPane().setContent(icerik);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);

        // İndirmeyi arka planda çalıştır
        new Thread(() -> {
            try {
                // İndirme
                GuncellemeService.indir(indirmeUrl, yuzde -> {
                    Platform.runLater(() -> {
                        progressBar.setProgress(yuzde / 100.0);
                        durumLabel.setText("İndiriliyor... %" + yuzde);
                    });
                });

                Platform.runLater(() -> {
                    durumLabel.setText("Güncelleme uygulanıyor...");
                    progressBar.setProgress(-1); // belirsiz
                });

                // Uygula
                GuncellemeService.guncellemeUygula();

                Platform.runLater(() -> {
                    dialog.close();
                    Alert bitti = new Alert(Alert.AlertType.INFORMATION);
                    bitti.initOwner(stage);
                    bitti.setTitle("Güncelleme Tamamlandı");
                    bitti.setHeaderText(null);
                    bitti.setContentText("Güncelleme indirildi. Uygulama şimdi yeniden başlayacak.");
                    bitti.showAndWait();
                    Platform.exit();
                    System.exit(0);
                });

            } catch (Exception e) {
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                String stackTrace = sw.toString();

                GuncellemeService.guncellemeyiLogla("=== HATA ===");
                GuncellemeService.guncellemeyiLogla("toString: " + e);
                GuncellemeService.guncellemeyiLogla("Stack:\n" + stackTrace);

                System.err.println("[GÜNCELLEME HATA] " + e);
                e.printStackTrace();

                Platform.runLater(() -> {
                    dialog.close();
                    guncellemeHataDiyaloguGoster(e.toString(), stackTrace);
                });
            }
        }, "guncelleme-indir").start();

        dialog.showAndWait();
    }

    private void guncellemeHataDiyaloguGoster(String hataStr, String stackTrace) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(stage);
        alert.setResizable(true);
        alert.setTitle("Güncelleme Hatası");
        alert.setHeaderText("Güncelleme başarısız.");
        alert.setContentText(hataStr);

        // Expandable stack trace alanı
        TextArea ta = new TextArea(hataStr + "\n\n" + stackTrace
                + "\nLog: AppData/Local/MarketPOS/guncelleme/guncelleme.log");
        ta.setEditable(false);
        ta.setWrapText(true);
        ta.setMaxWidth(Double.MAX_VALUE);
        ta.setMaxHeight(Double.MAX_VALUE);
        GridPane.setVgrow(ta, Priority.ALWAYS);
        GridPane.setHgrow(ta, Priority.ALWAYS);

        GridPane detay = new GridPane();
        detay.setMaxWidth(Double.MAX_VALUE);
        detay.add(new Label("Detay:"), 0, 0);
        detay.add(ta, 0, 1);

        alert.getDialogPane().setExpandableContent(detay);
        alert.getDialogPane().setExpanded(true);
        alert.getDialogPane().setMinWidth(500);
        alert.getDialogPane().setMinHeight(300);
        alert.showAndWait();
    }

    // ===== KAYITLI GİRİŞ YARDIMCI METODLARI =====
    // Format: base64(kullaniciAdi)|base64(sifre)
    private void girisKaydet_yaz(String kAdi, String sifre) {
        try {
            Files.createDirectories(GIRIS_DOSYASI.getParent());
            String enc = Base64.getEncoder().encodeToString(kAdi.getBytes(StandardCharsets.UTF_8))
                    + "|"
                    + Base64.getEncoder().encodeToString(sifre.getBytes(StandardCharsets.UTF_8));
            Files.writeString(GIRIS_DOSYASI, enc, StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
    }

    private String[] girisKaydet_oku() {
        try {
            if (!Files.exists(GIRIS_DOSYASI)) return null;
            String icerik = Files.readString(GIRIS_DOSYASI, StandardCharsets.UTF_8).trim();
            String[] parcalar = icerik.split("\\|", 2);
            if (parcalar.length != 2) return null;
            String kAdi  = new String(Base64.getDecoder().decode(parcalar[0]), StandardCharsets.UTF_8);
            String sifre = new String(Base64.getDecoder().decode(parcalar[1]), StandardCharsets.UTF_8);
            if (kAdi.isBlank() || sifre.isBlank()) return null;
            return new String[]{kAdi, sifre};
        } catch (Exception ignored) {
            return null;
        }
    }

    private void girisKaydetmeyiSil() {
        try { Files.deleteIfExists(GIRIS_DOSYASI); } catch (Exception ignored) {}
    }

    private void kayitEkraniAc() {
        KayitEkrani kayit = new KayitEkrani(stage);
        stage.setScene(kayit.olustur());
        stage.setWidth(500);
        stage.setHeight(550);
        stage.centerOnScreen();
    }

    private void girisYap() {
        String kAdi = kullaniciAdiField.getText().trim();
        String sifre = sifreField.getText().trim();

        if (kAdi.isEmpty() || sifre.isEmpty()) {
            hataMesaji.setText("Lütfen tüm alanları doldurun!");
            return;
        }

        girisBtn.setDisable(true);
        girisBtn.setText("Giriş yapılıyor...");
        hataMesaji.setText("");

        // Arka planda API çağrısı — UI donmasın
        new Thread(() -> {
            try {
                Map<String, Object> yanit = ApiClient.postPublic("/api/auth/giris",
                        Map.of("kullaniciAdi", kAdi, "sifre", sifre));

                if (yanit.containsKey("token")) {
                    String token = (String) yanit.get("token");
                    Long mktId = Long.valueOf(yanit.get("marketId").toString());
                    String r = (String) yanit.get("rol");

                    ApiClient.tokenKaydet(token, mktId, kAdi, r);
                    // Başarılı giriş → bilgileri kaydet (her seferinde güncelle)
                    girisKaydet_yaz(kAdi, sifre);

                    Platform.runLater(() -> {
                        // Kasa ekranına geç
                        KasaEkrani kasaEkrani = new KasaEkrani(stage);
                        Scene kasaSahne = kasaEkrani.olustur();
                        stage.setScene(kasaSahne);
                        stage.setWidth(1280);
                        stage.setHeight(800);
                        stage.centerOnScreen();
                    });

                } else {
                    String hata = yanit.getOrDefault("hata", "Giriş başarısız!").toString();
                    Platform.runLater(() -> {
                        hataMesaji.setText(hata);
                        girisBtn.setDisable(false);
                        girisBtn.setText("GİRİŞ YAP");
                    });
                }

            } catch (Exception ex) {
                Platform.runLater(() -> {
                    hataMesaji.setText("Sunucuya bağlanılamadı!");
                    girisBtn.setDisable(false);
                    girisBtn.setText("GİRİŞ YAP");
                });
            }
        }).start();
    }
}