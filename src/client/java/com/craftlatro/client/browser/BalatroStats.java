package com.craftlatro.client.browser;

import com.craftlatro.client.config.CraftlatroConfig;
import de.keksuccino.rinku.Rinku;
import net.minecraft.client.Minecraft;

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
                "screen viewport: " + viewportWidth + "x" + viewportHeight
                        + " at gui scale " + minecraft.getWindow().getGuiScale(),
                "render scale: " + CraftlatroConfig.getInstance().getBrowserRenderScale(),
                "target repaint rate: " + CraftlatroConfig.getInstance().getBrowserFrameRate() + " fps",
                "balatro page loaded: " + BalatroBrowser.isAvailable()
        );
    }

    public static List<String> reportLines() {
        return List.of(buildReport().split("\\R"));
    }

    /** Copies {@code text} to the system clipboard. Returns false when the clipboard is unavailable. */
    public static boolean copyToClipboard(String text) {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(text), null);
            return true;
        } catch (SecurityException | java.awt.HeadlessException unavailable) {
            return false;
        }
    }
}
