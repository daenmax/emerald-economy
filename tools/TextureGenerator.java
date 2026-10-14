import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * 程序化生成模组图标（128×128 的 {@code emerald_economy_logo.png}）。
 *
 * <p><b>注意</b>：方块贴图（{@code textures/block/*.png}）从 2026-10 起改由
 * <b>Blockbench</b> 制作，同名文件已经换成美术版本。这里刻意不再生成方块贴图，
 * 免得手滑跑一次脚本就把美术资源覆盖回「简笔金属 + 屏幕」。
 * 需要重做方块贴图请用 {@code 素材/blockbench/<机器>.bbmodel} 在 Blockbench 里改，
 * 或者改 {@code tools/gen_bbmodel.py}（它只生成工程文件，不生成贴图）。
 *
 * <p>用法：{@code java tools/TextureGenerator.java}
 */
public final class TextureGenerator {

    private static final int SIZE = 16;

    // 外壳配色（仅用于参考，方块贴图已由 Blockbench 制作）
    private static final int CASING = 0xFF3B4048;
    private static final int CASING_DARK = 0xFF282C32;
    private static final int CASING_LIGHT = 0xFF515862;
    private static final int RIVET = 0xFF767E8A;

    // 屏幕配色
    private static final int SCREEN_BG = 0xFF0E1114;

    private TextureGenerator() {
    }

    public static void main(String[] args) throws IOException {
        File root = new File(args.length > 0 ? args[0] : "src/main/resources");
        if (!root.exists() && !root.mkdirs()) {
            throw new IOException("无法创建目录：" + root);
        }
        write(new File(root, "emerald_economy_logo.png"), logo());
        System.out.println("模组图标已生成到 " + root.getAbsolutePath());
    }

    private static void write(File file, BufferedImage image) throws IOException {
        ImageIO.write(image, "png", file);
    }

    /** 金属外壳：底色 + 暗边 + 铆钉 + 两条装饰缝。 */
    private static BufferedImage casing() {
        BufferedImage image = blank(CASING);
        // 四周暗边
        for (int i = 0; i < SIZE; i++) {
            set(image, i, 0, CASING_DARK);
            set(image, i, SIZE - 1, CASING_DARK);
            set(image, 0, i, CASING_DARK);
            set(image, SIZE - 1, i, CASING_DARK);
        }
        // 装饰缝
        for (int i = 1; i < SIZE - 1; i++) {
            set(image, i, 3, CASING_LIGHT);
            set(image, i, SIZE - 4, CASING_LIGHT);
        }
        // 四角铆钉
        int[] xs = {2, SIZE - 3};
        int[] ys = {2, SIZE - 3};
        for (int x : xs) {
            for (int y : ys) {
                set(image, x, y, RIVET);
            }
        }
        // 轻微的高光，让平面不至于死板
        for (int i = 4; i < SIZE - 4; i++) {
            set(image, i, 4, CASING_LIGHT);
        }
        return image;
    }

    /** 屏幕面板：深色底 + 强调色边框与「文字行」。 */
    private static BufferedImage face(int accentRgb) {
        int accent = 0xFF000000 | accentRgb;
        int accentDark = darken(accentRgb, 0.55D);
        BufferedImage image = blank(SCREEN_BG);
        // 边框
        for (int i = 0; i < SIZE; i++) {
            set(image, i, 0, accent);
            set(image, i, SIZE - 1, accentDark);
            set(image, 0, i, accentDark);
            set(image, SIZE - 1, i, accentDark);
        }
        // 屏幕里的「数据行」
        int[][] rows = {
                {4, 3, 8},
                {6, 5, 11},
                {8, 3, 6},
                {10, 7, 12},
        };
        for (int[] row : rows) {
            int y = row[0];
            for (int x = row[1]; x <= row[2]; x++) {
                set(image, x, y, accent);
            }
        }
        return image;
    }

    /** 模组图标：深色底 + 绿宝石菱形 + 金色环。 */
    private static BufferedImage logo() {
        int size = 128;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        fill(image, 0xFF101418);
        int center = size / 2;
        // 金色圆环
        for (int angle = 0; angle < 360; angle++) {
            double rad = Math.toRadians(angle);
            for (int r = 46; r <= 52; r++) {
                int x = (int) Math.round(center + Math.cos(rad) * r);
                int y = (int) Math.round(center + Math.sin(rad) * r);
                set(image, x, y, 0xFFFFC24D);
            }
        }
        // 绿宝石菱形
        int half = 34;
        for (int dy = -half; dy <= half; dy++) {
            int span = half - Math.abs(dy);
            for (int dx = -span; dx <= span; dx++) {
                int x = center + dx;
                int y = center + dy;
                // 简单的明暗：左上亮、右下暗
                int shade = (dx - dy) > 0 ? 0xFF0F9C49 : 0xFF17DD62;
                if (dx + dy < -half / 2) {
                    shade = 0xFF57F08A;
                }
                set(image, x, y, shade);
            }
        }
        return image;
    }

    private static BufferedImage blank(int argb) {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        fill(image, argb);
        return image;
    }

    private static void fill(BufferedImage image, int argb) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setRGB(x, y, argb);
            }
        }
    }

    private static void set(BufferedImage image, int x, int y, int argb) {
        if (x < 0 || y < 0 || x >= image.getWidth() || y >= image.getHeight()) {
            return;
        }
        image.setRGB(x, y, argb);
    }

    private static int darken(int rgb, double factor) {
        Color color = new Color(rgb);
        return 0xFF000000 | ((int) (color.getRed() * factor) << 16)
                | ((int) (color.getGreen() * factor) << 8)
                | (int) (color.getBlue() * factor);
    }
}
