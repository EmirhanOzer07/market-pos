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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

/**
 * Giriş ekranı JavaFX görünümü.
 *
 * <p>Kullanıcı adı ve şifre ile giriş yapar, lisans bitiş tarihi yaklaşınca
 * uyarı gösterir. Ctrl+Shift+P ile gizli patron paneline erişilebilir.</p>
 */
public class GirisEkrani {

    private static final Logger log = LoggerFactory.getLogger(GirisEkrani.class);

    private static final Path GIRIS_DOSYASI = Paths.get(
            System.getProperty("user.home"), "AppData", "Local", "MarketPOS", "giris.dat");

    // =========================================================
    // AES-GCM şifreli giriş saklama (CWE-1004 düzeltmesi)
    // Anahtar: JWT_SECRET'ın SHA-256'sından türetilen 128-bit
    // JWT_SECRET kurulum başında rastgele üretilir → her kurulum benzersiz anahtar
    // =========================================================
    private static final String AES_ALGO = "AES/GCM/NoPadding";
    private static final SecretKey GIRIS_ANAHTARI = girisAnahtariTuret();

    private static SecretKey girisAnahtariTuret() {
        try {
            String jwt = System.getProperty("JWT_SECRET");
            if (jwt == null || jwt.isBlank()) {
                // PosApplication her zaman JWT_SECRET'ı config'den yükler.
                // Buraya düşülüyorsa başlatma sırası bozuktur — güvenli sıfır anahtar döndür.
                log.error("[GÜVENLİK] JWT_SECRET sistem özelliği bulunamadı — giriş şifreleme devre dışı");
                return new SecretKeySpec(new byte[16], "AES");
            }
            byte[] tam = MessageDigest.getInstance("SHA-256")
                    .digest((jwt + ":giris-v1").getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(Arrays.copyOf(tam, 16), "AES");
        } catch (Exception e) {
            log.error("Giriş şifreleme anahtarı türetilemedi", e);
            return new SecretKeySpec(new byte[16], "AES");
        }
    }

    private final Stage stage;
    private TextField kullaniciAdiField;
    private PasswordField sifreField;
    private Label hataMesaji;
    private Button girisBtn;

    public GirisEkrani(Stage stage) {
        this.stage = stage;
    }

    public Scene olustur() {
        boolean koyu = AyarYoneticisi.isKoyu();
        String arkaRenk  = AyarYoneticisi.arkaRengi();
        String formRenk  = AyarYoneticisi.formRengi();
        String metinRenk = AyarYoneticisi.metinRengi();
        String ikincil   = AyarYoneticisi.ikincilMetin();
        String inputArka = AyarYoneticisi.inputArka();
        String inputKenar = AyarYoneticisi.inputKenarlık();

        // ===== BAŞLIK =====
        Label baslik = new Label("🏪 MARKET POS SİSTEMİ");
        baslik.setFont(Font.font("Arial", FontWeight.BOLD, 22));
        baslik.setTextFill(Color.web(metinRenk));

        Label altBaslik = new Label("Sisteme giriş yapın");
        altBaslik.setFont(Font.font("Arial", 13));
        altBaslik.setTextFill(Color.web(ikincil));

        VBox baslikKutu = new VBox(5, baslik, altBaslik);
        baslikKutu.setAlignment(Pos.CENTER);
        baslikKutu.setPadding(new Insets(0, 0, 20, 0));

        // ===== KULLANICI ADI =====
        Label kAdiLabel = new Label("Kullanıcı Adı");
        kAdiLabel.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        kAdiLabel.setTextFill(Color.web(metinRenk));

        kullaniciAdiField = new TextField();
        kullaniciAdiField.setPromptText("Kullanıcı adınızı girin...");
        kullaniciAdiField.setPrefHeight(40);
        kullaniciAdiField.setFont(Font.font("Arial", 14));
        kullaniciAdiField.setStyle(
                "-fx-border-color: " + inputKenar + "; -fx-border-radius: 6; " +
                "-fx-background-color: " + inputArka + "; " +
                "-fx-control-inner-background: " + inputArka + "; " +
                "-fx-background-radius: 6; -fx-padding: 8; " +
                "-fx-text-fill: " + metinRenk + "; " +
                "-fx-prompt-text-fill: " + ikincil + ";");

        // ===== ŞİFRE =====
        Label sifreLabel = new Label("Şifre");
        sifreLabel.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        sifreLabel.setTextFill(Color.web(metinRenk));

        sifreField = new PasswordField();
        sifreField.setPromptText("Şifrenizi girin...");
        sifreField.setPrefHeight(40);
        sifreField.setFont(Font.font("Arial", 14));
        sifreField.setStyle(
                "-fx-border-color: " + inputKenar + "; -fx-border-radius: 6; " +
                "-fx-background-color: " + inputArka + "; " +
                "-fx-control-inner-background: " + inputArka + "; " +
                "-fx-background-radius: 6; -fx-padding: 8; " +
                "-fx-text-fill: " + metinRenk + "; " +
                "-fx-prompt-text-fill: " + ikincil + ";");

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

        // ===== TEMA TOGGLE BUTONU =====
        Button temaBtn = new Button(koyu ? "☀" : "🌙");
        temaBtn.setFont(Font.font("Arial", 14));
        temaBtn.setStyle(
                "-fx-background-color: transparent; -fx-text-fill: " + ikincil + "; " +
                "-fx-cursor: hand; -fx-border-color: " + inputKenar + "; " +
                "-fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 4 8;");
        temaBtn.setTooltip(new Tooltip(koyu ? "Açık Temaya Geç" : "Koyu Temaya Geç"));
        temaBtn.setOnAction(e -> {
            AyarYoneticisi.temaToggle();
            // Ekranı yeni temayla yeniden oluştur
            stage.setScene(new GirisEkrani(stage).olustur());
        });

        HBox temaKutu = new HBox(temaBtn);
        temaKutu.setAlignment(Pos.CENTER_RIGHT);
        temaKutu.setPadding(new Insets(0, 0, 8, 0));

        // ===== FORM LAYOUT =====
        VBox form = new VBox(10,
                temaKutu,
                baslikKutu,
                kAdiLabel, kullaniciAdiField,
                sifreLabel, sifreField,
                girisBtn,
                kayitliSilLink,
                kayitOlBtn,
                hataMesaji
        );
        form.setPadding(new Insets(32, 40, 40, 40));
        form.setMaxWidth(380);
        form.setStyle(
                "-fx-background-color: " + formRenk + "; -fx-background-radius: 12; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.18), 20, 0, 0, 4);");

        // ===== ANA LAYOUT =====
        StackPane anaLayout = new StackPane(form);
        anaLayout.setStyle("-fx-background-color: " + arkaRenk + ";");
        anaLayout.setPadding(new Insets(60));

        // ===== OLAYLAR =====
        girisBtn.setOnAction(e -> girisYap());
        sifreField.setOnAction(e -> girisYap()); // Enter ile giriş
        kullaniciAdiField.setOnAction(e -> sifreField.requestFocus());

        Scene scene = new Scene(anaLayout, 500, 420);

        // Ctrl+Shift+P ile gizli patron paneline erişim
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

    // ===== LİSANS UYARISI (30 gün ve altı) =====
    private void lisansUyarisiGoster(long kalanGun) {
        String mesaj;
        String renk;
        if (kalanGun == 0) {
            mesaj = "⚠  Lisansınız BUGÜN sona eriyor!\nYenileme yapılmazsa yarın giriş yapılamaz.";
            renk  = "#e74c3c";
        } else if (kalanGun <= 7) {
            mesaj = "⚠  Lisansınız " + kalanGun + " gün içinde sona eriyor!\nYenileme için yöneticinizle iletişime geçin.";
            renk  = "#e74c3c";
        } else {
            mesaj = "ℹ  Lisansınız " + kalanGun + " gün içinde sona eriyor.\nYenileme için yöneticinizle iletişime geçin.";
            renk  = "#e67e22";
        }

        Label ikonLbl = new Label(kalanGun <= 7 ? "⚠" : "ℹ");
        ikonLbl.setFont(Font.font("Arial", 36));
        ikonLbl.setTextFill(Color.WHITE);

        Label mesajLbl = new Label(mesaj);
        mesajLbl.setFont(Font.font("Arial", 13));
        mesajLbl.setTextFill(Color.WHITE);
        mesajLbl.setWrapText(true);

        HBox icerikKutu = new HBox(14, ikonLbl, mesajLbl);
        icerikKutu.setAlignment(Pos.CENTER_LEFT);
        icerikKutu.setPadding(new Insets(18, 20, 18, 20));
        icerikKutu.setStyle("-fx-background-color: " + renk + "; -fx-background-radius: 8 8 0 0;");

        Button tamam = new Button("Anladım");
        tamam.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        tamam.setStyle("-fx-background-color: " + renk + "; -fx-text-fill: white; " +
                "-fx-background-radius: 6; -fx-cursor: hand; -fx-padding: 8 24;");

        HBox butonKutu = new HBox(tamam);
        butonKutu.setAlignment(Pos.CENTER_RIGHT);
        butonKutu.setPadding(new Insets(12, 20, 12, 20));
        butonKutu.setStyle("-fx-background-color: #f8f9fa; -fx-background-radius: 0 0 8 8;");

        VBox govde = new VBox(0, icerikKutu, butonKutu);

        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(stage);
        dialog.setTitle("Lisans Uyarısı");
        dialog.getDialogPane().setContent(govde);
        dialog.getDialogPane().getButtonTypes().clear();
        dialog.getDialogPane().setStyle("-fx-padding: 0; -fx-background-radius: 8;");

        tamam.setOnAction(e -> dialog.close());
        dialog.showAndWait();
    }

    // ===== GÜNCELLEME DİYALOGU — Şık sürüm notları (G) =====
    private void guncellemeDiyaloguGoster(GuncellemeService.GuncellemeBilgisi bilgi) {
        String mevcutV = GuncellemeService.mevcutSurumOku();
        String yeniV   = bilgi.yeniSurum();

        // ── Başlık barı ──
        Label baslikLbl = new Label("Güncelleme Mevcut");
        baslikLbl.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        baslikLbl.setTextFill(Color.WHITE);

        Label versiyon = new Label("v" + mevcutV + "  →  v" + yeniV);
        versiyon.setFont(Font.font("Arial", 12));
        versiyon.setTextFill(Color.web("#a9cce3"));

        VBox baslikKutu = new VBox(3, baslikLbl, versiyon);

        Label roketLbl = new Label("🚀");
        roketLbl.setFont(Font.font("Arial", 32));

        HBox header = new HBox(14, roketLbl, baslikKutu);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(18, 20, 18, 20));
        header.setStyle("-fx-background-color: #1a3a5c;");

        // ── Sürüm notları ──
        VBox notlarKutu = new VBox(6);
        notlarKutu.setPadding(new Insets(14, 20, 4, 20));

        Label notBaslik = new Label("Bu sürümde neler var:");
        notBaslik.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        notBaslik.setTextFill(Color.web("#2c3e50"));
        notlarKutu.getChildren().add(notBaslik);

        if (!bilgi.surumNotu().isBlank()) {
            String[] satirlar = bilgi.surumNotu().split("\n");
            int gosterilenSatir = 0;
            for (String satir : satirlar) {
                String temiz = satir.trim();
                if (temiz.isBlank()) continue;
                if (gosterilenSatir >= 8) {
                    Label devami = new Label("  ... daha fazlası için GitHub'a bakın");
                    devami.setFont(Font.font("Arial", 11));
                    devami.setTextFill(Color.web("#7f8c8d"));
                    notlarKutu.getChildren().add(devami);
                    break;
                }
                // Markdown başlık/madde işaretlerini temizle
                String gorunen = temiz.replaceFirst("^#+\\s*", "").replaceFirst("^[-*]\\s*", "");
                Label madde = new Label("  •  " + gorunen);
                madde.setFont(Font.font("Arial", 12));
                madde.setTextFill(Color.web("#2c3e50"));
                madde.setWrapText(true);
                notlarKutu.getChildren().add(madde);
                gosterilenSatir++;
            }
        } else {
            Label yokMesaj = new Label("  Sürüm notu bulunmuyor.");
            yokMesaj.setFont(Font.font("Arial", 12));
            yokMesaj.setTextFill(Color.web("#7f8c8d"));
            notlarKutu.getChildren().add(yokMesaj);
        }

        // ── Butonlar ──
        Button simdiGuncelle = new Button("⬇  Şimdi Güncelle");
        simdiGuncelle.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        simdiGuncelle.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; " +
                "-fx-background-radius: 7; -fx-cursor: hand; -fx-padding: 9 22;");

        Button sonra = new Button("Sonra Hatırlat");
        sonra.setFont(Font.font("Arial", 13));
        sonra.setStyle("-fx-background-color: #bdc3c7; -fx-text-fill: #2c3e50; " +
                "-fx-background-radius: 7; -fx-cursor: hand; -fx-padding: 9 16;");

        HBox butonSatiri = new HBox(10, simdiGuncelle, sonra);
        butonSatiri.setAlignment(Pos.CENTER_RIGHT);
        butonSatiri.setPadding(new Insets(14, 20, 18, 20));

        VBox govde = new VBox(0, header, notlarKutu, butonSatiri);
        govde.setStyle("-fx-background-color: white; -fx-background-radius: 10;");

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(stage);
        dialog.setTitle("Market POS Güncelleme");
        dialog.getDialogPane().setContent(govde);
        dialog.getDialogPane().getButtonTypes().clear();
        dialog.getDialogPane().setStyle("-fx-padding: 0;");
        dialog.getDialogPane().setMinWidth(420);

        simdiGuncelle.setOnAction(e -> {
            dialog.close();
            guncellemeBaslat(bilgi.indirmeUrl(), bilgi.yeniSurum());
        });
        sonra.setOnAction(e -> dialog.close());

        dialog.showAndWait();
    }

