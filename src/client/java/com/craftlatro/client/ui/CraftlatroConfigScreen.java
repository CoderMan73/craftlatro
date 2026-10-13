package com.craftlatro.client.ui;

import com.craftlatro.client.config.CraftlatroConfig;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.impl.builders.SubCategoryBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The Cloth Config screen that edits {@link CraftlatroConfig}.
 *
 * <p>The values already persisted to {@code ~/.craftlatro/config/craftlatro.properties}; this class
 * is only the presentation layer, so every entry writes straight through to the config object instead
 * of staging values for a later save.
 *
 * <p><b>Applying a change while the Balatro screen is not open.</b> Where a value is only consumed
 * from the screen's own render loop, nothing applies it mid-session, so it takes effect the next
 * time the Balatro window opens. The tooltips say so, because silently doing nothing until a reopen
 * otherwise looks like a broken setting. Values the browser itself reads (stats overlay, render scale,
 * repaint rate) do apply to an open window immediately.
 *
 * <p><b>Game-package changes.</b> They are deliberately not editable here: swapping the Balatro
 * installation is still {@code /craftlatro pick}, which drops and rebuilds the browser session on its
 * own, and the settings on this screen never invalidate the running page.
 *
 * <p>The two categories follow the split the project is heading towards: everything that belongs to
 * the embedded browser sits under Browser, and everything that belongs to the Balatro integration
 * sits under Craftlatro. Each is subdivided into General and Performance, because the perf levers are
 * the ones a player tunes last and does not want scrolling past presentation to reach.
 */
public final class CraftlatroConfigScreen {

    /**
     * Tooltips are wrapped to this width before being handed to Cloth Config.
     *
     * <p>Cloth Config wraps entry tooltips to the screen width and positions them at the mouse, so an
     * unwrapped sentence renders as a single full-width band that covers the row being hovered and
     * makes the control underneath unreadable. Pre-wrapping to a tooltip width instead is what makes
     * it read as a tooltip box beside the cursor.
     */
    private static final int TOOLTIP_WIDTH = 220;

    private CraftlatroConfigScreen() {
    }

    /** Window presentation, mirroring the two modes {@link com.craftlatro.client.browser.BrowserWindow} has. */
    private enum WindowMode {
        FRAMED,
        FULLSCREEN
    }

    public static Screen create(Screen parent) {
        CraftlatroConfig config = CraftlatroConfig.getInstance();
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("config.craftlatro.title"))
                // Entries save themselves as they are edited; there is no staged save to confirm.
                .setDoesConfirmSave(false);
        ConfigEntryBuilder entries = builder.entryBuilder();

        addBrowserCategory(builder, entries, config);
        addCraftlatroCategory(builder, entries, config);

