package com.craftlatro.client.browser;

import com.craftlatro.client.config.CraftlatroConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The framed window that hosts the Balatro browser: geometry, chrome, hit-testing and drag math.
 *
 * <p>The surface used to fill the whole GUI viewport behind an opaque backdrop, so there was nothing
 * to grab and the 3D world was hidden. This class is the replacement window. It is moved by its
 * title bar, resized from any edge, expands to the full viewport, and leaves the world visible and
 * undimmed around itself while it is framed.
 *
 * <p>All coordinates are GUI-scaled pixels, which is the space the browser texture is blitted into.
 * The geometry is persisted per install through {@link CraftlatroConfig}; a GUI-scale change is
 * tolerated by clamping the stored rectangle into whatever viewport the screen opens with, and the
 * clamped rectangle is only written back when the player finishes a drag or toggles the mode, so a
 * drag does not write the config file every frame.
 */
public final class BrowserWindow {

    public static final int TITLE_BAR_HEIGHT = 18;
    public static final int RESIZE_BAND = 6;
    public static final int TITLE_BUTTON_SIZE = 16;
    public static final int FULLSCREEN_MARGIN = 8;
    private static final int MIN_WIDTH = 240;
    private static final int MIN_HEIGHT = 200;
    private static final int TITLE_BUTTON_COUNT = 2;
    private static final int TITLE_BUTTON_SPACING = 2;
    private static final int TITLE_TEXT_PADDING = 4;

    private static final int MODE_INDEX_TOGGLE = 0;
    private static final int MODE_INDEX_CLOSE = 1;

    private static final int EDGE_LEFT = 1;
    private static final int EDGE_RIGHT = 2;
    private static final int EDGE_BOTTOM = 4;

    private static final int FRAME_COLOR = 0xFF1B2027;
    private static final int FRAME_BORDER_COLOR = 0xFF05070A;
    private static final int TITLE_TEXT_COLOR = 0xFFE6EDF3;

    private final CraftlatroConfig config;

    private int x;
    private int y;
    private int width;
    private int height;
    private boolean framed = true;

    private DragMode dragMode = DragMode.NONE;
    private int dragMask;
    private int dragOffsetX;
    private int dragOffsetY;
    private int dragMouseX;
    private int dragMouseY;
    private int dragStartX;
    private int dragStartY;
    private int dragStartWidth;
    private int dragStartHeight;

    private enum DragMode {
        NONE,
        MOVE,
        RESIZE
    }

    public BrowserWindow(CraftlatroConfig config) {
        this.config = config;
    }

    /**
     * Loads the persisted window geometry and clamps it into the current viewport, falling back to a
     * centered default rectangle when nothing usable was stored.
     */
    public void restore(int screenWidth, int screenHeight) {
        framed = config.isWindowFramed();
        width = config.getWindowWidth();
        height = config.getWindowHeight();
        if (width <= 0 || height <= 0) {
            applyDefaultRect(screenWidth, screenHeight);
        } else {
            x = config.getWindowX();
            y = config.getWindowY();
        }
        clampIntoViewport(screenWidth, screenHeight);
        if (!framed) {
            fillViewport(screenWidth, screenHeight);
        }
    }

    private void applyDefaultRect(int screenWidth, int screenHeight) {
        int usableWidth = Math.max(1, screenWidth - FULLSCREEN_MARGIN * 2);
        int usableHeight = Math.max(1, screenHeight - FULLSCREEN_MARGIN * 2);
        width = Mth.clamp((int) Math.round(usableWidth * 0.82), MIN_WIDTH, usableWidth);
        height = Mth.clamp((int) Math.round(usableHeight * 0.82), MIN_HEIGHT, usableHeight);
        x = (screenWidth - width) / 2;
        y = (screenHeight - height) / 2;
    }

    private void fillViewport(int screenWidth, int screenHeight) {
        x = FULLSCREEN_MARGIN;
        y = FULLSCREEN_MARGIN;
        width = Math.max(MIN_WIDTH, screenWidth - FULLSCREEN_MARGIN * 2);
        height = Math.max(MIN_HEIGHT, screenHeight - FULLSCREEN_MARGIN * 2);
    }

    /** Keeps the window reachable when the GUI scale or the game window shrank underneath it. */
    public void clampIntoViewport(int screenWidth, int screenHeight) {
        width = Mth.clamp(width, MIN_WIDTH, Math.max(MIN_WIDTH, screenWidth));
        height = Mth.clamp(height, MIN_HEIGHT, Math.max(MIN_HEIGHT, screenHeight));
        x = Mth.clamp(x, 0, Math.max(0, screenWidth - width));
        y = Mth.clamp(y, 0, Math.max(0, screenHeight - height));
    }

    /** Re-applies the current mode to a new viewport size: reclamp a framed window, re-fill a fullscreen one. */
    public void applyViewport(int screenWidth, int screenHeight) {
        if (framed) {
            clampIntoViewport(screenWidth, screenHeight);
        } else {
            fillViewport(screenWidth, screenHeight);
        }
    }

    public boolean isFramed() {
        return framed;
    }

    public int interiorX() {
        return x;
    }

    public int interiorY() {
        return y + TITLE_BAR_HEIGHT;
    }

    public int interiorWidth() {
        return width;
    }

