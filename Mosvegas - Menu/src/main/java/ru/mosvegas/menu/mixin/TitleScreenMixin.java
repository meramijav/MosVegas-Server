package ru.mosvegas.menu.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.mosvegas.menu.VideoBackground;

import java.util.ArrayList;
import java.util.List;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    @Unique
    private static final byte[] MOSVEGAS_SERVER_ADDRESS = {
            59, 38, 53, 19, 24, 3, 25, 10, 119, 98, 97, 110, 80
    };

    @Unique
    private static final int MOSVEGAS_TEXT_BEIGE = 0xE8D8BC;

    @Unique
    private static final int MOSVEGAS_TEXT_BEIGE_HOVERED = 0xF5E8CF;

    @Unique
    private final List<MenuButton> mosvegas$buttons = new ArrayList<>();

    @Unique
    private long mosvegas$openedNanos;

    protected TitleScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void mosvegas$init(CallbackInfo ci) {
        ci.cancel();
        mosvegas$openedNanos = System.nanoTime();
        mosvegas$buttons.clear();

        int x = Math.max(28, width / 13);
        int buttonWidth = Math.min(142, Math.max(118, width / 6));
        int buttonHeight = 17;
        int gap = 22;
        int y = Math.max(114, height / 2 - 24);

        MinecraftClient client = MinecraftClient.getInstance();
        mosvegas$buttons.add(new MenuButton(
                x, y, buttonWidth, buttonHeight,
                mosvegas$text("\u041f\u043e\u0434\u043a\u043b\u044e\u0447\u0438\u0442\u044c\u0441\u044f \u043a \u0441\u0435\u0440\u0432\u0435\u0440\u0443"),
                button -> mosvegas$connect(client), 0));
        mosvegas$buttons.add(new MenuButton(
                x, y + gap, buttonWidth, buttonHeight,
                mosvegas$text("\u041d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0438"),
                button -> client.setScreen(new OptionsScreen(this, client.options)), 1));
        mosvegas$buttons.add(new MenuButton(
                x, y + gap * 2, buttonWidth, buttonHeight,
                mosvegas$text("\u0412\u044b\u0445\u043e\u0434"),
                button -> client.scheduleStop(), 2));
        mosvegas$buttons.forEach(this::addDrawableChild);
    }

    @Unique
    private void mosvegas$connect(MinecraftClient client) {
        String address = mosvegas$serverAddress();
        ServerInfo server = new ServerInfo("MosVegas", address, ServerInfo.ServerType.OTHER);
        ConnectScreen.connect(this, client, ServerAddress.parse(address), server, false);
    }

    @Unique
    private static String mosvegas$serverAddress() {
        char[] decoded = new char[MOSVEGAS_SERVER_ADDRESS.length];
        for (int i = 0; i < MOSVEGAS_SERVER_ADDRESS.length; i++) {
            decoded[i] = (char) (MOSVEGAS_SERVER_ADDRESS[i] ^ (0x0D + i * 7));
        }
        return new String(decoded);
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void mosvegas$render(
            DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        VideoBackground.render(context, width, height);

        for (MenuButton button : mosvegas$buttons) {
            button.render(context, mouseX, mouseY, delta);
        }

        ci.cancel();
    }

    @Unique
    private double mosvegas$seconds() {
        return (System.nanoTime() - mosvegas$openedNanos) / 1_000_000_000.0;
    }

    @Unique
    private static float mosvegas$easeOutCubic(float value) {
        float clamped = Math.max(0.0f, Math.min(1.0f, value));
        float remaining = 1.0f - clamped;
        return 1.0f - remaining * remaining * remaining;
    }

    @Unique
    private static Text mosvegas$text(String value) {
        return Text.literal(value);
    }

    @Unique
    private final class MenuButton extends ButtonWidget {
        private final int order;
        private double lastFrameSeconds = Double.NaN;
        private float hover;
        private float press;
        private long clickedNanos;

        private MenuButton(
                int x, int y, int width, int height, Text text, PressAction action, int order) {
            super(x, y, width, height, text, action, DEFAULT_NARRATION_SUPPLIER);
            this.order = order;
        }

        @Override
        public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            double now = mosvegas$seconds();
            float frameTime = Double.isNaN(lastFrameSeconds)
                    ? 1.0f / 60.0f
                    : (float) Math.min(0.05, now - lastFrameSeconds);
            lastFrameSeconds = now;

            float hoverTarget = isHovered() ? 1.0f : 0.0f;
            hover += (hoverTarget - hover) * (1.0f - (float) Math.exp(-20.0f * frameTime));

            float pressTarget = System.nanoTime() - clickedNanos < 105_000_000L ? 1.0f : 0.0f;
            press += (pressTarget - press) * (1.0f - (float) Math.exp(-30.0f * frameTime));

            float appear = mosvegas$easeOutCubic((float) ((now - order * 0.055) / 0.38));
            int alpha = Math.max(0, Math.min(255, (int) (255 * appear)));
            int slide = Math.round((1.0f - appear) * -18.0f);
            int renderX = getX() + slide + Math.round(hover * 3.0f + press * 2.0f);
            int renderY = getY() + Math.round(press);

            if (hover > 0.005f) {
                int accentAlpha = Math.min(255, (int) (220 * hover * appear));
                context.fill(
                        renderX, renderY + 3, renderX + 1, renderY + height - 3,
                        (accentAlpha << 24) | 0xE7A44A);

                int lineAlpha = Math.min(255, (int) (145 * hover * appear));
                context.fill(
                        renderX + 5, renderY + height - 1,
                        renderX + 5 + Math.round((width - 12) * hover), renderY + height,
                        (lineAlpha << 24) | 0xD65A47);
            }

            int color = hover > 0.20f
                    ? MOSVEGAS_TEXT_BEIGE_HOVERED
                    : MOSVEGAS_TEXT_BEIGE;
            context.drawText(
                    textRenderer, getMessage(), renderX + 7, renderY + 4,
                    (alpha << 24) | color, true);
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            clickedNanos = System.nanoTime();
            super.onClick(mouseX, mouseY);
        }
    }
}
