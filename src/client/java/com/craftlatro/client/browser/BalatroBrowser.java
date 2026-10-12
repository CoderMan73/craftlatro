package com.craftlatro.client.browser;

import com.craftlatro.client.config.CraftlatroConfig;
import de.keksuccino.rinku.Rinku;
import de.keksuccino.rinku.RinkuBrowser;
import org.cef.browser.CefBrowserOsr;
import org.cef.browser.CefPaintEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Owns the single Chromium browser that shows the Balatro love.js page.
 *
 * <p>The browser deliberately outlives {@link BrowserScreen}. Closing the screen only detaches it;
 * the page keeps running so reopening the game is instant instead of re-unzipping the {@code .love}
 * archive. While no screen is showing, the surface is parked: audio is muted, the paint rate is cut
 * to a trickle and the surface is shrunk, because Rinku pumps pending paint frames for every live
 * browser on the Minecraft render thread, and an unshown full-rate surface costs frames and makes
 * noise while playing.
 */
public final class BalatroBrowser {

    private static final Logger LOGGER = LoggerFactory.getLogger("craftlatro");
    private static final int PARKED_WIDTH = 320;
    private static final int PARKED_HEIGHT = 180;
    private static final int PARKED_FRAME_RATE = 5;
    private static final int SAMPLE_WINDOW_MILLIS = 1000;

    private static final AtomicInteger paintsInWindow = new AtomicInteger();
    private static int lastPaintRate;
    private static long windowStartMillis;
    private static int lastPaintWidth;
    private static int lastPaintHeight;

    private static final Consumer<CefPaintEvent> PAINT_COUNTER = event -> {
        if (event == null || event.getPopup()) {
            return;
        }
        paintsInWindow.incrementAndGet();
        lastPaintWidth = event.getWidth();
        lastPaintHeight = event.getHeight();
    };

    private static RinkuBrowser browser;
    private static String activeUrl;
    private static boolean parked;
    private static int appliedFrameRate;
    private static boolean audioMuted;

    private BalatroBrowser() {
    }

    /**
     * @return the live browser for {@code url}, or {@code null} when Chromium is not ready yet
     */
    public static synchronized RinkuBrowser acquire(String url) {
        if (browser == null || !browser.isValid()) {
            releaseBrowser();
            if (!Rinku.isInitialized()) {
                return null;
            }
            LOGGER.info("Creating Balatro browser for {}", url);
            RinkuBrowser created = Rinku.createBrowser(url, false);
            created.useBrowserControls(false);
            browser = created;
            activeUrl = url;
            parked = false;
            appliedFrameRate = 0;
            audioMuted = false;
            paintsInWindow.set(0);
            windowStartMillis = 0;
            addPaintCounter(browser);
            return browser;
        }
        if (!url.equals(activeUrl)) {
            LOGGER.info("Balatro package changed; reloading browser from {}", url);
            browser.loadURL(url);
            activeUrl = url;
        }
        return browser;
    }

    /** Applies the on-screen resolution, the audio state and the configured paint rate. */
    public static synchronized void showViewport(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        parked = false;
        if (browser == null || !browser.isValid()) {
            return;
        }
        int frameRate = CraftlatroConfig.getInstance().getBrowserFrameRate();
        if (frameRate != appliedFrameRate) {
            try {
                browser.setWindowlessFrameRate(frameRate);
                appliedFrameRate = frameRate;
                LOGGER.info("Balatro browser paint rate set to {} fps", frameRate);
            } catch (RuntimeException failure) {
                LOGGER.warn("Could not set the Balatro browser paint rate.", failure);
            }
        }
        if (audioMuted) {
            browser.setAudioMuted(false);
            audioMuted = false;
        }
        browser.resize(width, height);
    }

    /** Keeps the page session alive but silent and cheap while no screen is rendering it. */
    public static synchronized void park() {
        if (browser == null || !browser.isValid() || parked) {
            return;
        }
        try {
            browser.setAudioMuted(true);
            audioMuted = true;
        } catch (RuntimeException failure) {
            LOGGER.warn("Could not mute the Balatro browser while parked.", failure);
        }
        try {
            browser.setWindowlessFrameRate(PARKED_FRAME_RATE);
            appliedFrameRate = PARKED_FRAME_RATE;
        } catch (RuntimeException failure) {
            LOGGER.warn("Could not slow the parked Balatro browser.", failure);
        }
        browser.resize(PARKED_WIDTH, PARKED_HEIGHT);
        parked = true;
    }

    /** Advances the paint-rate sample and returns the observed repaints per second. */
    public static synchronized int samplePaintRate() {
        long now = System.currentTimeMillis();
        if (windowStartMillis == 0) {
            windowStartMillis = now;
            return lastPaintRate;
        }
        if (now - windowStartMillis < SAMPLE_WINDOW_MILLIS) {
            return lastPaintRate;
        }
        long elapsed = Math.max(1L, now - windowStartMillis);
        int paints = paintsInWindow.getAndSet(0);
        lastPaintRate = (int) Math.round(paints * 1000.0 / elapsed);
        windowStartMillis = now;
        return lastPaintRate;
    }

    public static synchronized int lastPaintWidth() {
        return lastPaintWidth;
    }

    public static synchronized int lastPaintHeight() {
        return lastPaintHeight;
    }

    /** Drops the current session. The next {@link #acquire(String)} starts a fresh page. */
    public static synchronized void release() {
        releaseBrowser();
    }

    private static void addPaintCounter(RinkuBrowser target) {
        try {
            if (target instanceof CefBrowserOsr osrBrowser) {
                osrBrowser.addOnPaintListener(PAINT_COUNTER);
            }
        } catch (RuntimeException failure) {
            LOGGER.warn("Could not observe Balatro browser repaints; the stats overlay will read 0.", failure);
        }
    }

    private static void releaseBrowser() {
        if (browser != null) {
            if (browser.isValid() && Rinku.isInitialized()) {
                try {
                    if (browser instanceof CefBrowserOsr osrBrowser) {
                        osrBrowser.removeOnPaintListener(PAINT_COUNTER);
                    }
                } catch (RuntimeException ignored) {
                    // The listener is a no-op counter; a failed removal is harmless.
                }
                try {
                    browser.close();
                } catch (RuntimeException failure) {
                    LOGGER.warn("Failed to close Balatro browser cleanly.", failure);
                }
            }
            browser = null;
        }
        activeUrl = null;
        parked = false;
        appliedFrameRate = 0;
        audioMuted = false;
        paintsInWindow.set(0);
        lastPaintRate = 0;
        lastPaintWidth = 0;
        lastPaintHeight = 0;
        windowStartMillis = 0;
    }
}