        return builder.build();
    }

    private static void addBrowserCategory(ConfigBuilder builder, ConfigEntryBuilder entries, CraftlatroConfig config) {
        ConfigCategory category = builder.getOrCreateCategory(Component.translatable("config.craftlatro.category.browser"));
        category.setDescription(new Component[]{
                Component.translatable("config.craftlatro.category.browser.description"),
        });

        SubCategoryBuilder general = entries.startSubCategory(
                Component.translatable("config.craftlatro.subcategory.general"))
                .setExpanded(true);
        general.add(windowModeEntry(entries, config));
        general.add(backgroundBlurEntry(entries, config));
        general.add(aspectLockedEntry(entries, config));
        category.addEntry(general.build());

        SubCategoryBuilder performance = entries.startSubCategory(
                Component.translatable("config.craftlatro.subcategory.performance"))
                .setExpanded(true);
        performance.add(renderScaleEntry(entries, config));
        performance.add(frameRateEntry(entries, config));
        performance.add(statsOverlayEntry(entries, config));
        category.addEntry(performance.build());
    }

    private static void addCraftlatroCategory(ConfigBuilder builder, ConfigEntryBuilder entries, CraftlatroConfig config) {
        ConfigCategory category = builder.getOrCreateCategory(Component.translatable("config.craftlatro.category.craftlatro"));
        category.setDescription(new Component[]{
                Component.translatable("config.craftlatro.category.craftlatro.description"),
        });

        SubCategoryBuilder general = entries.startSubCategory(
                Component.translatable("config.craftlatro.subcategory.general"))
                .setExpanded(true);
        general.add(gamePathEntry(entries));
        general.add(worldRunningEntry(entries, config));
        general.add(openToggleEntry(entries, config));
        category.addEntry(general.build());

        category.addEntry(entries.startTextDescription(Component.translatable("config.craftlatro.lovejs"))
                .setColor(0xFFB0B0B0)
                .build());
    }

    private static AbstractConfigListEntry windowModeEntry(ConfigEntryBuilder entries, CraftlatroConfig config) {
        return entries.startEnumSelector(
                        Component.translatable("config.craftlatro.windowMode"),
                        WindowMode.class,
                        currentWindowMode(config))
                .setEnumNameProvider(mode -> Component.translatable("config.craftlatro.windowMode." + labelSuffix(mode)))
                .setDefaultValue(currentWindowMode(config))
                .setSaveConsumer(newWindowMode(config))
                .setTooltip(tooltip("config.craftlatro.windowMode.tooltip"))
                .build();
    }

    private static AbstractConfigListEntry backgroundBlurEntry(ConfigEntryBuilder entries, CraftlatroConfig config) {
        return entries.startBooleanToggle(
                        Component.translatable("config.craftlatro.backgroundBlur"),
                        config.isWindowBackgroundBlur())
                .setDefaultValue(config.isWindowBackgroundBlur())
                .setSaveConsumer(config::setWindowBackgroundBlur)
                .setTooltip(tooltip("config.craftlatro.backgroundBlur.tooltip"))
                .build();
    }

    private static AbstractConfigListEntry aspectLockedEntry(ConfigEntryBuilder entries, CraftlatroConfig config) {
        return entries.startBooleanToggle(
                        Component.translatable("config.craftlatro.aspectLocked"),
                        config.isWindowAspectLocked())
                .setDefaultValue(config.isWindowAspectLocked())
                .setSaveConsumer(config::setWindowAspectLocked)
                .setTooltip(tooltip("config.craftlatro.aspectLocked.tooltip"))
                .build();
    }

    private static AbstractConfigListEntry renderScaleEntry(ConfigEntryBuilder entries, CraftlatroConfig config) {
        return entries.startIntSlider(
                        Component.translatable("config.craftlatro.renderScale"),
                        renderScalePercent(config),
                        (int) Math.round(CraftlatroConfig.MIN_BROWSER_RENDER_SCALE * 100),
                        (int) Math.round(CraftlatroConfig.MAX_BROWSER_RENDER_SCALE * 100))
                .setDefaultValue(renderScalePercent(config))
                .setSaveConsumer(percent -> config.setBrowserRenderScale(percent / 100.0))
                .setTooltip(tooltip("config.craftlatro.renderScale.tooltip"))
                .build();
    }

    private static AbstractConfigListEntry frameRateEntry(ConfigEntryBuilder entries, CraftlatroConfig config) {
        return entries.startIntSlider(
                        Component.translatable("config.craftlatro.frameRate"),
                        config.getBrowserFrameRate(),
                        CraftlatroConfig.MIN_BROWSER_FRAME_RATE,
                        CraftlatroConfig.MAX_BROWSER_FRAME_RATE)
                .setDefaultValue(config.getBrowserFrameRate())
                .setSaveConsumer(config::setBrowserFrameRate)
                .setTooltip(tooltip("config.craftlatro.frameRate.tooltip"))
                .build();
    }

    private static AbstractConfigListEntry statsOverlayEntry(ConfigEntryBuilder entries, CraftlatroConfig config) {
        return entries.startBooleanToggle(
                        Component.translatable("config.craftlatro.statsOverlay"),
                        config.isStatsOverlayEnabled())
                .setDefaultValue(config.isStatsOverlayEnabled())
                .setSaveConsumer(config::setStatsOverlayEnabled)
                .setTooltip(tooltip("config.craftlatro.statsOverlay.tooltip"))
                .build();
    }

    private static AbstractConfigListEntry gamePathEntry(ConfigEntryBuilder entries) {
        return entries.startTextDescription(Component.translatable("config.craftlatro.gamePath"))
                .setColor(0xFFB0B0B0)
                .build();
    }

    private static AbstractConfigListEntry worldRunningEntry(ConfigEntryBuilder entries, CraftlatroConfig config) {
        return entries.startBooleanToggle(
                        Component.translatable("config.craftlatro.worldRunning"),
                        config.isWindowWorldRunning())
                .setDefaultValue(config.isWindowWorldRunning())
                .setSaveConsumer(config::setWindowWorldRunning)
                .setTooltip(tooltip("config.craftlatro.worldRunning.tooltip"))
                .build();
    }

    private static AbstractConfigListEntry openToggleEntry(ConfigEntryBuilder entries, CraftlatroConfig config) {
        return entries.startBooleanToggle(
                        Component.translatable("config.craftlatro.openToggle"),
                        config.isOpenBalatroToggle())
                .setDefaultValue(config.isOpenBalatroToggle())
                .setSaveConsumer(config::setOpenBalatroToggle)
                .setTooltip(tooltip("config.craftlatro.openToggle.tooltip"))
                .build();
    }

    /**
     * A tooltip as pre-wrapped lines, so Cloth Config renders a narrow tooltip box instead of a
     * full-width band that covers the hovered row.
     */
    private static Component[] tooltip(String translationKey) {
        return wrap(Component.translatable(translationKey), TOOLTIP_WIDTH);
    }

    /**
     * Splits a component into lines no wider than {@code maxWidth} pixels.
     *
     * <p>Cloth Config re-splits every line it is given to the screen width, so a line that already
     * fits is passed through unchanged and the tooltip ends up as wide as its longest line rather
     * than as wide as the screen.
     */
    private static Component[] wrap(Component message, int maxWidth) {
        Font font = Minecraft.getInstance().font;
        List<Component> lines = new ArrayList<>();
        for (FormattedCharSequence sequence : font.split(message, maxWidth)) {
            StringBuilder text = new StringBuilder();
            sequence.accept((index, style, codePoint) -> {
                text.appendCodePoint(codePoint);
                return true;
            });
            lines.add(Component.literal(text.toString()));
        }
        return lines.toArray(Component[]::new);
    }

    private static WindowMode currentWindowMode(CraftlatroConfig config) {
        return config.isWindowFramed() ? WindowMode.FRAMED : WindowMode.FULLSCREEN;
    }

    private static Consumer<WindowMode> newWindowMode(CraftlatroConfig config) {
        return mode -> config.setWindowMode(mode == WindowMode.FRAMED
                ? CraftlatroConfig.WINDOW_MODE_FRAMED
                : CraftlatroConfig.WINDOW_MODE_FULLSCREEN);
    }

    private static String labelSuffix(Enum<?> mode) {
        return mode.name().toLowerCase(Locale.ROOT);
    }

    private static int renderScalePercent(CraftlatroConfig config) {
        return (int) Math.round(config.getBrowserRenderScale() * 100);
    }
}
