package com.market.pos;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.layout.HBox;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.geometry.Pos;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import com.market.pos.ekran.GirisEkrani;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

/**
 * Uygulamanın giriş noktası; Spring Boot ve JavaFX'i aynı süreçte başlatır.
 *
 * <p>Spring context bir arka plan thread'inde başlar; hazır olunca JavaFX UI
 * {@link GirisEkrani} ile gösterilir. Sistem tepsisi, güncelleme kontrolü ve
 * patron şifresi yükleme de bu sınıfta yönetilir.</p>
 */
@SpringBootApplication(exclude = {
        org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class,
        org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration.class,
        org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration.class
})
@org.springframework.scheduling.annotation.EnableScheduling
public class PosApplication extends Application {

    private static ConfigurableApplicationContext springCtx;
    private static final CompletableFuture<Void> springHazir = new CompletableFuture<>();
    private Stage anaEkran;

    public static void main(String[] args) throws Exception {
        ConfigManager.hazirla();
        System.setProperty("java.awt.headless", "false");

        // Spring'i arka planda başlat — JavaFX'i BEKLETME
        Thread springThread = new Thread(() -> {
            try {
                springCtx = SpringApplication.run(PosApplication.class, args);
                springHazir.complete(null);
            } catch (Exception e) {
                springHazir.completeExceptionally(e);
            }
        });
        springThread.setDaemon(false); // Spring kapanana kadar JVM çıkmasın
        springThread.setName("spring-main");
        springThread.start();

        // JavaFX'i hemen başlat — yükleniyor ekranı gösterilir
        Application.launch(PosApplication.class, args);
    }

    @Override
    public void start(Stage stage) {
        this.anaEkran = stage;

        stage.setTitle("OZR POS");
        stage.setMinWidth(500);
        stage.setMinHeight(560);
        stage.setWidth(500);
        stage.setHeight(560);
        stage.centerOnScreen();

        // Yükleniyor ekranını hemen göster
        stage.setScene(yukleniyorSahnesiOlustur());
        stage.show();

        // İkonu oluştur ve pencereye ekle
        try {
            ikonOlustur(); // AppData'ya kaydeder
            java.io.File ikonDosya = new java.io.File(ConfigManager.APPDATA_DIR + "/pos-icon.png");
            if (ikonDosya.exists()) {
                stage.getIcons().add(new javafx.scene.image.Image(ikonDosya.toURI().toString()));
            }
        } catch (Exception ignored) {}

        // Spring hazır olunca giriş ekranına geç
        springHazir.whenComplete((v, hata) -> Platform.runLater(() -> {
            if (hata != null) {
                javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                        javafx.scene.control.Alert.AlertType.ERROR);
                alert.setTitle("Başlatma Hatası");
                alert.setHeaderText("Uygulama başlatılamadı");
                alert.setContentText(hata.getMessage());
                alert.showAndWait();
                System.exit(1);
                return;
            }
            GirisEkrani girisEkrani = new GirisEkrani(stage);
            Scene girisScene = girisEkrani.olustur();
            stage.setScene(girisScene);
            pencereBoyutunuYukle(stage);
        }));

        stage.setOnCloseRequest(e -> {
            e.consume();
            pencereBoyutunuKaydet(stage);

            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.CONFIRMATION);
            alert.initOwner(stage);
            alert.setTitle("Çıkış");
            alert.setHeaderText(null);
            alert.setContentText("OZR POS kapatılsın mı?\nKapatılırsa satış yapılamaz.");

            javafx.scene.control.ButtonType evetBtn =
                    new javafx.scene.control.ButtonType("Evet, Kapat",
                            javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
            javafx.scene.control.ButtonType hayirBtn =
                    new javafx.scene.control.ButtonType("Hayır",
                            javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);

            alert.getButtonTypes().setAll(evetBtn, hayirBtn);

            alert.showAndWait().ifPresent(secim -> {
                if (secim == evetBtn) {
                    if (springCtx != null) springCtx.close();
                    Platform.exit();
                    System.exit(0);
                }
            });
        });

