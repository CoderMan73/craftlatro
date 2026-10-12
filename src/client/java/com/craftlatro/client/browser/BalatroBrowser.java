package com.craftlatro.client.browser;

import de.keksuccino.rinku.Rinku;
import de.keksuccino.rinku.RinkuBrowser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owns the single Chromium browser that shows the Balatro love.js page.
 *
 * <p>The browser deliberately outlives {@link BrowserScreen}. Closing the screen only detaches it;
 * the page keeps running so reopening the game is instant instead of re-unzipping the {@code .love}
 * archive. While no screen is showing, the surface is parked at a tiny footprint because Rinku keeps
 * pumping pending paint frames for every live browser on the Minecraft render thread, and an
 * unshown full-screen surface would cost frame time while playing.
 */
public final class BalatroBrowser {

    private static final Logger LOGGER = LoggerFactory.getLogger("craftlatro");
    private static final int PARKED_WIDTH = 320;
    private static final int PARKED_HEIGHT = 180;

    private static RinkuBrowser browser;
    private static String activeUrl;
    private static boolean parked;

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
            return browser;
        }
        if (!url.equals(activeUrl)) {
            LOGGER.info("Balatro package changed; reloading browser from {}", url);
            browser.loadURL(url);
            activeUrl = url;
        }
        return browser;
    }

    /** Applies the on-screen resolution and unparks the surface if it was parked. */
    public static synchronized void showViewport(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        parked = false;
        if (browser == null || !browser.isValid()) {
            return;
        }
        browser.resize(width, height);
    }

    /** Keeps the page session alive at a cheap footprint while no screen is rendering it. */
    public static synchronized void park() {
        if (browser == null || !browser.isValid() || parked) {
            return;
        }
        browser.resize(PARKED_WIDTH, PARKED_HEIGHT);
        parked = true;
    }

    /** Drops the current session. The next {@link #acquire(String)} starts a fresh page. */
    public static synchronized void release() {
        releaseBrowser();
    }

    private static void releaseBrowser() {
        if (browser != null) {
            if (browser.isValid() && Rinku.isInitialized()) {
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
    }
}
