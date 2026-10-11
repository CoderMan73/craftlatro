package com.craftlatro.client.ui;

import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.util.Mth;

public class FidelityWarningScreen extends Screen {

    private static final Component TITLE = Component.literal("Balatro Web Port Notice");
    private static final Component WARNING_TEXT = Component.literal(
            "Love.js (2dengine web player) does not perfectly match native Balatro:\n\n" +
            "  - RNG: Seed parity is not guaranteed\n" +
            "  - Shaders: Visual effects may differ\n" +
            "  - Audio: Sound behavior differs\n\n" +
            "The Chromium runtime (~150MB) is managed by Rinku and downloaded on first use.\n\n" +
            "Select your Balatro.exe to begin."
    );

    private final Screen parent;
    private final Runnable onSelectExe;
    private MultiLineLabel message = MultiLineLabel.EMPTY;

    public FidelityWarningScreen(Screen parent, Runnable onSelectExe) {
        super(TITLE);
        this.parent = parent;
        this.onSelectExe = onSelectExe;
    }

    @Override
    public Component getNarrationMessage() {
        return CommonComponents.joinForNarration(super.getNarrationMessage(), WARNING_TEXT);
    }

    @Override
    protected void init() {
        super.init();

        this.message = MultiLineLabel.create(this.font, WARNING_TEXT, this.width - 50);
        int textHeight = this.message.getLineCount() * 9;
        int buttonY = Mth.clamp(90 + textHeight + 12, this.height / 6 + 96, this.height - 48);

        int buttonWidth = 150;
        int centerX = (this.width - buttonWidth) / 2;

        this.addRenderableWidget(Button.builder(
                Component.literal("Select Balatro.exe"),
                button -> {
                    onSelectExe.run();
                    onClose();
                }
        ).bounds(centerX, buttonY, buttonWidth, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("Later"),
                button -> onClose()
        ).bounds(centerX, buttonY + 24, buttonWidth, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        super.extractRenderState(graphics, mouseX, mouseY, partial);
        ActiveTextCollector textRenderer = graphics.textRenderer();
        graphics.centeredText(this.font, this.title, this.width / 2, 70, -1);
        this.message.visitLines(TextAlignment.CENTER, this.width / 2, 90, 9, textRenderer);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void onClose() {
        if (parent != null) {
            minecraft.gui.setScreen(parent);
        } else {
            super.onClose();
        }
    }
}
