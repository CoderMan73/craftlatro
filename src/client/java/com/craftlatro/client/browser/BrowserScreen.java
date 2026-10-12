package com.craftlatro.client.browser;

import com.craftlatro.client.config.CraftlatroConfig;
import com.craftlatro.client.ui.CraftlatroConfigScreen;
import de.keksuccino.rinku.Rinku;
import de.keksuccino.rinku.RinkuBrowser;
import de.keksuccino.rinku.listeners.RinkuInitListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * The screen that shows Balatro through the embedded Chromium browser.
 *
 * <p>The viewport is a movable, resizable window ({@link BrowserWindow}) rather than a fullscreen
 * panel. In framed mode the world keeps rendering around the window and no backdrop covers it, so
 * the 3D world is visible at the same time as the game. While the window is fullscreen the old
 * behaviour is kept: an opaque backdrop over the whole GUI viewport.
 */
public class BrowserScreen extends Screen {

    public static final Component TITLE = Component.literal("Balatro");
    private static final int BACKDROP_COLOR = 0xFF0E1116;
    private static final int LABEL_COLOR = 0xFFE6EDF3;
    private static final int LOADING_BAR_HEIGHT = 2;
    private static final int LOADING_BAR_TRACK_COLOR = 0x55000000;
    private static final int LOADING_BAR_FILL_COLOR = 0xFF3BA8FF;

    private final String startUrl;
    private final BrowserWindow window;

    private RinkuBrowser browser;
    private boolean initScheduled = false;
    private boolean browserButtonDown = false;
    private int browserPixelWidth;
    private int browserPixelHeight;
    private int appliedPixelWidth;
    private int appliedPixelHeight;
    private boolean titleButtonsLabelFramed = true;
    private Button modeButton;
    private Button closeButton;
    private Button settingsButton;

    public BrowserScreen(String startUrl) {
        super(TITLE);
        this.startUrl = startUrl;
        this.window = new BrowserWindow(CraftlatroConfig.getInstance());
    }

    @Override
    protected void init() {
        super.init();

        window.restore(screenWidth(), screenHeight());

        modeButton = Button.builder(modeButtonLabel(), button ->
                        window.toggleFramed(screenWidth(), screenHeight()))
                .bounds(window.titleButtonX(BrowserWindow.TITLE_BUTTON_MODE_TOGGLE), window.titleButtonY(), BrowserWindow.TITLE_BUTTON_SIZE, BrowserWindow.TITLE_BUTTON_SIZE)
                .tooltip(Tooltip.create(modeButtonTooltip()))
                .build();
        addRenderableWidget(modeButton);

        closeButton = Button.builder(Component.literal("X"), button -> onClose())
                .bounds(window.titleButtonX(BrowserWindow.TITLE_BUTTON_CLOSE), window.titleButtonY(), BrowserWindow.TITLE_BUTTON_SIZE, BrowserWindow.TITLE_BUTTON_SIZE)
                .tooltip(Tooltip.create(Component.literal("Close the Balatro window")))
                .build();
        addRenderableWidget(closeButton);

        settingsButton = Button.builder(Component.literal("S"), button -> openSettings())
                .bounds(window.titleButtonX(BrowserWindow.TITLE_BUTTON_SETTINGS), window.titleButtonY(), BrowserWindow.TITLE_BUTTON_SIZE, BrowserWindow.TITLE_BUTTON_SIZE)
                .tooltip(Tooltip.create(Component.literal("Craftlatro settings")))
                .build();
        addRenderableWidget(settingsButton);

        createBrowserIfNotReady();
    }

    /**
     * The world keeps rendering behind the framed window either way; this only decides whether it
     * keeps ticking. A screen owns all input while it is open, so a running world can hurt the
     * player while they cannot react, which is why the default keeps the usual singleplayer pause.
     */
    @Override
    public boolean isPauseScreen() {
        return !CraftlatroConfig.getInstance().isWindowWorldRunning();
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
        browserButtonDown = false;
        appliedPixelWidth = 0;
        appliedPixelHeight = 0;
        resizeBrowser();
    }

    /**
     * Drops the current page session and reattaches. Used when the served package has been replaced,
     * since the old love.js page can still be holding the archive it was started with.
     */
    public void rebind() {
        BalatroBrowser.release();
        browser = null;
        browserButtonDown = false;
        initScheduled = false;
        appliedPixelWidth = 0;
        appliedPixelHeight = 0;
        createBrowserIfNotReady();
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        window.applyViewport(screenWidth(), screenHeight());
    }

    private int screenScale() {
        return minecraft.getWindow().getGuiScale();
    }

