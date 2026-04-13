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

import java.util.Map;

/**
 * Yeni market kaydı için JavaFX kayıt ekranı.
 *
 * <p>Davetiye kodu doğrulamasını içeren kayıt formunu gösterir;
 * başarılı kayıt sonrası giriş ekranına yönlendirir.</p>
 */
public class KayitEkrani {

    private final Stage stage;
    private Label hataMesaji;

    public KayitEkrani(Stage stage) {
        this.stage = stage;
    }

    public Scene olustur() {

        Label baslik = new Label("🏪 YENİ MARKET KAYDI");
        baslik.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        baslik.setTextFill(Color.web("#2c3e50"));

        Label altBaslik = new Label("Davetiye kodunuzla kayıt olun");
        altBaslik.setFont(Font.font("Arial", 13));
        altBaslik.setTextFill(Color.web("#7f8c8d"));

        VBox baslikKutu = new VBox(5, baslik, altBaslik);
        baslikKutu.setAlignment(Pos.CENTER);
        baslikKutu.setPadding(new Insets(0, 0, 15, 0));

        TextField kullaniciAdiField = alanOlustur("Kullanıcı Adı", "Yönetici kullanıcı adı...");
        PasswordField sifreField = sifreAlaniOlustur("Şifre (min 8 karakter)", "Güçlü bir şifre seçin...");
        TextField marketAdiField = alanOlustur("Market Adı", "Marketinizin adı...");
        TextField davetiyeField = alanOlustur("Davetiye Kodu", "POS-XXXXXXXX formatında...");

        Button kayitBtn = new Button("HESAP OLUŞTUR");
        kayitBtn.setPrefWidth(Double.MAX_VALUE);
        kayitBtn.setPrefHeight(45);
        kayitBtn.setFont(Font.font("Arial", FontWeight.BOLD, 15));
        kayitBtn.setStyle(
                "-fx-background-color: #2980b9; -fx-text-fill: white; " +
                        "-fx-background-radius: 6; -fx-cursor: hand;");
        kayitBtn.setOnMouseEntered(e ->
                kayitBtn.setStyle(
                        "-fx-background-color: #2471a3; -fx-text-fill: white; " +
                                "-fx-background-radius: 6; -fx-cursor: hand;"));
        kayitBtn.setOnMouseExited(e ->
                kayitBtn.setStyle(
                        "-fx-background-color: #2980b9; -fx-text-fill: white; " +
                                "-fx-background-radius: 6; -fx-cursor: hand;"));
        kayitBtn.setOnAction(e -> kayitOl(
                kullaniciAdiField.getText().trim(),
                sifreField.getText().trim(),
                marketAdiField.getText().trim(),
                davetiyeField.getText().trim(),
                kayitBtn));

        Button geriBtn = new Button("← Giriş Ekranına Dön");
        geriBtn.setPrefWidth(Double.MAX_VALUE);
        geriBtn.setPrefHeight(38);
        geriBtn.setFont(Font.font("Arial", 13));
        geriBtn.setStyle(
                "-fx-background-color: transparent; -fx-text-fill: #7f8c8d; " +
                        "-fx-cursor: hand; -fx-border-color: #bdc3c7; " +
                        "-fx-border-radius: 6; -fx-background-radius: 6;");
        geriBtn.setOnAction(e -> {
            GirisEkrani giris = new GirisEkrani(stage);
            stage.setScene(giris.olustur());
            stage.setWidth(500);
            stage.setHeight(400);
            stage.centerOnScreen();
        });

        hataMesaji = new Label("");
        hataMesaji.setFont(Font.font("Arial", 13));
        hataMesaji.setWrapText(true);
        hataMesaji.setMaxWidth(Double.MAX_VALUE);

        VBox form = new VBox(10,
                baslikKutu,
                etiketOlustur("Kullanıcı Adı"), kullaniciAdiField,
                etiketOlustur("Şifre (min 8 karakter)"), sifreField,
                etiketOlustur("Market Adı"), marketAdiField,
                etiketOlustur("Davetiye Kodu"), davetiyeField,
                kayitBtn,
                geriBtn,
                hataMesaji
        );
        form.setPadding(new Insets(35));
        form.setMaxWidth(400);
        form.setStyle(
                "-fx-background-color: white; -fx-background-radius: 12; " +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 20, 0, 0, 4);");

        StackPane anaLayout = new StackPane(form);
        anaLayout.setStyle("-fx-background-color: #ecf0f1;");
        anaLayout.setPadding(new Insets(50));

        return new Scene(anaLayout, 500, 550);
    }

