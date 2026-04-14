package com.market.pos.build;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Build-time yardımcı: OZR POS ikonunu ICO formatında üretir.
 *
 * <p>Kullanım (mvn package -DskipTests sonrasında):</p>
 * <pre>
 *   java -cp target\classes com.market.pos.build.IconMaker
 * </pre>
 * Çıktı: target/icon.ico (jpackage için)
 */
public class IconMaker {

    public static void main(String[] args) throws Exception {
        Files.createDirectories(Paths.get("target"));

        BufferedImage img = ikonCiz(256);

        // PNG baytlarını belleğe yaz
        ByteArrayOutputStream pngBellek = new ByteArrayOutputStream();
        ImageIO.write(img, "PNG", pngBellek);
        byte[] png = pngBellek.toByteArray();

        // ICO formatı (little-endian, tek görüntü, PNG-embedded)
        try (FileOutputStream out = new FileOutputStream("target/icon.ico")) {
            // ── ICO başlığı (6 byte) ──
            leShort(out, 0);  // reserved
            leShort(out, 1);  // type = 1 (ICO)
            leShort(out, 1);  // görüntü sayısı = 1

            // ── Dizin girdisi (16 byte) ──
            out.write(0);     // genişlik  = 256 (0 → 256 anlamı)
            out.write(0);     // yükseklik = 256
            out.write(0);     // renk paleti = 0 (truecolor)
            out.write(0);     // reserved
            leShort(out, 1);  // planes = 1
            leShort(out, 32); // bpp = 32
            leInt(out, png.length);  // PNG veri boyutu
            leInt(out, 22);          // PNG veri ofseti (6 başlık + 16 dizin)

            // ── PNG verisi ──
            out.write(png);
        }

        System.out.println("[IconMaker] target/icon.ico oluşturuldu — " + png.length + " byte");
    }

    // =========================================================
    // İkon çizimi — PosApplication.ikonOlustur() ile aynı tasarım
    // =========================================================

    public static BufferedImage ikonCiz(int boyut) {
        BufferedImage img = new BufferedImage(boyut, boyut, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY);

        double s = boyut / 256.0; // ölçek çarpanı

        // Dış glow
        g.setColor(new Color(0, 212, 255, 40));
        g.fillRoundRect(sc(s, 2), sc(s, 2), sc(s, 252), sc(s, 252), sc(s, 60), sc(s, 60));
        g.setColor(new Color(0, 212, 255, 25));
        g.fillRoundRect(0, 0, boyut, boyut, sc(s, 64), sc(s, 64));

        // Koyu lacivert arka plan
        g.setColor(new Color(13, 27, 42));
        g.fillRoundRect(sc(s, 6), sc(s, 6), sc(s, 244), sc(s, 244), sc(s, 52), sc(s, 52));

        // Üst cyan gradient parlama
        GradientPaint gradient = new GradientPaint(
                0, sc(s, 6),  new Color(0, 180, 230, 90),
                0, sc(s, 90), new Color(13, 27, 42, 0));
        g.setPaint(gradient);
        g.fillRoundRect(sc(s, 6), sc(s, 6), sc(s, 244), sc(s, 100), sc(s, 52), sc(s, 52));

        // Hex grid deseni
        g.setColor(new Color(0, 212, 255, 18));
        g.setStroke(new BasicStroke(1f));
        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 6; col++) {
                int hx = sc(s, 20 + col * 40 + (row % 2 == 0 ? 0 : 20));
                int hy = sc(s, 20 + row * 32);
                int r  = sc(s, 12);
                int[] xs = new int[6], ys = new int[6];
                for (int i = 0; i < 6; i++) {
                    xs[i] = hx + (int)(r * Math.cos(Math.toRadians(60 * i - 30)));
                    ys[i] = hy + (int)(r * Math.sin(Math.toRadians(60 * i - 30)));
                }
                g.drawPolygon(xs, ys, 6);
            }
        }

        // Yatay ayırıcı çizgi
        g.setColor(new Color(0, 212, 255, 60));
        g.setStroke(new BasicStroke(sc(s, 1.5f)));
        g.drawLine(sc(s, 28), sc(s, 175), sc(s, 228), sc(s, 175));

        // "OZR" — büyük cyan
        g.setColor(new Color(0, 212, 255));
        g.setFont(new Font("Arial", Font.BOLD, sc(s, 86)));
        FontMetrics fm = g.getFontMetrics();
        int x = (boyut - fm.stringWidth("OZR")) / 2;
        g.drawString("OZR", x, sc(s, 155));

        // "POS" — küçük beyaz
        g.setColor(new Color(200, 230, 245));
        g.setFont(new Font("Arial", Font.BOLD, sc(s, 34)));
        fm = g.getFontMetrics();
        x = (boyut - fm.stringWidth("POS")) / 2;
        g.drawString("POS", x, sc(s, 208));

        // Sağ üst köşe noktası
        g.setColor(new Color(0, 212, 255, 180));
        g.fillOval(sc(s, 212), sc(s, 20), sc(s, 14), sc(s, 14));
        g.setColor(new Color(0, 212, 255, 80));
        g.fillOval(sc(s, 208), sc(s, 16), sc(s, 22), sc(s, 22));

        // Sol alt üç nokta
        g.setColor(new Color(0, 212, 255, 120));
        g.fillOval(sc(s, 24), sc(s, 220), sc(s, 7), sc(s, 7));
        g.fillOval(sc(s, 35), sc(s, 220), sc(s, 7), sc(s, 7));
        g.fillOval(sc(s, 46), sc(s, 220), sc(s, 7), sc(s, 7));

        g.dispose();
        return img;
    }

    private static int sc(double scale, double val) {
        return (int) Math.round(scale * val);
    }

    // ── Little-endian yardımcıları ──

    private static void leShort(OutputStream out, int val) throws IOException {
        out.write(val & 0xFF);
        out.write((val >> 8) & 0xFF);
    }

    private static void leInt(OutputStream out, int val) throws IOException {
        out.write(val & 0xFF);
        out.write((val >> 8) & 0xFF);
        out.write((val >> 16) & 0xFF);
        out.write((val >> 24) & 0xFF);
    }
}