    private double renderScale() {
        return CraftlatroConfig.getInstance().getBrowserRenderScale();
    }

    /**
     * The logical size of the screen, read straight from the window. Screen fields only refresh when
     * the screen itself is rebuilt, which never happened when the player resized the game window, so
     * that is why the Balatro surface used to stay stuck at whatever size it started at.
     */
    private int screenWidth() {
        return minecraft.getWindow().getGuiScaledWidth();
    }

    private int screenHeight() {
        return minecraft.getWindow().getGuiScaledHeight();
    }

    private int browserPixelWidth() {
        return Math.max(1, (int) Math.round(window.interiorWidth() * screenScale() * renderScale()));
    }

    private int browserPixelHeight() {
        return Math.max(1, (int) Math.round(window.interiorHeight() * screenScale() * renderScale()));
    }

    private void resizeBrowser() {
        if (browser == null) {
            return;
        }
        browserPixelWidth = browserPixelWidth();
        browserPixelHeight = browserPixelHeight();
        if (browserPixelWidth == appliedPixelWidth && browserPixelHeight == appliedPixelHeight) {
            return;
        }
        appliedPixelWidth = browserPixelWidth;
        appliedPixelHeight = browserPixelHeight;
        BalatroBrowser.showViewport(browserPixelWidth, browserPixelHeight);
    }

    /** Re-checks the surface size every frame so window drags, resizes and GUI-scale changes land live. */
    private void syncViewport() {
        if (browser == null) {
            return;
        }
        window.applyViewport(screenWidth(), screenHeight());
        if (browserPixelWidth() != appliedPixelWidth || browserPixelHeight() != appliedPixelHeight) {
            resizeBrowser();
        }
    }

    private int toBrowserX(double guiX) {
        return (int) Math.round((guiX - window.interiorX()) * browserPixelWidth / (double) Math.max(1, window.interiorWidth()));
    }

    private int toBrowserY(double guiY) {
        return (int) Math.round((guiY - window.interiorY()) * browserPixelHeight / (double) Math.max(1, window.interiorHeight()));
    }

    private boolean isInBrowserBounds(double x, double y) {
        int bx = window.interiorX();
        int by = window.interiorY();
        return x >= bx && x < bx + window.interiorWidth() && y >= by && y < by + window.interiorHeight();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partial) {
        super.extractRenderState(guiGraphics, mouseX, mouseY, partial);

        syncViewport();
        BalatroBrowser.samplePaintRate();
        BalatroBrowser.observeFrameRate(minecraft.getFps());

        if (!window.isFramed()) {
            // A fullscreen window leaves nothing of the world to show, so keep the whole GUI
            // viewport covered while the page loads or the texture is repainting.
            guiGraphics.fill(0, 0, screenWidth(), screenHeight(), BACKDROP_COLOR);
        }

        window.render(guiGraphics, minecraft.font, TITLE);
        updateTitleButtons();

        int interiorX = window.interiorX();
        int interiorY = window.interiorY();
        int interiorWidth = window.interiorWidth();
        int interiorHeight = window.interiorHeight();

        // Chromium renders the whole viewport opaque, so the interior is covered rather than left
        // transparent while the page loads; the world stays visible everywhere else.
        guiGraphics.fill(interiorX, interiorY, interiorX + interiorWidth, interiorY + interiorHeight, BACKDROP_COLOR);

        if (browser != null && browser.isTextureReady()) {
            Identifier texture = browser.getTextureIdentifier();
            if (texture != null) {
                guiGraphics.blit(
                        RenderPipelines.GUI_TEXTURED,
                        texture,
                        interiorX, interiorY,
                        0f, 0f,
                        interiorWidth, interiorHeight,
                        interiorWidth, interiorHeight
                );
            }
        }

        renderLoadingIndicator(guiGraphics);
        renderStatsOverlay(guiGraphics, partial);
    }

    private void updateTitleButtons() {
        modeButton.setX(window.titleButtonX(BrowserWindow.TITLE_BUTTON_MODE_TOGGLE));
        modeButton.setY(window.titleButtonY());
        closeButton.setX(window.titleButtonX(BrowserWindow.TITLE_BUTTON_CLOSE));
        closeButton.setY(window.titleButtonY());
        settingsButton.setX(window.titleButtonX(BrowserWindow.TITLE_BUTTON_SETTINGS));
        settingsButton.setY(window.titleButtonY());

        if (window.isFramed() != titleButtonsLabelFramed) {
            titleButtonsLabelFramed = window.isFramed();
            modeButton.setMessage(modeButtonLabel());
            modeButton.setTooltip(Tooltip.create(modeButtonTooltip()));
        }
    }