    public int interiorHeight() {
        return height - TITLE_BAR_HEIGHT;
    }

    public boolean isOverTitleBar(double mouseX, double mouseY) {
        return framed
                && mouseX >= x && mouseX < x + width
                && mouseY >= y && mouseY < y + TITLE_BAR_HEIGHT;
    }

    /**
     * Edge band a point can grab for resizing, or 0. The title bar is the move handle, so the top
     * edge is never a resize edge; that keeps moving and resizing from overlapping.
     */
    public int resizeMaskAt(double mouseX, double mouseY) {
        if (!framed) {
            return 0;
        }
        int mask = 0;
        if (mouseX >= x && mouseX < x + RESIZE_BAND) {
            mask |= EDGE_LEFT;
        } else if (mouseX >= x + width - RESIZE_BAND && mouseX < x + width) {
            mask |= EDGE_RIGHT;
        }
        if (mouseY >= y + height - RESIZE_BAND && mouseY < y + height) {
            mask |= EDGE_BOTTOM;
        }
        return mask;
    }

    public boolean isResizeMask(int mask) {
        return mask != 0;
    }

    public void beginMove(double mouseX, double mouseY) {
        dragMode = DragMode.MOVE;
        dragOffsetX = (int) Math.round(mouseX) - x;
        dragOffsetY = (int) Math.round(mouseY) - y;
    }

    public void beginResize(int mask, double mouseX, double mouseY) {
        dragMode = DragMode.RESIZE;
        dragMask = mask;
        dragMouseX = (int) Math.round(mouseX);
        dragMouseY = (int) Math.round(mouseY);
        dragStartX = x;
        dragStartY = y;
        dragStartWidth = width;
        dragStartHeight = height;
    }

    public void dragTo(double mouseX, double mouseY, int screenWidth, int screenHeight) {
        if (dragMode == DragMode.MOVE) {
            x = Mth.clamp((int) Math.round(mouseX) - dragOffsetX, 0, Math.max(0, screenWidth - width));
            y = Mth.clamp((int) Math.round(mouseY) - dragOffsetY, 0, Math.max(0, screenHeight - height));
            return;
        }
        if (dragMode == DragMode.RESIZE) {
            int mouseDeltaX = (int) Math.round(mouseX) - dragMouseX;
            int mouseDeltaY = (int) Math.round(mouseY) - dragMouseY;
            if ((dragMask & EDGE_RIGHT) != 0) {
                width = Mth.clamp(dragStartWidth + mouseDeltaX, MIN_WIDTH, Math.max(MIN_WIDTH, screenWidth - dragStartX));
            }
            if ((dragMask & EDGE_BOTTOM) != 0) {
                height = Mth.clamp(dragStartHeight + mouseDeltaY, MIN_HEIGHT, Math.max(MIN_HEIGHT, screenHeight - dragStartY));
            }
            if ((dragMask & EDGE_LEFT) != 0) {
                int maxShift = dragStartWidth - MIN_WIDTH;
                int shift = Mth.clamp(mouseDeltaX, 0, maxShift);
                x = dragStartX + shift;
                width = dragStartWidth - shift;
            }
        }
    }

    /** Finishes a drag and persists the window so the next open restores where it was left. */
    public void endDrag() {
        if (dragMode == DragMode.NONE) {
            return;
        }
        dragMode = DragMode.NONE;
        dragMask = 0;
        saveGeometry();
    }

    public boolean isDragging() {
        return dragMode != DragMode.NONE;
    }

    /** Switches between the persisted framed window and the full-viewport window. */
    public void toggleFramed(int screenWidth, int screenHeight) {
        framed = !framed;
        if (framed) {
            clampIntoViewport(screenWidth, screenHeight);
        } else {
            fillViewport(screenWidth, screenHeight);
        }
        config.setWindowMode(framed ? CraftlatroConfig.WINDOW_MODE_FRAMED : CraftlatroConfig.WINDOW_MODE_FULLSCREEN);
        saveGeometry();
    }

    private void saveGeometry() {
        config.setWindowX(x);
        config.setWindowY(y);
        config.setWindowWidth(width);
        config.setWindowHeight(height);
    }

    public void render(GuiGraphicsExtractor graphics, Font font, Component title) {
        graphics.fill(x, y, x + width, y + TITLE_BAR_HEIGHT, FRAME_COLOR);
        graphics.outline(x, y, x + width, y + height, FRAME_BORDER_COLOR);

        int titleY = y + (TITLE_BAR_HEIGHT - font.lineHeight) / 2 + 1;
        int titleLimit = titleButtonX(MODE_INDEX_CLOSE) - TITLE_TEXT_PADDING;
        int available = titleLimit - (x + TITLE_TEXT_PADDING);
        if (available > font.width(title)) {
            graphics.text(font, title, x + TITLE_TEXT_PADDING, titleY, TITLE_TEXT_COLOR);
        }
    }

    public int titleButtonX(int index) {
        int right = x + width - TITLE_BUTTON_SPACING - TITLE_BUTTON_SIZE;
        return right - (TITLE_BUTTON_COUNT - 1 - index) * (TITLE_BUTTON_SIZE + TITLE_BUTTON_SPACING);
    }

    public int titleButtonY() {
        return y + (TITLE_BAR_HEIGHT - TITLE_BUTTON_SIZE) / 2;
    }
}
