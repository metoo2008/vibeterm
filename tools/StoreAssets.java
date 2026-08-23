import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** 生成 Google Play 商店素材(纯 JDK AWT,无第三方依赖)。 */
public class StoreAssets {
    static final Color BG = new Color(0x0D1117);
    static final Color GREEN = new Color(0x7EE787);
    static final Color FG = new Color(0xE6EDF3);
    static final Color GRAY = new Color(0x8B949E);

    static Font mono(float size) {
        try {
            Font f = Font.createFont(Font.TRUETYPE_FONT,
                new File("app/src/main/assets/fonts/JetBrainsMonoNL-Regular.ttf"));
            return f.deriveFont(Font.BOLD, size);
        } catch (Exception e) {
            return new Font("Monospaced", Font.BOLD, (int) size);
        }
    }

    static void aa(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    /** 绘制 ❯_ 提示符标记:chevron + 下划线。cx,cy 为 chevron 顶点区域中心,s 为尺度。 */
    static void mark(Graphics2D g, double cx, double cy, double s, double stroke) {
        g.setColor(GREEN);
        g.setStroke(new BasicStroke((float) stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        GeneralPath chev = new GeneralPath();
        chev.moveTo(cx - 0.55 * s, cy - 0.75 * s);
        chev.lineTo(cx + 0.35 * s, cy);
        chev.lineTo(cx - 0.55 * s, cy + 0.75 * s);
        g.draw(chev);
        // 下划线圆角矩形,位于 chevron 右下
        double uw = 1.05 * s, uh = 0.26 * s;
        double ux = cx + 0.15 * s, uy = cy + 0.55 * s;
        g.fill(new RoundRectangle2D.Double(ux, uy, uw, uh, uh, uh));
    }

    static void icon() throws Exception {
        int N = 512;
        BufferedImage img = new BufferedImage(N, N, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        aa(g);
        // 圆角深底(留出边距,Play 会自动裁圆角/加阴影)
        g.setColor(BG);
        g.fill(new RoundRectangle2D.Double(0, 0, N, N, 112, 112));
        mark(g, 232, 250, 150, 34);
        g.dispose();
        File out = new File("docs/store-assets/icon-512.png");
        out.getParentFile().mkdirs();
        ImageIO.write(img, "png", out);
        System.out.println("wrote " + out);
    }

    static void feature() throws Exception {
        int W = 1024, H = 500;
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        aa(g);
        g.setColor(BG);
        g.fillRect(0, 0, W, H);
        // 左侧标记
        mark(g, 170, 250, 150, 30);
        // 文案
        g.setFont(mono(96f));
        g.setColor(FG);
        g.drawString("VibeTerm", 360, 235);
        g.setFont(mono(34f));
        g.setColor(GREEN);
        g.drawString("SSH terminal for vibe coding", 362, 300);
        g.setFont(mono(26f));
        g.setColor(GRAY);
        g.drawString("native CJK input · tmux keep-alive", 362, 350);
        g.dispose();
        File out = new File("docs/store-assets/feature-1024x500.png");
        out.getParentFile().mkdirs();
        ImageIO.write(img, "png", out);
        System.out.println("wrote " + out);
    }

    public static void main(String[] a) throws Exception {
        System.setProperty("java.awt.headless", "true");
        icon();
        feature();
    }
}