        stage.show();
    }

    private Scene yukleniyorSahnesiOlustur() {
        javafx.scene.control.ProgressIndicator spinner =
                new javafx.scene.control.ProgressIndicator(-1);
        spinner.setMaxSize(60, 60);

        Label baslik = new Label("OZR POS");
        baslik.setFont(javafx.scene.text.Font.font("Arial",
                javafx.scene.text.FontWeight.BOLD, 22));
        baslik.setTextFill(javafx.scene.paint.Color.web("#00d4ff"));

        Label alt = new Label("Sistem başlatılıyor...");
        alt.setFont(javafx.scene.text.Font.font("Arial", 13));
        alt.setTextFill(javafx.scene.paint.Color.web("#8faac0"));

        javafx.scene.layout.VBox kutu = new javafx.scene.layout.VBox(18,
                baslik, spinner, alt);
        kutu.setAlignment(javafx.geometry.Pos.CENTER);

        javafx.scene.layout.StackPane root = new javafx.scene.layout.StackPane(kutu);
        root.setStyle("-fx-background-color: #0d1b2a;");
        return new Scene(root, 500, 400);
    }

    private void pencereBoyutunuYukle(Stage stage) {
        try {
            File configDosya = new File(ConfigManager.APPDATA_DIR + "/config.properties");
            if (!configDosya.exists()) return;
            Properties props = new Properties();
            try (InputStream is = new FileInputStream(configDosya)) {
                props.load(is);
            }
            String genislik = props.getProperty("PENCERE_GENISLIK");
            String yukseklik = props.getProperty("PENCERE_YUKSEKLIK");
            if (genislik != null && yukseklik != null) {
                stage.setWidth(Double.parseDouble(genislik));
                stage.setHeight(Double.parseDouble(yukseklik));
            }
        } catch (Exception ignored) {}
    }

    private void pencereBoyutunuKaydet(Stage stage) {
        try {
            File configDosya = new File(ConfigManager.APPDATA_DIR + "/config.properties");
            Properties props = new Properties();
            if (configDosya.exists()) {
                try (InputStream is = new FileInputStream(configDosya)) {
                    props.load(is);
                }
            }
            props.setProperty("PENCERE_GENISLIK", String.valueOf((int) stage.getWidth()));
            props.setProperty("PENCERE_YUKSEKLIK", String.valueOf((int) stage.getHeight()));
            try (OutputStream os = new FileOutputStream(configDosya)) {
                props.store(os, "OZR POS - Uygulama Yapilandirmasi");
            }
        } catch (Exception ignored) {}
    }

    private HBox baslikCubuguOlustur(Stage stage) {
        HBox cubuk = new HBox();
        cubuk.setStyle("-fx-background-color: #1a252f; -fx-padding: 8 12 8 12;");
        cubuk.setAlignment(Pos.CENTER_LEFT);
        cubuk.setSpacing(10);

        Label baslik = new Label("OZR POS");
        baslik.setStyle("-fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold;");

        javafx.scene.layout.Region bosluk = new javafx.scene.layout.Region();
        HBox.setHgrow(bosluk, javafx.scene.layout.Priority.ALWAYS);

        Button kucult = new Button("─");
        kucult.setStyle("-fx-background-color: transparent; -fx-text-fill: white; -fx-font-size: 14px; -fx-cursor: hand; -fx-border-color: transparent;");
        kucult.setOnAction(e -> stage.setIconified(true));
        kucult.setOnMouseEntered(e -> kucult.setStyle("-fx-background-color: #34495e; -fx-text-fill: white; -fx-font-size: 14px; -fx-cursor: hand;"));
        kucult.setOnMouseExited(e -> kucult.setStyle("-fx-background-color: transparent; -fx-text-fill: white; -fx-font-size: 14px; -fx-cursor: hand; -fx-border-color: transparent;"));

        Button trayBtn = new Button("⊟");
        trayBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #95a5a6; -fx-font-size: 14px; -fx-cursor: hand; -fx-border-color: transparent;");
        trayBtn.setOnAction(e -> stage.hide());
        trayBtn.setOnMouseEntered(e -> trayBtn.setStyle("-fx-background-color: #34495e; -fx-text-fill: white; -fx-font-size: 14px; -fx-cursor: hand;"));
        trayBtn.setOnMouseExited(e -> trayBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #95a5a6; -fx-font-size: 14px; -fx-cursor: hand; -fx-border-color: transparent;"));

        Button kapat = new Button("✕");
        kapat.setStyle("-fx-background-color: transparent; -fx-text-fill: white; -fx-font-size: 13px; -fx-cursor: hand; -fx-border-color: transparent;");
        kapat.setOnAction(e -> stage.hide());
        kapat.setOnMouseEntered(e -> kapat.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-size: 13px; -fx-cursor: hand;"));
        kapat.setOnMouseExited(e -> kapat.setStyle("-fx-background-color: transparent; -fx-text-fill: white; -fx-font-size: 13px; -fx-cursor: hand; -fx-border-color: transparent;"));

        cubuk.getChildren().addAll(baslik, bosluk, kucult, trayBtn, kapat);

        final double[] baslangic = new double[2];
        cubuk.setOnMousePressed(e -> {
            baslangic[0] = e.getSceneX();
            baslangic[1] = e.getSceneY();
        });
        cubuk.setOnMouseDragged(e -> {
            stage.setX(e.getScreenX() - baslangic[0]);
            stage.setY(e.getScreenY() - baslangic[1]);
        });

        return cubuk;
    }

    private void baslatTray(Stage stage) {
        try {
            TrayIcon trayIcon = new TrayIcon(ikonOlustur(), "OZR POS");
            trayIcon.setImageAutoSize(true);

            PopupMenu menu = new PopupMenu();

            MenuItem baslik = new MenuItem("OZR POS v1.0");
            baslik.setEnabled(false);

            MenuItem goster = new MenuItem("Pencereyi Goster");
            goster.addActionListener(e -> Platform.runLater(() -> {
                stage.show();
                stage.toFront();
            }));

            MenuItem cikis = new MenuItem("Programi Kapat");
            cikis.addActionListener(e -> {
                int secim = javax.swing.JOptionPane.showConfirmDialog(
                        null,
                        "OZR POS kapatilsin mi?\nKapatilirsa satis yapilamaz.",
                        "Programi Kapat",
                        javax.swing.JOptionPane.YES_NO_OPTION,
                        javax.swing.JOptionPane.WARNING_MESSAGE
                );
                if (secim == javax.swing.JOptionPane.YES_OPTION) {
                    SystemTray.getSystemTray().remove(trayIcon);
                    if (springCtx != null) springCtx.close();
                    Platform.exit();
                    System.exit(0);
                }
            });

            menu.add(baslik);
            menu.addSeparator();
            menu.add(goster);
            menu.addSeparator();
            menu.add(cikis);

            trayIcon.setPopupMenu(menu);
            trayIcon.addActionListener(e -> Platform.runLater(() -> {
                stage.show();
                stage.toFront();
            }));

            SystemTray.getSystemTray().add(trayIcon);

        } catch (Exception e) {
            System.err.println("Tray baslatılamadı: " + e.getMessage());
        }
    }

    private static Image ikonOlustur() {
        BufferedImage img = com.market.pos.build.IconMaker.ikonCiz(256);

        // AppData'ya kaydet (sistem tepsisi + JavaFX pencere ikonu için)
        try {
            File ikonDosya = new File(ConfigManager.APPDATA_DIR + "/pos-icon.png");
            javax.imageio.ImageIO.write(img, "PNG", ikonDosya);
        } catch (Exception ignored) {}

        return img;
    }

}