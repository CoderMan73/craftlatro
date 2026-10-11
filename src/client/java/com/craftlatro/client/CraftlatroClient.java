package com.craftlatro.client;

import com.craftlatro.client.browser.BrowserScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public class CraftlatroClient implements ClientModInitializer {

    public static final String KEY_OPEN_BALATRO = "key.craftlatro.open_balatro";

    private static KeyMapping openBalatroKey;

    @Override
    public void onInitializeClient() {
        openBalatroKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                KEY_OPEN_BALATRO,
                InputConstants.KEY_B,
                KeyMapping.Category.MISC
        ));

        ClientTickEvents.START_CLIENT_TICK.register(this::onClientTick);
    }

    private void onClientTick(Minecraft client) {
        if (openBalatroKey.isDown() && !(client.gui.screen() instanceof BrowserScreen)) {
            if (client.player != null) {
                openBrowserScreen(client);
            }
        }
    }

    private void openBrowserScreen(Minecraft client) {
        String startUrl = "http://localhost:18080";
        client.gui.setScreen(new BrowserScreen(startUrl));
    }
}
