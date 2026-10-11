package com.craftlatro.client.browser;

import de.keksuccino.rinku.Rinku;
import de.keksuccino.rinku.RinkuBrowser;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class BrowserScreen extends Screen {

    public static final Component TITLE = Component.literal("Balatro");
    private static final int FRAME_MARGIN = 8;
    private static final int CLOSE_BUTTON_WIDTH = 20;
    private static final int CLOSE_BUTTON_HEIGHT = 20;

    private final String startUrl;
    private RinkuBrowser browser;
    private Button closeButton;

    public BrowserScreen(String startUrl) {
        super(TITLE);
        this.startUrl = startUrl;
    }

    @Override
    protected void init() {
        super.init();

        if (browser == null && Rinku.isInitialized()) {
            browser = Rinku.createBrowser(startUrl, false);
            browser.useBrowserControls(false);
        }

        int buttonX = width - FRAME_MARGIN - CLOSE_BUTTON_WIDTH;
        int buttonY = FRAME_MARGIN;
        closeButton = addRenderableWidget(
                Button.builder(Component.literal("X"), button -> onClose())
                        .bounds(buttonX, buttonY, CLOSE_BUTTON_WIDTH, CLOSE_BUTTON_HEIGHT)
                        .build()
        );
    }

    @Override
    public void tick() {
        super.tick();
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
    public void resize(int width, int height) {
        super.resize(width, height);
        resizeBrowser();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partial) {
        super.extractRenderState(guiGraphics, mouseX, mouseY, partial);

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
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        boolean handled = super.mouseClicked(event, isDoubleClick);
        if (handled) {
            return true;
        }

        if (browser != null && isInBrowserBounds(event.x(), event.y())) {
            int scale = minecraft.getWindow().getGuiScale();
            browser.sendMousePress(
                    (int) ((event.x() - getBrowserX()) * scale),
                    (int) ((event.y() - getBrowserY()) * scale),
                    event.button()
            );
            browser.setFocus(true);
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (browser != null) {
            int scale = minecraft.getWindow().getGuiScale();
            browser.sendMouseRelease(
                    (int) ((event.x() - getBrowserX()) * scale),
                    (int) ((event.y() - getBrowserY()) * scale),
                    event.button()
            );
        }
        return super.mouseReleased(event);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (browser != null && isInBrowserBounds(mouseX, mouseY)) {
            int scale = minecraft.getWindow().getGuiScale();
            browser.sendMouseMove(
                    (int) ((mouseX - getBrowserX()) * scale),
                    (int) ((mouseY - getBrowserY()) * scale)
            );
        }
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        boolean handled = super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        if (handled) {
            return true;
        }

        if (browser != null && isInBrowserBounds(mouseX, mouseY)) {
            int scale = minecraft.getWindow().getGuiScale();
            browser.sendMouseWheel(
                    (int) ((mouseX - getBrowserX()) * scale),
                    (int) ((mouseY - getBrowserY()) * scale),
                    scrollY,
                    0
            );
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }

        if (browser != null && isFocused()) {
            browser.sendKeyPress(event.key(), event.scancode(), event.modifiers());
            browser.setFocus(true);
            return true;
        }

        return super.keyPressed(event);
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
        if (browser != null) {
                browser.sendKeyTyped((char) event.codepoint(), 0);
            browser.setFocus(true);
            return true;
        }
        return super.charTyped(event);
    }

    private boolean isInBrowserBounds(double x, double y) {
        int bx = getBrowserX();
        int by = getBrowserY();
        int bw = getBrowserWidth();
        int bh = getBrowserHeight();
        return x >= bx && y >= by && x < bx + bw && y < by + bh;
    }

    private void resizeBrowser() {
        if (browser != null) {
            int scale = minecraft.getWindow().getGuiScale();
            browser.resize(getBrowserWidth() * scale, getBrowserHeight() * scale);
        }
    }

    @Override
    public void onClose() {
        if (browser != null && Rinku.isInitialized()) {
            browser.close();
        }
        super.onClose();
    }
}
