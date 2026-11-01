package ru.mosvegas.casino.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import ru.mosvegas.casino.client.state.ClientTable;
import ru.mosvegas.casino.client.state.ClientTables;
import ru.mosvegas.casino.game.blackjack.BlackjackPhase;
import ru.mosvegas.casino.net.TableSnapshot;

/** Small status strip while seated without the table menu open. */
public final class TableHud {
    private TableHud() {}

    public static void render(DrawContext ctx, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen != null || mc.options.hudHidden) return;
        ClientTable table = ClientTables.mine();
        if (table == null) return;
        TableSnapshot.Player me = table.me();
        if (me == null) return;
        BlackjackPhase phase = table.phase();
        int width = ctx.getScaledWindowWidth();
        Text title = Text.translatable("mosvegas_casino.phase." + phase.name().toLowerCase());
        boolean myTurn = me.decidingHand() >= 0 && me.decisionMask() != 0;
        if (myTurn) title = Text.translatable("mosvegas_casino.screen.your_turn").formatted(Formatting.BOLD);
        int remaining = table.remainingTicks(tickDelta);
        if (myTurn && me.decidingHand() < me.hands().size()) remaining = table.remainingTicks(me.hands().get(me.decidingHand()).remaining(), tickDelta);
        String time = remaining > 0 ? "  " + (remaining + 19) / 20 + " c" : "";
        Text line = title.copy().append(Text.literal(time));
        Text hint = Text.translatable("mosvegas_casino.hud.hint", ClientSetup.MENU_KEY.getBoundKeyLocalizedText());
        int w = Math.max(mc.textRenderer.getWidth(line), mc.textRenderer.getWidth(hint)) + 16;
        int x = width / 2 - w / 2, y = 4;
        ctx.fill(x, y, x + w, y + 26, myTurn ? 0xB0503A00 : 0x90000000);
        ctx.drawCenteredTextWithShadow(mc.textRenderer, line, width / 2, y + 4, myTurn ? 0xFFE45A : 0xFFE9A8);
        ctx.drawCenteredTextWithShadow(mc.textRenderer, hint, width / 2, y + 15, 0xAAAAAA);
    }
}