    private void kayitOl(String kAdi, String sifre, String marketAdi,
                         String davetiye, Button btn) {
        if (kAdi.isEmpty() || sifre.isEmpty() || marketAdi.isEmpty() || davetiye.isEmpty()) {
            bildir("Tüm alanları doldurun!", "#e74c3c");
            return;
        }
        if (sifre.length() < 8) {
            bildir("Şifre en az 8 karakter olmalı!", "#e74c3c");
            return;
        }

        btn.setDisable(true);
        btn.setText("Kayıt yapılıyor...");
        hataMesaji.setText("");

        new Thread(() -> {
            try {
                Map<String, Object> yanit = ApiClient.postPublic("/api/auth/kayit",
                        Map.of(
                                "kullaniciAdi", kAdi,
                                "sifre", sifre,
                                "marketAdi", marketAdi,
                                "davetiyeKodu", davetiye
                        ));

                Platform.runLater(() -> {
                    String mesaj = yanit.getOrDefault("mesaj", "").toString();
                    String hata = yanit.getOrDefault("hata", "").toString();

                    if (mesaj.contains("Başarılı") || "Başarılı".equals(mesaj)) {
                        bildir("✓ Kayıt başarılı! Giriş yapabilirsiniz.", "#27ae60");
                        new Thread(() -> {
                            try { Thread.sleep(2000); } catch (Exception ignored) {}
                            Platform.runLater(() -> {
                                GirisEkrani giris = new GirisEkrani(stage);
                                stage.setScene(giris.olustur());
                                stage.setWidth(500);
                                stage.setHeight(400);
                                stage.centerOnScreen();
                            });
                        }).start();
                    } else {
                        bildir(!hata.isEmpty() ? hata : "Kayıt başarısız!", "#e74c3c");
                        btn.setDisable(false);
                        btn.setText("HESAP OLUŞTUR");
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    bildir("Sunucuya bağlanılamadı!", "#e74c3c");
                    btn.setDisable(false);
                    btn.setText("HESAP OLUŞTUR");
                });
            }
        }).start();
    }

    private void bildir(String mesaj, String renk) {
        hataMesaji.setText(mesaj);
        hataMesaji.setStyle("-fx-text-fill: " + renk + ";");
    }

    private TextField alanOlustur(String prompt, String placeholder) {
        TextField field = new TextField();
        field.setPromptText(placeholder);
        field.setPrefHeight(40);
        field.setFont(Font.font("Arial", 14));
        field.setStyle(
                "-fx-border-color: #bdc3c7; -fx-border-radius: 6; " +
                        "-fx-background-radius: 6; -fx-padding: 8;");
        return field;
    }

    private PasswordField sifreAlaniOlustur(String prompt, String placeholder) {
        PasswordField field = new PasswordField();
        field.setPromptText(placeholder);
        field.setPrefHeight(40);
        field.setFont(Font.font("Arial", 14));
        field.setStyle(
                "-fx-border-color: #bdc3c7; -fx-border-radius: 6; " +
                        "-fx-background-radius: 6; -fx-padding: 8;");
        return field;
    }

    private Label etiketOlustur(String metin) {
        Label label = new Label(metin);
        label.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        label.setTextFill(Color.web("#2c3e50"));
        return label;
    }
}
