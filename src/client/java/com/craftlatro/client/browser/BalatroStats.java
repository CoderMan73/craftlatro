package com.craftlatro.client.browser;

import com.craftlatro.client.config.CraftlatroConfig;
import de.keksuccino.rinku.Rinku;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.HeadlessException;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Builds the plain-text diagnostic report behind {@code /craftlatro stats}.
 *
 * <p>Chromium's repaint rate is invisible from outside the browser, so judging whether the Balatro
 * page is running smoothly needs numbers rather than impressions. The report is written in a
 * copy-pasteable form and pushed to the system clipboard so it can be handed over verbatim, and it is
 * also logged so a session leaves a trail in the console.
 */
public final class BalatroStats {

    private static final Logger LOGGER = LoggerFactory.getLogger("craftlatro");
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z", Locale.ROOT);

    private BalatroStats() {
    }

    public static String buildReport() {
        Minecraft minecraft = Minecraft.getInstance();
        int viewportWidth = Math.max(1, minecraft.getWindow().getGuiScaledWidth());
        int viewportHeight = Math.max(1, minecraft.getWindow().getGuiScaledHeight());
        double interval = BalatroBrowser.averagePaintIntervalMillis();
        int surfaceWidth = BalatroBrowser.lastPaintWidth();
        int surfaceHeight = BalatroBrowser.lastPaintHeight();

        return String.join(System.lineSeparator(),
                "craftlatro diagnostics",
                "captured: " + ZonedDateTime.now().format(TIMESTAMP),
                "rinku: " + Rinku.VERSION,
                "minecraft fps: " + minecraft.getFps(),
                "chromium repaints/s: now " + BalatroBrowser.samplePaintRate()
                        + ", over " + BalatroBrowser.sampleHistorySeconds() + "s lowest "
                        + BalatroBrowser.lowestPaintRate() + " / highest " + BalatroBrowser.highestPaintRate(),
                "chromium paint gap: " + (interval > 0 ? String.format(Locale.ROOT, "%.1f", interval) + " ms" : "n/a"),
                "browser surface: " + surfaceWidth + "x" + surfaceHeight
                        + (BalatroBrowser.isParked() ? " (parked)" : ""),
                windowLine(),
                "screen viewport: " + viewportWidth + "x" + viewportHeight
                        + " at gui scale " + minecraft.getWindow().getGuiScale(),
                "render scale: " + CraftlatroConfig.getInstance().getBrowserRenderScale(),
                "repaint rate: target " + CraftlatroConfig.getInstance().getBrowserFrameRate()
                        + " fps, applied " + BalatroBrowser.appliedFrameRate() + " fps",
                "balatro page loaded: " + BalatroBrowser.isAvailable()
        );
    }

    public static List<String> reportLines() {
        return List.of(buildReport().split("\\R"));
    }

    /**
     * The persisted window state, which is what the player can move and resize. It is read from the
     * config rather than the screen because the screen does not exist when the report is taken from
     * the chat command, and the geometry is only written back when a drag or a mode toggle ends.
     */
    private static String windowLine() {
        CraftlatroConfig config = CraftlatroConfig.getInstance();
        return "window: " + (config.isWindowFramed() ? "framed" : "fullscreen")
                + " " + config.getWindowWidth() + "x" + config.getWindowHeight()
                + " at " + config.getWindowX() + "," + config.getWindowY()
                + ", world running: " + config.isWindowWorldRunning();
    }

    /**
     * Copies {@code text} to the system clipboard.
     *
     * <p>The GLFW window's own clipboard is used first because it is guaranteed to be reachable from a
     * running client, whereas the AWT toolkit refuses to hand out a clipboard when the JVM was started
     * headless, which is what happened in a development launch.
     *
     * @return false when no clipboard could be reached
     */
    public static boolean copyToClipboard(String text) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.getWindow() != null) {
            try {
                GLFW.glfwSetClipboardString(minecraft.getWindow().handle(), text);
                return true;
            } catch (RuntimeException failure) {
                LOGGER.warn("Could not reach the GLFW clipboard; falling back to AWT.", failure);
            }
        }

        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
            return true;
        } catch (HeadlessException | SecurityException failure) {
            LOGGER.warn("No system clipboard is available in this environment.", failure);
            return false;
        }
    }
}
