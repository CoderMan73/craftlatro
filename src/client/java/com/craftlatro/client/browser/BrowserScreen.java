package com.craftlatro.client.browser;

import com.craftlatro.client.config.CraftlatroConfig;
import de.keksuccino.rinku.Rinku;
import de.keksuccino.rinku.RinkuBrowser;
import de.keksuccino.rinku.listeners.RinkuInitListener;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

public class BrowserScreen extends Screen {

    public static final Component TITLE = Component.literal("Balatro");
    private static final int FRAME_MARGIN = 8;
    private static final int CLOSE_BUTTON_WIDTH = 20;
    private static final int CLOSE_BUTTON_HEIGHT = 20;
    private static final int LOADING_BAR_HEIGHT = 2;
    private static final int LOADING_BAR_TRACK_COLOR = 0x55000000;
    private static final int LOADING_BAR_FILL_COLOR = 0xFF3BA8FF;
    private static final int BACKDROP_COLOR = 0xFF0E1116;

    private final String startUrl;
    private RinkuBrowser browser;
    private boolean initScheduled = false;
    private int browserPixelWidth;
    private int browserPixelHeight;
    private Button closeButton;

    public BrowserScreen(String startUrl) {
        super(TITLE);
        this.startUrl = startUrl;
    }

    @Override
    protected void init() {
        super.init();

        int buttonX = width - FRAME_MARGIN - CLOSE_BUTTON_WIDTH;
        int buttonY = FRAME_MARGIN;
        closeButton = addRenderableWidget(
                Button.builder(Component.literal("X"), button -> onClose())
                        .bounds(buttonX, buttonY, CLOSE_BUTTON_WIDTH, CLOSE_BUTTON_HEIGHT)
                        .build()
        );

        createBrowserIfNotReady();
    }

    private void createBrowserIfNotReady() {
        if (browser != null) {
            return;
        }

        if (Rinku.isInitialized()) {
            attachBrowser();
        } else if (Rinku.isInitializationAllowed() && !initScheduled) {
            initScheduled = true;
            Rinku.scheduleForInit(new RinkuInitListener() {
                @Override
                public void onInit(boolean successful) {
                    if (successful) {
                        minecraft.execute(() -> {
                            if (minecraft.gui.screen() instanceof BrowserScreen && browser == null) {
                                attachBrowser();
                            }
                        });
                    }
                }
            });
        }
    }

    private void attachBrowser() {
        browser = BalatroBrowser.acquire(startUrl);
        if (browser == null) {
            return;
        }
        resizeBrowser();
    }

    /**
     * Drops the current page session and reattaches. Used when the served package has been replaced,
     * since the old love.js page can still be holding the archive it was started with.
     */
    public void rebind() {
        BalatroBrowser.release();
        browser = null;
        initScheduled = false;
        createBrowserIfNotReady();
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        resizeBrowser();
    }

    private int screenScale() {
        return minecraft.getWindow().getGuiScale();
    }

    private double renderScale() {
        return CraftlatroConfig.getInstance().getBrowserRenderScale();
    }

    private int browserPixelWidth() {
        return Math.max(1, (int) Math.round(getBrowserWidth() * screenScale() * renderScale()));
    }

    private int browserPixelHeight() {
        return Math.max(1, (int) Math.round(getBrowserHeight() * screenScale() * renderScale()));
    }

    private void resizeBrowser() {
        if (browser == null) {
            return;
        }
        browserPixelWidth = browserPixelWidth();
        browserPixelHeight = browserPixelHeight();
        BalatroBrowser.showViewport(browserPixelWidth, browserPixelHeight);
    }

    private int toBrowserX(double guiX) {
        return (int) Math.round((guiX - getBrowserX()) * browserPixelWidth / (double) Math.max(1, getBrowserWidth()));
    }

