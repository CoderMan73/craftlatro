package com.craftlatro.client.ui;

import com.craftlatro.client.config.CraftlatroConfig;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * The Cloth Config screen that edits {@link CraftlatroConfig}.
 *
 * <p>The values already persisted to {@code ~/.craftlatro/config/craftlatro.properties}; this class
 * is only the presentation layer, so every entry writes straight through to the config object instead
 * of staging values for a later save. Two consequences drive the copy on this screen:
 *
 * <p><b>Applying a change while the Balatro screen is not open.</b> Where a value is only consumed
 * from the screen's own render loop, nothing applies it mid-session, so it takes effect the next
 * time the Balatro window opens. The performance entries say so in their tooltips, because silently
 * doing nothing until a reopen otherwise looks like a broken setting. Values the browser itself
 * reads (stats overlay, render scale, repaint rate) do apply to an open window immediately.
 *
 * <p><b>Game-package changes.</b> They are not editable here: swapping the Balatro installation is
 * still {@code /craftlatro pick}, which drops and rebuilds the browser session on its own. The
 * settings on this screen never invalidate the running page.
 */
public final class CraftlatroConfigScreen {

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

        addBalatroCategory(builder, entries, config);
        addPerformanceCategory(builder, entries, config);

        return builder.build();
    }

    private static void addBalatroCategory(ConfigBuilder builder, ConfigEntryBuilder entries, CraftlatroConfig config) {
        ConfigCategory category = builder.getOrCreateCategory(Component.translatable("config.craftlatro.category.balatro"));
        category.setDescription(new Component[]{
                Component.translatable("config.craftlatro.category.balatro.description"),
        });

        category.addEntry(entries.startEnumSelector(
                                Component.translatable("config.craftlatro.windowMode"),
                                WindowMode.class,
                                currentWindowMode(config))
                        .setEnumNameProvider(mode -> Component.translatable("config.craftlatro.windowMode." + labelSuffix(mode)))
                        .setDefaultValue(currentWindowMode(config))
                        .setSaveConsumer(newWindowMode(config))
                        .setTooltip(Component.translatable("config.craftlatro.windowMode.tooltip"))
                        .build());

        category.addEntry(entries.startBooleanToggle(
                                Component.translatable("config.craftlatro.worldRunning"),
                                config.isWindowWorldRunning())
                        .setDefaultValue(config.isWindowWorldRunning())
                        .setSaveConsumer(config::setWindowWorldRunning)
                        .setTooltip(Component.translatable("config.craftlatro.worldRunning.tooltip"))
                        .build());

        category.addEntry(entries.startIntSlider(
                                Component.translatable("config.craftlatro.backgroundStrength"),
                                config.getWindowBackgroundStrength(),
                                CraftlatroConfig.MIN_WINDOW_BACKGROUND_STRENGTH,
                                CraftlatroConfig.MAX_WINDOW_BACKGROUND_STRENGTH)
                        .setDefaultValue(config.getWindowBackgroundStrength())
                        .setSaveConsumer(config::setWindowBackgroundStrength)
                        .setTooltip(Component.translatable("config.craftlatro.backgroundStrength.tooltip"))
                        .build());

        category.addEntry(entries.startTextDescription(Component.translatable("config.craftlatro.gamePath"))
                .setColor(0xFFB0B0B0)
                .build());

        category.addEntry(entries.startTextDescription(Component.translatable("config.craftlatro.commands"))
                .setColor(0xFFB0B0B0)
                .build());

        category.addEntry(entries.startTextDescription(Component.translatable("config.craftlatro.experimental"))
                .setColor(0xFFB0B0B0)
                .build());
    }

    private static void addPerformanceCategory(ConfigBuilder builder, ConfigEntryBuilder entries, CraftlatroConfig config) {
        ConfigCategory category = builder.getOrCreateCategory(Component.translatable("config.craftlatro.category.performance"));
        category.setDescription(new Component[]{
                Component.translatable("config.craftlatro.category.performance.description"),
        });

        category.addEntry(entries.startIntSlider(
                                Component.translatable("config.craftlatro.renderScale"),
                                renderScalePercent(config),
                                (int) Math.round(CraftlatroConfig.MIN_BROWSER_RENDER_SCALE * 100),
                                (int) Math.round(CraftlatroConfig.MAX_BROWSER_RENDER_SCALE * 100))
                        .setDefaultValue(renderScalePercent(config))
                        .setSaveConsumer(percent -> config.setBrowserRenderScale(percent / 100.0))
                        .setTooltip(Component.translatable("config.craftlatro.renderScale.tooltip"))
                        .build());

        category.addEntry(entries.startIntSlider(
                                Component.translatable("config.craftlatro.frameRate"),
                                config.getBrowserFrameRate(),
                                CraftlatroConfig.MIN_BROWSER_FRAME_RATE,
                                CraftlatroConfig.MAX_BROWSER_FRAME_RATE)
                        .setDefaultValue(config.getBrowserFrameRate())
                        .setSaveConsumer(config::setBrowserFrameRate)
                        .setTooltip(Component.translatable("config.craftlatro.frameRate.tooltip"))
                        .build());

        category.addEntry(entries.startBooleanToggle(
                                Component.translatable("config.craftlatro.statsOverlay"),
                                config.isStatsOverlayEnabled())
                        .setDefaultValue(config.isStatsOverlayEnabled())
                        .setSaveConsumer(config::setStatsOverlayEnabled)
                        .setTooltip(Component.translatable("config.craftlatro.statsOverlay.tooltip"))
                        .build());

        category.addEntry(entries.startBooleanToggle(
                                Component.translatable("config.craftlatro.aspectLocked"),
                                config.isWindowAspectLocked())
                        .setDefaultValue(config.isWindowAspectLocked())
                        .setSaveConsumer(config::setWindowAspectLocked)
                        .setTooltip(Component.translatable("config.craftlatro.aspectLocked.tooltip"))
                        .build());
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
