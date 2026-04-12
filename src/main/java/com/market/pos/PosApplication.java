package com.market.pos;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.layout.BorderPane;
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
import java.nio.file.*;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Uygulamanın giriş noktası; Spring Boot ve JavaFX'i aynı süreçte başlatır.
 *
 * <p>Spring context bir arka plan thread'inde başlar; hazır olunca JavaFX UI
 * {@link GirisEkrani} ile gösterilir. Sistem tepsisi, güncelleme kontrolü ve
 * patron şifresi yükleme de bu sınıfta yönetilir.</p>
 */
@SpringBootApplication
@org.springframework.scheduling.annotation.EnableScheduling
public class PosApplication extends Application {

    private static final String APPDATA_DIR =
            System.getProperty("user.home") + "/AppData/Local/MarketPOS";
    private static final String CONFIG_DOSYASI = APPDATA_DIR + "/config.properties";

    private static ConfigurableApplicationContext springCtx;
    private static final CompletableFuture<Void> springHazir = new CompletableFuture<>();
    private Stage anaEkran;

    public static void main(String[] args) throws Exception {
        configHazirla();
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

        stage.setTitle("Market POS Sistemi");
        stage.setMinWidth(500);
        stage.setMinHeight(400);
        stage.setWidth(500);
        stage.setHeight(400);
        stage.centerOnScreen();

        // Yükleniyor ekranını hemen göster
        stage.setScene(yukleniyorSahnesiOlustur());
        stage.show();

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
            alert.setContentText("Market POS kapatılsın mı?\nKapatılırsa satış yapılamaz.");

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

        Label baslik = new Label("MARKET POS");
        baslik.setFont(javafx.scene.text.Font.font("Arial",
                javafx.scene.text.FontWeight.BOLD, 22));
        baslik.setTextFill(javafx.scene.paint.Color.web("#2c3e50"));

        Label alt = new Label("Sistem başlatılıyor...");
        alt.setFont(javafx.scene.text.Font.font("Arial", 13));
        alt.setTextFill(javafx.scene.paint.Color.web("#7f8c8d"));

        javafx.scene.layout.VBox kutu = new javafx.scene.layout.VBox(18,
                baslik, spinner, alt);
        kutu.setAlignment(javafx.geometry.Pos.CENTER);

        javafx.scene.layout.StackPane root = new javafx.scene.layout.StackPane(kutu);
        root.setStyle("-fx-background-color: #ecf0f1;");
        return new Scene(root, 500, 400);
    }

    private void pencereBoyutunuYukle(Stage stage) {
        try {
            File configDosya = new File(CONFIG_DOSYASI);
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
            File configDosya = new File(CONFIG_DOSYASI);
            Properties props = new Properties();
            if (configDosya.exists()) {
                try (InputStream is = new FileInputStream(configDosya)) {
                    props.load(is);
                }
            }
            props.setProperty("PENCERE_GENISLIK", String.valueOf((int) stage.getWidth()));
            props.setProperty("PENCERE_YUKSEKLIK", String.valueOf((int) stage.getHeight()));
            try (OutputStream os = new FileOutputStream(configDosya)) {
                props.store(os, "Market POS - Uygulama Yapilandirmasi");
            }
        } catch (Exception ignored) {}
    }