    private int toBrowserY(double guiY) {
        return (int) Math.round((guiY - getBrowserY()) * browserPixelHeight / (double) Math.max(1, getBrowserHeight()));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partial) {
        super.extractRenderState(guiGraphics, mouseX, mouseY, partial);

        // Chromium renders the whole viewport opaque, so cover the 3D world behind it instead of
        // letting it show through while the page loads or the texture is repainting.
        guiGraphics.fill(0, 0, width, height, BACKDROP_COLOR);

        if (browser != null && browser.isTextureReady()) {
            Identifier texture = browser.getTextureIdentifier();
            if (texture != null) {
                int bx = getBrowserX();
                int by = getBrowserY();
                int bw = getBrowserWidth();
                int bh = getBrowserHeight();
                guiGraphics.blit(
                        RenderPipelines.GUI_TEXTURED,
                        texture,
                        bx, by,
                        0f, 0f,
                        bw, bh,
                        bw, bh
                );
            }
        }

        renderLoadingIndicator(guiGraphics);
    }

    private void renderLoadingIndicator(GuiGraphicsExtractor guiGraphics) {
        if (browser == null || !browser.isLoading()) {
            return;
        }

        int bx = getBrowserX();
        int by = getBrowserY() - LOADING_BAR_HEIGHT - 2;
        int bw = getBrowserWidth();
        guiGraphics.fill(bx, by, bx + bw, by + LOADING_BAR_HEIGHT, LOADING_BAR_TRACK_COLOR);

        if (closeButton != null) {
            int segmentWidth = Math.max(20, bw / 4);
            int travelRange = bw + segmentWidth;
            int animatedOffset = (int) ((Util.getMillis() / 6L) % travelRange) - segmentWidth;
            int segmentStart = Math.max(bx, bx + animatedOffset);
            int segmentEnd = Math.min(bx + bw, bx + animatedOffset + segmentWidth);
            if (segmentEnd > segmentStart) {
                guiGraphics.fill(segmentStart, by, segmentEnd, by + LOADING_BAR_HEIGHT, LOADING_BAR_FILL_COLOR);
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        boolean handled = super.mouseClicked(event, isDoubleClick);
        if (handled) {
            return true;
        }

        if (browser == null || !isInBrowserBounds(event.x(), event.y())) {
            return false;
        }

        browser.sendMousePress(toBrowserX(event.x()), toBrowserY(event.y()), event.button());
        browser.setFocus(true);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (browser != null) {
            browser.sendMouseRelease(toBrowserX(event.x()), toBrowserY(event.y()), event.button());
        }
        return super.mouseReleased(event);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (browser != null && isInBrowserBounds(mouseX, mouseY)) {
            browser.sendMouseMove(toBrowserX(mouseX), toBrowserY(mouseY));
        }
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        boolean handled = super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        if (handled) {
            return true;
        }

        if (browser == null || !isInBrowserBounds(mouseX, mouseY)) {
            return false;
        }

        browser.sendMouseWheel(toBrowserX(mouseX), toBrowserY(mouseY), scrollY, 0);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }

        if (super.keyPressed(event)) {
            return true;
        }

        if (browser != null && isFocused()) {
            browser.sendKeyPress(event.key(), event.scancode(), event.modifiers());
            browser.setFocus(true);
            return true;
        }

        return false;
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (browser != null) {
            browser.sendKeyRelease(event.key(), event.scancode(), event.modifiers());
        }
        return super.keyReleased(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (super.charTyped(event)) {
            return true;
        }

        if (browser != null) {
            browser.sendKeyTyped((char) event.codepoint(), 0);
            browser.setFocus(true);
            return true;
        }

        return false;
    }

    private boolean isInBrowserBounds(double x, double y) {
        int bx = getBrowserX();
        int by = getBrowserY();
        int bw = getBrowserWidth();
        int bh = getBrowserHeight();
        return x >= bx && x < bx + bw && y >= by && y < by + bh;
    }

    private int getBrowserX() {
        return FRAME_MARGIN;
    }

    private int getBrowserY() {
        return FRAME_MARGIN;
    }

    private int getBrowserWidth() {
        return Math.max(1, width - FRAME_MARGIN * 2);
    }

    private int getBrowserHeight() {
        return Math.max(1, height - FRAME_MARGIN * 2);
    }

    @Override
    public void tick() {
        super.tick();
        createBrowserIfNotReady();
    }

    /**
     * Detaches the browser without destroying it. The love.js page keeps running so the next open is
     * instant, at a small offscreen size so no frames are spent on an unseen page.
     */
    @Override
    public void onClose() {
        BalatroBrowser.park();
        super.onClose();
    }
}
