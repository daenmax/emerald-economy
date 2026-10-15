import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * 从美术素材生成模组图标（{@code src/main/resources/emerald_economy_logo.png}）。
 *
 * <p>模组图标显示在游戏的「模组列表」里，由 {@code neoforge.mods.toml} 的
 * {@code logoFile} 引用。2026-10 起图标改用美术素材 {@code 素材/logo.png}，
 * 本脚本只负责把素材处理成可直接打包的资源：
 * <ol>
 *   <li>把与画布四边连通的白色背景抠成透明（图形内部不与边缘相连的白色高光保持不动）；</li>
 *   <li>高质量缩放到 {@link #ICON_SIZE}×{@link #ICON_SIZE}（素材原图 1254×1254，原尺寸进 jar 太浪费）；</li>
 *   <li>写出 PNG。</li>
 * </ol>
 *
 * <p><b>注意</b>：方块贴图（{@code textures/block/*.png}）从 2026-10 起改由
 * <b>Blockbench</b> 制作，同名文件已经换成美术版本。这里刻意不再生成方块贴图，
 * 免得手滑跑一次脚本就把美术资源覆盖回「简笔金属 + 屏幕」。
 * 需要重做方块贴图请用 {@code 素材/blockbench/<机器>.bbmodel} 在 Blockbench 里改，
 * 或者改 {@code tools/gen_bbmodel.py}（它只生成工程文件，不生成贴图）。
 *
 * <p>换新 logo 的流程：把新图存成 {@code 素材/logo.png}，再运行本脚本。
 *
 * <p>用法：{@code java tools/TextureGenerator.java}
 */
public final class TextureGenerator {

    /** 模组图标的输出边长（正方形，像素）。模组列表里显示尺寸很小，256 已经绰绰有余。 */
    private static final int ICON_SIZE = 256;

    /** 美术素材路径（相对项目根目录）。 */
    private static final String SOURCE = "素材/logo.png";

    /** 资源输出路径（相对项目根目录）。 */
    private static final String OUTPUT = "src/main/resources/emerald_economy_logo.png";

    /** 判定「白色背景」的通道下限：R、G、B 都 ≥ 这个值才可能是背景白。 */
    private static final int WHITE_MIN = 240;

    /** 判定「白色背景」的通道差上限：三通道最大与最小的差 ≤ 这个值才算中性白（防止误伤浅色图形）。 */
    private static final int WHITE_SPREAD = 14;

    private TextureGenerator() {
    }

    public static void main(String[] args) throws IOException {
        File source = new File(args.length > 0 ? args[0] : SOURCE);
        File output = new File(args.length > 1 ? args[1] : OUTPUT);
        BufferedImage art = ImageIO.read(source);
        if (art == null) {
            throw new IOException("读不出图片（文件不存在或格式不支持）：" + source.getAbsolutePath());
        }
        BufferedImage cut = cutWhiteBackground(art);
        BufferedImage icon = shrinkToSquare(cut, ICON_SIZE);
        File parent = output.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("无法创建目录：" + parent);
        }
        ImageIO.write(icon, "png", output);
        System.out.println("模组图标已生成：" + output.getAbsolutePath()
                + "（源 " + source.getName() + " " + art.getWidth() + "×" + art.getHeight()
                + " → 输出 " + icon.getWidth() + "×" + icon.getHeight() + "）");
    }

    /**
     * 把与画布四边连通的白色像素抠成透明。
     * <p>用「从边缘泛洪」而不是「全图白色都透明」，是为了保住图形内部的白色：例如宝石上的高光、
     * 字母内侧的填白，这些白色不与边缘连通，不能被误抠。
     */
    private static BufferedImage cutWhiteBackground(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        // 统一转成 ARGB，后面可以直接改 alpha 通道
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, 0xFF000000 | (source.getRGB(x, y) & 0x00FFFFFF));
            }
        }
        boolean[] visited = new boolean[width * height];
        int[] stack = new int[width * height];
        int top = 0;
        // 种子：四条边上的白色像素（入栈时立刻标记 visited，保证每个像素最多进栈一次）
        for (int x = 0; x < width; x++) {
            top = pushIfBackground(image, visited, stack, top, x, 0);
            top = pushIfBackground(image, visited, stack, top, x, height - 1);
        }
        for (int y = 0; y < height; y++) {
            top = pushIfBackground(image, visited, stack, top, 0, y);
            top = pushIfBackground(image, visited, stack, top, width - 1, y);
        }
        // 泛洪：把连通的背景白一格一格抠掉，并尝试向上下左右扩散
        while (top > 0) {
            int index = stack[--top];
            int x = index % width;
            int y = index / width;
            image.setRGB(x, y, 0x00000000);
            if (x > 0) {
                top = pushIfBackground(image, visited, stack, top, x - 1, y);
            }
            if (x < width - 1) {
                top = pushIfBackground(image, visited, stack, top, x + 1, y);
            }
            if (y > 0) {
                top = pushIfBackground(image, visited, stack, top, x, y - 1);
            }
            if (y < height - 1) {
                top = pushIfBackground(image, visited, stack, top, x, y + 1);
            }
        }
        return image;
    }

    /** 若 (x, y) 是还没访问过的背景白，就压入栈；返回新的栈顶。 */
    private static int pushIfBackground(BufferedImage image, boolean[] visited, int[] stack,
                                        int top, int x, int y) {
        int index = y * image.getWidth() + x;
        if (visited[index]) {
            return top;
        }
        visited[index] = true;
        if (!isNeutralWhite(image.getRGB(x, y))) {
            return top;
        }
        stack[top] = index;
        return top + 1;
    }

    /** 判断一个像素是否算「背景白」：够亮，且三个通道彼此接近（中性灰白，不是浅绿/浅黄）。 */
    private static boolean isNeutralWhite(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        int min = Math.min(r, Math.min(g, b));
        int max = Math.max(r, Math.max(g, b));
        return min >= WHITE_MIN && (max - min) <= WHITE_SPREAD;
    }

    /**
     * 等比缩放到指定边长。
     * <p>一步从 1254 缩到 256 会丢细节（双线性插值只采样附近几个像素），
     * 所以先按「每次减半」缩几轮，最后一步再缩到目标尺寸，这是 Java2D 里常用的高质量缩小做法。
     */
    private static BufferedImage shrinkToSquare(BufferedImage source, int target) {
        BufferedImage current = source;
        while (current.getWidth() / 2 >= target && current.getHeight() / 2 >= target) {
            current = resize(current, current.getWidth() / 2, current.getHeight() / 2);
        }
        return resize(current, target, target);
    }

    private static BufferedImage resize(BufferedImage source, int width, int height) {
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = out.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
        graphics.drawImage(source, 0, 0, width, height, null);
        graphics.dispose();
        return out;
    }
}
