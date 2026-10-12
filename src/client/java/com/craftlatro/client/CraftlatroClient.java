package com.craftlatro.client;

import com.craftlatro.client.browser.BalatroBrowser;
import com.craftlatro.client.browser.BalatroStats;
import com.craftlatro.client.browser.BrowserScreen;
import com.craftlatro.client.config.CraftlatroConfig;
import com.craftlatro.client.extractor.GameLauncher;
import com.craftlatro.client.ui.FidelityWarningScreen;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Environment(EnvType.CLIENT)
public class CraftlatroClient implements ClientModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("craftlatro");

    public static final String KEY_OPEN_BALATRO = "key.craftlatro.open_balatro";
    public static final String KEY_SELECT_EXE = "key.craftlatro.select_exe";

    private static final long STATS_LOG_INTERVAL_MILLIS = 10_000L;

    private static KeyMapping openBalatroKey;
    private static KeyMapping selectExeKey;
    private boolean previousGameLoaded = false;
    private boolean wasInWorld = false;
    private long lastStatsLogMillis;

    @Override
    public void onInitializeClient() {
        openBalatroKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                KEY_OPEN_BALATRO,
                InputConstants.KEY_B,
                KeyMapping.Category.MISC
        ));

        selectExeKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                KEY_SELECT_EXE,
                InputConstants.KEY_F9,
                KeyMapping.Category.MISC
        ));

        ClientTickEvents.START_CLIENT_TICK.register(this::onClientTick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(this::onClientStopping);
        registerCommands();
    }

    private void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> {
            dispatcher.register(ClientCommands.literal("craftlatro")
                    .then(ClientCommands.literal("open").executes(context -> {
                        openBrowserIfReady();
                        return 1;
                    }))
                    .then(ClientCommands.literal("pick").executes(context -> {
                        GameLauncher.promptForBalatroExe(Minecraft.getInstance());
                        return 1;
                    }))
                    .then(ClientCommands.literal("warn").executes(context -> {
                        showFidelityWarning();
                        return 1;
                    }))
                    .then(ClientCommands.literal("render").executes(context -> {
                        reportRenderScale();
                        return 1;
                    }).then(ClientCommands.argument("scale", DoubleArgumentType.doubleArg(
                                    CraftlatroConfig.MIN_BROWSER_RENDER_SCALE,
                                    CraftlatroConfig.MAX_BROWSER_RENDER_SCALE))
                            .executes(context -> {
                                setRenderScale(DoubleArgumentType.getDouble(context, "scale"));
                                return 1;
                            })))
                    .then(ClientCommands.literal("reload").executes(context -> {
                        reloadBrowser();
                        return 1;
                    }))
                    .then(ClientCommands.literal("stats").executes(context -> {
                        toggleStatsOverlay();
                        return 1;
                    }))
                    .then(ClientCommands.literal("fps")
                            .then(ClientCommands.argument("rate", IntegerArgumentType.integer(
                                            CraftlatroConfig.MIN_BROWSER_FRAME_RATE,
                                            CraftlatroConfig.MAX_BROWSER_FRAME_RATE))
                                    .executes(context -> {
                                        setBrowserFrameRate(IntegerArgumentType.getInteger(context, "rate"));
                                        return 1;
                                    })))
                    .then(ClientCommands.literal("load")
                            .then(ClientCommands.argument("path", StringArgumentType.string())
                                    .executes(context -> {
                                        String path = StringArgumentType.getString(context, "path");
                                        loadGameFromPath(path);
                                        return 1;
                                    })))
            );
        });
    }

    private void reportRenderScale() {
        CraftlatroConfig config = CraftlatroConfig.getInstance();
        sendStatus("Player render scale: "
                + config.getBrowserRenderScale()
                + " (chromium offscreen resolution as a fraction of the screen)");
    }

    private void setRenderScale(double scale) {
        CraftlatroConfig.getInstance().setBrowserRenderScale(scale);
        sendStatus("Player render scale set to " + CraftlatroConfig.getInstance().getBrowserRenderScale());
    }

    private void reloadBrowser() {
        // A stale love.js package stays cached inside Chromium, so the page is dropped entirely rather
        // than reloaded when the user asks for a fresh copy.
        BalatroBrowser.release();
        sendStatus("Balatro browser reset; it will reload on the next open.");
    }

    private void toggleStatsOverlay() {
        CraftlatroConfig config = CraftlatroConfig.getInstance();
        boolean enabled = !config.isStatsOverlayEnabled();
        config.setStatsOverlayEnabled(enabled);
        String report = BalatroStats.buildReport();
        LOGGER.info("{}", report);
        sendStatus("craftlatro stats " + (enabled ? "overlay on" : "overlay off")
                + " - report copied to clipboard"
                + (BalatroStats.copyToClipboard(report) ? "" : " (clipboard unavailable)")
                + ". Also logged to the console.");
    }

    private void logPeriodicStats(Minecraft client) {
        if (!CraftlatroConfig.getInstance().isStatsOverlayEnabled() || client.gui.screen() == null) {
            lastStatsLogMillis = 0;
            return;
        }
        long now = System.currentTimeMillis();
        if (lastStatsLogMillis == 0) {
            lastStatsLogMillis = now;
            return;
        }
        if (now - lastStatsLogMillis < STATS_LOG_INTERVAL_MILLIS) {
            return;
        }
        lastStatsLogMillis = now;
        LOGGER.info("{}", BalatroStats.buildReport());
    }

    private void setBrowserFrameRate(int rate) {
        CraftlatroConfig.getInstance().setBrowserFrameRate(rate);
        sendStatus("Balatro repaint target set to " + rate + " fps");
    }

    private void sendStatus(String message) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.sendSystemMessage(Component.literal(message));
        }
    }

    private void onClientTick(Minecraft client) {
        CraftlatroConfig config = CraftlatroConfig.getInstance();

        // Auto-load game if data already exists from a previous run
        if (!GameLauncher.isReady() && !GameLauncher.isExtractionInProgress()) {
            GameLauncher.tryAutoLoad(client);
        }

        if (previousGameLoaded && !GameLauncher.isReady()) {
            BalatroBrowser.release();
            if (client.gui.screen() instanceof BrowserScreen) {
                client.gui.setScreen(null);
            }
        }
        previousGameLoaded = GameLauncher.isReady();

        if (!config.getWarningAcknowledged() && client.player != null && client.gui.screen() == null) {
            if (!isGameLoaded()) {
                Minecraft.getInstance().execute(this::showFidelityWarning);
            }
        }

        // Balatro keeps its Lua session and its music while the screen is closed so reopening is
        // instant, but leaving a world is a hard stop: nothing should be audible or running between
        // worlds, so the page is dropped outright.
        if (wasInWorld && client.level == null) {
            BalatroBrowser.release();
            if (client.gui.screen() instanceof BrowserScreen) {
                client.gui.setScreen(null);
            }
        }
        wasInWorld = client.level != null;

        if (openBalatroKey.isDown()) {
            if (!isGameLoaded()) {
                sendChatLinkMessage(client);
            } else {
                openBrowserScreen(client);
            }
        }

        if (selectExeKey.isDown()) {
            GameLauncher.promptForBalatroExe(client);
        }

        logPeriodicStats(client);
    }

    private boolean isGameLoaded() {
        return GameLauncher.isReady();
    }

    private void showFidelityWarning() {
        CraftlatroConfig.getInstance().setWarningAcknowledged(true);
        Minecraft client = Minecraft.getInstance();
        Screen current = client.gui.screen();
        client.gui.setScreen(new FidelityWarningScreen(current, () ->
                GameLauncher.promptForBalatroExe(client)));
    }

    private void sendChatLinkMessage(Minecraft client) {
        if (client.player == null) return;

        Component link = Component.literal("[Click here to select Balatro.exe]")
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent.RunCommand("/craftlatro pick"))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("Open file selector for Balatro.exe")))
                        .withUnderlined(true)
                        .withBold(true));

        Component message = Component.literal("")
                .append(Component.literal("Game not loaded. "))
                .append(link);

        client.player.sendSystemMessage(message);
    }

    private void openBrowserScreen(Minecraft client) {
        client.gui.setScreen(new BrowserScreen(GameLauncher.getBaseUrl()));
    }

    private void openBrowserIfReady() {
        if (isGameLoaded()) {
            openBrowserScreen(Minecraft.getInstance());
        } else {
            sendChatLinkMessage(Minecraft.getInstance());
        }
    }

    private void loadGameFromPath(String path) {
        Path exePath = Path.of(path);
        if (!Files.exists(exePath)) {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.sendSystemMessage(
                        Component.literal("File not found: " + path));
            }
            return;
        }
        if (!exePath.getFileName().toString().toLowerCase().endsWith(".exe")) {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.sendSystemMessage(
                        Component.literal("File is not an .exe: " + path));
            }
            return;
        }
        GameLauncher.loadGame(path, Minecraft.getInstance());
    }

    private void onClientStopping(Minecraft client) {
        GameLauncher.shutdown();
        BalatroBrowser.release();
    }
}