    /**
     * The settings live in a Cloth Config screen rather than behind more chat commands, and the
     * Balatro window is where a player is when they want to change them.
     */
    private void openSettings() {
        minecraft.gui.setScreen(CraftlatroConfigScreen.create(this));
    }

    private Component modeButtonLabel() {
        return Component.literal(window.isFramed() ? "M" : "R");
    }

    private Component modeButtonTooltip() {
        return Component.literal(window.isFramed()
                ? "Expand the Balatro window to the full screen"
                : "Restore the Balatro window to a movable size");
    }

    private void renderStatsOverlay(GuiGraphicsExtractor guiGraphics, float partial) {
        if (!CraftlatroConfig.getInstance().isStatsOverlayEnabled()) {
            return;
        }

        List<String> lines = BalatroStats.reportLines();
        int widest = 0;
        for (String line : lines) {
            widest = Math.max(widest, minecraft.font.width(line));
        }

        int x = window.interiorX();
        int y = window.interiorY();
        int lineHeight = minecraft.font.lineHeight + 1;
        int panelHeight = lines.size() * lineHeight + 2;
        guiGraphics.fill(x - 2, y - 2, x + widest + 2, y + panelHeight, 0xA0000000);

        for (int index = 0; index < lines.size(); index++) {
            guiGraphics.text(minecraft.font, lines.get(index), x, y + index * lineHeight, LABEL_COLOR);
        }
    }

    private void renderLoadingIndicator(GuiGraphicsExtractor guiGraphics) {
        if (browser == null || !browser.isLoading()) {
            return;
        }

        int bx = window.interiorX();
        int by = window.interiorY();
        int bw = window.interiorWidth();
        guiGraphics.fill(bx, by, bx + bw, by + LOADING_BAR_HEIGHT, LOADING_BAR_TRACK_COLOR);

        int segmentWidth = Math.max(20, bw / 4);
        int travelRange = bw + segmentWidth;
        int animatedOffset = (int) ((Util.getMillis() / 6L) % travelRange) - segmentWidth;
        int segmentStart = Math.max(bx, bx + animatedOffset);
        int segmentEnd = Math.min(bx + bw, bx + animatedOffset + segmentWidth);
        if (segmentEnd > segmentStart) {
            guiGraphics.fill(segmentStart, by, segmentEnd, by + LOADING_BAR_HEIGHT, LOADING_BAR_FILL_COLOR);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        boolean handled = super.mouseClicked(event, isDoubleClick);
        if (handled) {
            return true;
        }

        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (isDoubleClick && window.isOverTitleBar(event.x(), event.y())) {
                window.toggleFramed(screenWidth(), screenHeight());
                return true;
            }
            int resizeMask = window.resizeMaskAt(event.x(), event.y());
            if (window.isResizeMask(resizeMask)) {
                window.beginResize(resizeMask, event.x(), event.y());
                return true;
            }
            if (window.isOverTitleBar(event.x(), event.y())) {
                window.beginMove(event.x(), event.y());
                return true;
            }
        }

        if (browser == null || !isInBrowserBounds(event.x(), event.y())) {
            return false;
        }

        browser.sendMousePress(toBrowserX(event.x()), toBrowserY(event.y()), event.button());
        browser.setFocus(true);
        browserButtonDown = true;
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (window.isDragging()) {
            window.dragTo(event.x(), event.y(), screenWidth(), screenHeight());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        window.endDrag();
        if (browserButtonDown && browser != null) {
            browser.sendMouseRelease(toBrowserX(event.x()), toBrowserY(event.y()), event.button());
            browserButtonDown = false;
        }
        return super.mouseReleased(event);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (browser != null && !window.isDragging() && isInBrowserBounds(mouseX, mouseY)) {
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

    /** One-line snapshot of what the overlay shows, for the log and the chat command. */
    public static String statsSnapshot() {
        Minecraft minecraft = Minecraft.getInstance();
        return "minecraft=" + minecraft.getFps() + "fps"
                + " chromiumPaints/s=" + BalatroBrowser.samplePaintRate()
                + " paintSource=" + BalatroBrowser.lastPaintWidth() + "x" + BalatroBrowser.lastPaintHeight()
                + " renderScale=" + CraftlatroConfig.getInstance().getBrowserRenderScale()
                + " targetFps=" + CraftlatroConfig.getInstance().getBrowserFrameRate()
                + " window=" + CraftlatroConfig.getInstance().isWindowFramed();
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