    private void guncellemeBaslat(String indirmeUrl, String yeniSurum) {
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
                GuncellemeService.guncellemeUygula(yeniSurum);

                Platform.runLater(() -> {
                    dialog.close();
                    Alert bitti = new Alert(Alert.AlertType.INFORMATION);
                    bitti.initOwner(stage);
                    bitti.setTitle("Güncelleme Tamamlandı");
                    bitti.setHeaderText("Güncelleme başarıyla tamamlandı!");
                    bitti.setContentText("Yeni sürüm yüklendi.\nUygulama yeniden başlatılıyor...");
                    bitti.getDialogPane().setMinWidth(380);
                    bitti.setResizable(true);
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

    // =========================================================
    // KAYITLI GİRİŞ — AES-256/GCM şifreli (CWE-1004 düzeltmesi)
    // Format: Base64(IV):Base64(AES-GCM(kullaniciAdi + TAB + sifre))
    // Eski Base64 format (":" içermez) → otomatik temizlenir, kullanıcı yeniden giriş yapar
    // =========================================================

    private void girisKaydet_yaz(String kAdi, String sifre) {
        try {
            Files.createDirectories(GIRIS_DOSYASI.getParent());
            byte[] iv = new byte[12];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(AES_ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, GIRIS_ANAHTARI, new GCMParameterSpec(128, iv));
            byte[] sifreli = cipher.doFinal((kAdi + "\t" + sifre).getBytes(StandardCharsets.UTF_8));
            String enc = Base64.getEncoder().encodeToString(iv)
                       + ":" + Base64.getEncoder().encodeToString(sifreli);
            Files.writeString(GIRIS_DOSYASI, enc, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.debug("Giriş bilgileri kaydedilemedi", e);
        }
    }

    private String[] girisKaydet_oku() {
        try {
            if (!Files.exists(GIRIS_DOSYASI)) return null;
            String icerik = Files.readString(GIRIS_DOSYASI, StandardCharsets.UTF_8).trim();
            // Eski format tespiti: ":" içermiyorsa eski düz Base64 — sil, kullanıcı yeniden girecek
            if (!icerik.contains(":")) {
                Files.deleteIfExists(GIRIS_DOSYASI);
                log.debug("Eski giris.dat formatı silindi, AES ile yeniden kaydedilecek");
                return null;
            }
            String[] dosyaParcalari = icerik.split(":", 2);
            if (dosyaParcalari.length != 2) return null;
            byte[] iv      = Base64.getDecoder().decode(dosyaParcalari[0]);
            byte[] sifreli = Base64.getDecoder().decode(dosyaParcalari[1]);
            Cipher cipher  = Cipher.getInstance(AES_ALGO);
            cipher.init(Cipher.DECRYPT_MODE, GIRIS_ANAHTARI, new GCMParameterSpec(128, iv));
            String metin = new String(cipher.doFinal(sifreli), StandardCharsets.UTF_8);
            String[] parcalar = metin.split("\t", 2);
            if (parcalar.length != 2 || parcalar[0].isBlank() || parcalar[1].isBlank()) return null;
            return parcalar;
        } catch (Exception e) {
            log.debug("Giriş bilgileri okunamadı, temizleniyor", e);
            try { Files.deleteIfExists(GIRIS_DOSYASI); } catch (Exception ignored) {}
            return null;
        }
    }

    private void girisKaydetmeyiSil() {
        try { Files.deleteIfExists(GIRIS_DOSYASI); } catch (Exception e) {
            log.debug("Giriş dosyası silinemedi", e);
        }
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

                    // Lisans bitiş tarihi kontrolü — 30 gün içindeyse uyar
                    long kalanGun = -1;
                    try {
                        Object lisansObj = yanit.get("lisansBitisTarihi");
                        if (lisansObj != null) {
                            LocalDate bitisTarihi = LocalDate.parse(lisansObj.toString());
                            kalanGun = ChronoUnit.DAYS.between(LocalDate.now(), bitisTarihi);
                        }
                    } catch (Exception ex) {
                        log.debug("Lisans tarihi okunamadı", ex);
                    }
                    final long kalanGunFinal = kalanGun;

                    Platform.runLater(() -> {
                        // Lisans uyarısı varsa önce göster
                        if (kalanGunFinal >= 0 && kalanGunFinal <= 30) {
                            lisansUyarisiGoster(kalanGunFinal);
                        }
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