    private HBox baslikCubuguOlustur(Stage stage) {
        HBox cubuk = new HBox();
        cubuk.setStyle("-fx-background-color: #1a252f; -fx-padding: 8 12 8 12;");
        cubuk.setAlignment(Pos.CENTER_LEFT);
        cubuk.setSpacing(10);

        Label baslik = new Label("🏪 Market POS Sistemi");
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
            TrayIcon trayIcon = new TrayIcon(ikonOlustur(), "Market POS Sistemi");
            trayIcon.setImageAutoSize(true);

            PopupMenu menu = new PopupMenu();

            MenuItem baslik = new MenuItem("Market POS v1.0");
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
                        "Market POS kapatilsin mi?\nKapatilirsa satis yapilamaz.",
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
        BufferedImage img = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Arka plan — koyu yeşil yuvarlak
        g.setColor(new Color(27, 94, 32));
        g.fillRoundRect(8, 8, 240, 240, 48, 48);

        // İç parlama efekti
        g.setColor(new Color(76, 175, 80));
        g.fillRoundRect(16, 16, 224, 112, 40, 40);
        g.setColor(new Color(27, 94, 32));
        g.fillRoundRect(16, 80, 224, 80, 0, 0);

        // "POS" yazısı
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 80));
        FontMetrics fm = g.getFontMetrics();
        int x = (256 - fm.stringWidth("POS")) / 2;
        g.drawString("POS", x, 145);

        // Alt çizgi ve market yazısı
        g.setColor(new Color(165, 214, 167));
        g.setFont(new Font("Arial", Font.BOLD, 30));
        fm = g.getFontMetrics();
        x = (256 - fm.stringWidth("MARKET")) / 2;
        g.drawString("MARKET", x, 195);

        // Üst barkod çizgileri dekorasyon
        g.setColor(new Color(200, 230, 201));
        for (int i = 0; i < 8; i++) {
            int genislik = (i % 2 == 0) ? 4 : 2;
            g.fillRect(50 + i * 14, 28, genislik, 30);
        }

        g.dispose();

        // İkonu AppData'ya kaydet (jpackage için)
        try {
            File ikonDosya = new File(APPDATA_DIR + "/pos-icon.png");
            javax.imageio.ImageIO.write(img, "PNG", ikonDosya);
        } catch (Exception ignored) {}

        return img;
    }

    private static final String UYGULAMA_VERSIYONU = "1.0.0";

    private static void configHazirla() throws IOException {
        Files.createDirectories(Paths.get(APPDATA_DIR + "/temp"));
        System.setProperty("java.io.tmpdir", APPDATA_DIR + "/temp");
        Files.createDirectories(Paths.get(APPDATA_DIR));

        versiyonDosyasiKontrol();

        File configDosya = new File(CONFIG_DOSYASI);
        if (!configDosya.exists()) {
            ilkKurulumYap(configDosya);
        }
        configYukle(configDosya);

        // Port çakışması önleme: 8080'den başla, doluysa 8081..8090
        int port = portBul();
        System.setProperty("server.port", String.valueOf(port));
    }

    /** 8080-8090 arasında boş port bulur. */
    private static int portBul() {
        for (int p = 8080; p <= 8090; p++) {
            try (java.net.ServerSocket ss = new java.net.ServerSocket(p)) {
                ss.setReuseAddress(true);
                return p;
            } catch (IOException ignored) {}
        }
        return 8080;
    }

    private static void versiyonDosyasiKontrol() {
        Path versiyonYolu = Paths.get(APPDATA_DIR + "/version.txt");
        try {
            if (!Files.exists(versiyonYolu)) {
                Files.writeString(versiyonYolu, UYGULAMA_VERSIYONU);
            }
            // İleride: dosyadan okunan versiyon ile UYGULAMA_VERSIYONU karşılaştırılabilir
        } catch (IOException e) {
            System.err.println("Versiyon dosyası oluşturulamadı: " + e.getMessage());
        }
    }

    private static void ilkKurulumYap(File configDosya) throws IOException {
        Properties props = new Properties();

        // Her kurulum için benzersiz rastgele değerler üret — kaynak kodda YAZILMAZ
        String jwtSecret = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        String dbSifresi = UUID.randomUUID().toString().replace("-", "");

        props.setProperty("JWT_SECRET", jwtSecret);
        props.setProperty("DB_KULLANICI_SIFRESI", dbSifresi);

        // Patron şifresi düz metin değil — hash olarak sakla
        props.setProperty("SUPERADMIN_KEY_HASH", hashle("patron123"));

        try (OutputStream os = new FileOutputStream(configDosya)) {
            props.store(os, "Market POS - Uygulama Yapilandirmasi\n# BU DOSYAYI SILMEYIN");
        }
    }

    private static void configYukle(File configDosya) throws IOException {
        Properties props = new Properties();
        try (InputStream is = new FileInputStream(configDosya)) {
            props.load(is);
        }
        for (String key : props.stringPropertyNames()) {
            if (System.getenv(key) == null && System.getProperty(key) == null) {
                System.setProperty(key, props.getProperty(key));
            }
        }

        // Eski kurulum migrasyonu: DB_KULLANICI_SIFRESI yoksa config'e ekle.
        // Eski kurulumlar "pos_db_2024!" ile şifreli DB'ye sahip olduğundan
        // aynı değeri config'e yazıyoruz. Artık kaynak kodda değil, config.properties'de.
        // Yeni kurulumlar ilkKurulumYap'ta rastgele değer alır.
        if (!props.containsKey("DB_KULLANICI_SIFRESI")) {
            String eskiSifre = "pos_db_2024!"; // Eski hardcoded değer — sadece migrasyon için
            props.setProperty("DB_KULLANICI_SIFRESI", eskiSifre);
            System.setProperty("DB_KULLANICI_SIFRESI", eskiSifre);
            try (OutputStream os = new FileOutputStream(configDosya)) {
                props.store(os, "Market POS - Migrasyon guncellendi");
            }
        }

        // Environment variable'dan patron şifresi geldiyse hash'le
        String patronKey = System.getenv("SUPERADMIN_KEY");
        if (patronKey != null) {
            System.setProperty("SUPERADMIN_KEY_HASH", hashle(patronKey));
        }
    }

    /** SHA-256 ile hash hesaplar; patron şifresi bellekte düz metin olarak saklanmaz. */
    private static String hashle(String metin) {
        try {
            java.security.MessageDigest md =
                    java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(
                    metin.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }


}