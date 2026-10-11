package com.craftlatro.client;

import com.craftlatro.client.browser.BrowserScreen;
import com.craftlatro.client.config.CraftlatroConfig;
import com.craftlatro.client.extractor.GameLauncher;
import com.craftlatro.client.ui.FidelityWarningScreen;
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

    private static KeyMapping openBalatroKey;
    private static KeyMapping selectExeKey;

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

    private void onClientTick(Minecraft client) {
        CraftlatroConfig config = CraftlatroConfig.getInstance();

        // Auto-load game if data already exists from a previous run
        if (!GameLauncher.isReady() && !GameLauncher.isExtractionInProgress()) {
            GameLauncher.tryAutoLoad(client);
        }

        if (!config.getWarningAcknowledged() && client.player != null && client.gui.screen() == null) {
            if (!isGameLoaded()) {
                Minecraft.getInstance().execute(this::showFidelityWarning);
            }
        }

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
    }
}
