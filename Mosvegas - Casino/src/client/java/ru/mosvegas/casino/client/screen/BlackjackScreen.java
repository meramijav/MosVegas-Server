package ru.mosvegas.casino.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.Camera;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import ru.mosvegas.casino.block.BlackjackTableBlock;
import ru.mosvegas.casino.client.ClientNet;
import ru.mosvegas.casino.client.render.CasinoTextures;
import ru.mosvegas.casino.client.state.BetSpots;
import ru.mosvegas.casino.client.state.ClientTable;
import ru.mosvegas.casino.client.state.ClientTables;
import ru.mosvegas.casino.client.state.Settlement;
import ru.mosvegas.casino.game.blackjack.BlackjackPhase;
import ru.mosvegas.casino.game.blackjack.Decision;
import ru.mosvegas.casino.game.blackjack.Outcome;
import ru.mosvegas.casino.game.blackjack.SideBet;
import ru.mosvegas.casino.game.blackjack.TurnMode;
import ru.mosvegas.casino.net.TableAction;
import ru.mosvegas.casino.net.TableSnapshot;
import ru.mosvegas.casino.table.TableGeometry;
import ru.mosvegas.casino.table.TableSize;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Table menu laid out like a live-casino blackjack game: table name and limits on a thin top bar, the round
 * status with a timer in the middle, the chip rack with undo / repeat / double, bets placed by clicking the
 * spots on the 3D table (the chip is thrown onto the felt), round decision buttons, balance bottom-left,
 * the dealer's and your hand top-right and the result over the dealer. The game is not paused or darkened.
 */
public final class BlackjackScreen extends Screen {
    private static int selectedChip = 1;
    static final int GREEN = 0xFF1FA052, RED = 0xFFD8403A, ORANGE = 0xFFE89A1C, BLUE = 0xFF2F72D6, GREY = 0xFF5C5C5C,
            GOLD = 0xFFE0B040, DIM = 0xFFB4B4B4;

    private final BlockPos pos;
    private final List<UiButton> buttons = new ArrayList<>();
    private int hoveredSpot = ClientTable.NO_SPOT;
    private Decision flash;
    private float flashAt = -1000;

    public BlackjackScreen(BlockPos pos) {
        super(Text.translatable("mosvegas_casino.screen.title"));
        this.pos = pos;
    }

    @Override public boolean shouldPause() { return false; }

    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}

    private void act(TableAction action, int arg, long amount) { ClientNet.send(pos, action, arg, amount); }

    public BlockPos tablePos() { return pos; }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        buttons.clear();
        ClientTable table = ClientTables.get(pos);
        if (table == null || table.snapshot() == null) {
            ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("mosvegas_casino.screen.connecting"), width / 2, height / 2, 0xFFFFFF);
            return;
        }
        TableSnapshot s = table.snapshot();
        TableSnapshot.Player me = table.me();
        BlackjackPhase phase = BlackjackPhase.byId(s.phase());
        float now = ClientTable.now(delta);

        drawTopBar(ctx, s, me);
        drawStatus(ctx, table, s, me, phase, delta);
        if (me == null) buildSeatPicker(ctx, s);
        else {
            drawHands(ctx, s, me);
            drawBalance(ctx, s, me, phase);
            buildActions(ctx, table, s, me, phase);
            drawResult(ctx, s, me, phase);
        }
        boolean overButton = false;
        for (UiButton b : buttons) overButton |= b.hit(mouseX, mouseY);
        hoveredSpot = me != null && phase == BlackjackPhase.BETTING && !overButton ? pickSpot(s, me, mouseX, mouseY) : ClientTable.NO_SPOT;
        table.hover(hoveredSpot, now);
        for (UiButton b : buttons) b.draw(ctx, textRenderer, mouseX, mouseY);
        if (hoveredSpot != ClientTable.NO_SPOT) drawSpotTip(ctx, s, me, mouseX, mouseY);
        drawFlash(ctx, now);
        super.render(ctx, mouseX, mouseY, delta);
    }

    // ------------------------------------------------------------------ text helpers

    private String money(TableSnapshot s, long amount) {
        String n = String.format("%,d", amount).replace(',', ' ').replace(' ', ' ');
        return s.currency().isEmpty() ? n : n + " " + s.currency();
    }

    private String signed(TableSnapshot s, long amount) {
        return (amount > 0 ? "+" : amount < 0 ? "-" : "") + money(s, Math.abs(amount));
    }

    private void text(DrawContext ctx, Text t, int x, int y, int color, float scale, boolean centered) {
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y, 0);
        ctx.getMatrices().scale(scale, scale, 1f);
        if (centered) ctx.drawCenteredTextWithShadow(textRenderer, t, 0, 0, color);
        else ctx.drawTextWithShadow(textRenderer, t, 0, 0, color);
        ctx.getMatrices().pop();
    }

    private OrderedText fit(Text text, int maxWidth) {
        if (textRenderer.getWidth(text) <= maxWidth) return text.asOrderedText();
        return net.minecraft.util.Language.getInstance().reorder(textRenderer.trimToWidth(text, Math.max(10, maxWidth - 6)));
    }

    private static Text upper(Text t) { return Text.literal(t.getString().toUpperCase(Locale.ROOT)); }

    static void circle(DrawContext ctx, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int half = (int) Math.round(Math.sqrt(r * r - dy * dy));
            ctx.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
        }
    }

    // ------------------------------------------------------------------ top bar and status

    private void drawTopBar(DrawContext ctx, TableSnapshot s, TableSnapshot.Player me) {
        ctx.fill(0, 0, width, 13, 0xA0000000);
        MutableText title = Text.literal("MosVegas ").formatted(Formatting.GOLD, Formatting.BOLD)
                .append(Text.literal(s.styleName() + "  ").formatted(Formatting.WHITE))
                .append(Text.literal(money(s, s.minBet()) + " – " + money(s, s.maxBet())).formatted(Formatting.YELLOW));
        Text dealer = s.dealerPresent() ? Text.translatable("mosvegas_casino.screen.dealer", s.dealerName()) : Text.translatable("mosvegas_casino.screen.no_dealer");
        int right = width - 6;
        if (me != null) {
            buttons.add(new PillButton(width - 62, 1, 58, 11, Text.translatable("mosvegas_casino.button.leave_short"), RED, true, () -> {
                act(TableAction.LEAVE, 0, 0);
                close();
            }));
            right = width - 68;
        }
        int w = textRenderer.getWidth(dealer);
        ctx.drawTextWithShadow(textRenderer, dealer, right - w, 3, DIM);
        ctx.drawTextWithShadow(textRenderer, fit(title, right - w - 16), 5, 3, 0xFFFFFF);
    }

    private void drawStatus(DrawContext ctx, ClientTable table, TableSnapshot s, TableSnapshot.Player me, BlackjackPhase phase, float delta) {
        Text title = Text.translatable("mosvegas_casino.phase." + phase.name().toLowerCase());
        int remaining = table.remainingTicks(delta);
        int total = Math.max(1, s.phaseLength());
        if (phase != BlackjackPhase.BETTING && phase != BlackjackPhase.INSURANCE) remaining = 0;
        if (phase == BlackjackPhase.PLAYER_TURNS) {
            if (me != null && me.decidingHand() >= 0 && me.decidingHand() < me.hands().size() && me.decisionMask() != 0) {
                title = Text.translatable("mosvegas_casino.screen.your_turn");
                remaining = table.remainingTicks(me.hands().get(me.decidingHand()).remaining(), delta);
                total = Math.max(remaining, 20 * 12);
            } else if (s.activePlayer() != null && s.player(s.activePlayer()) != null) {
                title = Text.literal(s.player(s.activePlayer()).name()).append(Text.translatable("mosvegas_casino.phase.decides"));
            }
        }
        int cx = width / 2;
        // betting and insurance: big block over the dealer's chest (like a live casino); otherwise a slim line on top
        boolean big = phase == BlackjackPhase.BETTING || phase == BlackjackPhase.INSURANCE;
        int y = big ? statusY() : 17;
        text(ctx, upper(title), cx, y, 0xFFFFFF, big ? 1.25f : 1.0f, true);
        if (remaining > 0) {
            float frac = MathHelper.clamp(remaining / (float) total, 0f, 1f);
            int color = frac > 0.5f ? GREEN : frac > 0.25f ? 0xFFE3C21E : RED;
            int bw = 150, by = y + 24;
            text(ctx, Text.literal(Integer.toString((remaining + 19) / 20)).formatted(Formatting.BOLD), cx, y + 12, 0xFFFFFF, 1.1f, true);
            ctx.fill(cx - bw / 2, by, cx + bw / 2, by + 3, 0x80000000);
            int fill = (int) (bw * frac);
            ctx.fill(cx - fill / 2, by, cx + fill / 2, by + 3, color);
        } else if (!s.status().isEmpty() && !big) {
            Text status = Text.literal(s.status()).formatted(Formatting.ITALIC);
            int sw = Math.min(textRenderer.getWidth(status) + 10, Math.min(width - 40, 300));
            ctx.fill(cx - sw / 2, y + 11, cx + sw / 2, y + 22, 0x80000000);
            ctx.drawCenteredTextWithShadow(textRenderer, fit(status, sw - 8), cx, y + 13, 0xD0D0D0);
        }
    }

    /** Top of the betting block: just under the seated dealer's chin. */
    private int statusY() { return Math.max(40, (int) (height * 0.27f)); }

    /** Lowest free line above the hotbar. */
    private int aboveHotbar() { return height - 24; }

    // ------------------------------------------------------------------ bottom-left balance

    private void drawBalance(DrawContext ctx, TableSnapshot s, TableSnapshot.Player me, BlackjackPhase phase) {
        // bottom-left corner, lifted above the hotbar when the window is too narrow for both
        int y = width / 2 - 92 < 212 ? height - 50 : height - 26;
        ctx.fill(0, y - 4, 210, y + 22, 0x80000000);
        text(ctx, Text.translatable("mosvegas_casino.screen.balance"), 6, y, DIM, 0.7f, false);
        ctx.drawTextWithShadow(textRenderer, Text.literal(money(s, s.balance())).formatted(Formatting.BOLD), 6, y + 8, 0xFFFFFF);
        long stake = totalStake(me);
        boolean showWin = (phase == BlackjackPhase.PAYOUT || phase == BlackjackPhase.CLEANUP) && me.playing() && me.roundPayout() > stake;
        Text label = Text.translatable(showWin ? "mosvegas_casino.screen.last_win" : "mosvegas_casino.screen.total_bet_label");
        long value = showWin ? me.roundPayout() : stake;
        int x = 104;
        Identifier chip = CasinoTextures.chip(s.chips().isEmpty() ? 100 : s.chips().get(Math.min(selectedChip, s.chips().size() - 1)), s.chips());
        RenderSystem.enableBlend();
        ctx.drawTexture(chip, x, y + 2, 0, 0, 12, 12, 12, 12);
        RenderSystem.disableBlend();
        text(ctx, label, x + 15, y, DIM, 0.7f, false);
        ctx.drawTextWithShadow(textRenderer, Text.literal(money(s, value)).formatted(Formatting.BOLD), x + 15, y + 8, showWin ? 0xFF7CFF8C : 0xFFFFFF);
    }

    private static long totalStake(TableSnapshot.Player me) {
        long total = me.main() + me.insurance();
        for (long v : me.side()) total += v;
        for (int i = 0; i < me.hands().size(); i++) {
            long bet = me.hands().get(i).bet();
            total += i == 0 ? Math.max(0, bet - me.main()) : bet;
        }
        return total;
    }

    // ------------------------------------------------------------------ top-right hands

    private void drawHands(DrawContext ctx, TableSnapshot s, TableSnapshot.Player me) {
        int x = width - 128, y = 18;
        Identifier back = CasinoTextures.back(s.style());
        List<Row> rows = new ArrayList<>();
        if (s.dealerCards().length > 0) rows.add(new Row(Text.translatable("mosvegas_casino.screen.dealer_hand"), s.dealerCards(), s.dealerValue(), 0xFF2A2A2A, ""));
        List<TableSnapshot.Hand> hands = me.controls() ? me.hands() : s.owner(me.box()) == null ? List.of() : s.owner(me.box()).hands();
        for (int h = 0; h < hands.size(); h++) {
            TableSnapshot.Hand hand = hands.get(h);
            if (hand.cards().length == 0) continue;
            boolean deciding = me.controls() && me.decidingHand() == h && me.decisionMask() != 0;
            String value = hand.has(TableSnapshot.HAND_NATURAL) ? "BJ" : hand.value();
            int badge = hand.has(TableSnapshot.HAND_BUST) ? RED : hand.has(TableSnapshot.HAND_NATURAL) || "21".equals(value) ? GREEN : deciding ? GOLD : 0xFF2A2A2A;
            Text label = hands.size() > 1 ? Text.translatable("mosvegas_casino.screen.hand_n", h + 1) : Text.translatable("mosvegas_casino.screen.your_hand");
            Outcome o = Outcome.byId(hand.outcome());
            rows.add(new Row(label, hand.cards(), value, badge, o == Outcome.NONE ? "" : Text.translatable("mosvegas_casino.outcome." + o.name().toLowerCase()).getString()));
        }
        if (rows.isEmpty()) return;
        int rowH = 26;
        ctx.fill(x - 4, y - 3, width - 2, y + rows.size() * rowH, 0x80000000);
        RenderSystem.enableBlend();
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            int ry = y + i * rowH;
            text(ctx, upper(r.label), x, ry, DIM, 0.65f, false);
            int bx = x + 8, by = ry + 15;
            circle(ctx, bx, by, 8, 0xFF000000 | 0xFFFFFF);
            circle(ctx, bx, by, 7, r.badge);
            text(ctx, Text.literal(r.value), bx, by - 3, r.badge == GOLD ? 0xFF000000 : 0xFFFFFF, r.value.length() > 3 ? 0.6f : 0.8f, true);
            int cx = x + 20;
            for (int c = 0; c < r.cards.length && c < 8; c++) {
                int card = r.cards[c];
                ctx.drawTexture(card < 0 ? back : CasinoTextures.face(card), cx + c * 8, ry + 8, 0, 0, 10, 14, 10, 14);
            }
            if (!r.outcome.isEmpty()) text(ctx, Text.literal(r.outcome), width - 6 - (int) (textRenderer.getWidth(r.outcome) * 0.7f), ry + 12, GOLD, 0.7f, false);
        }
        RenderSystem.disableBlend();
    }

    private record Row(Text label, int[] cards, String value, int badge, String outcome) {}

    // ------------------------------------------------------------------ not seated

    private void buildSeatPicker(DrawContext ctx, TableSnapshot s) {
        int rows = s.boxes();
        int w = 260, h = 24 + rows * 22, x = width / 2 - w / 2, y = height / 2 - h / 2;
        ctx.fill(x, y, x + w, y + h, 0xB0000000);
        ctx.drawBorder(x, y, w, h, GOLD);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("mosvegas_casino.screen.pick_box"), width / 2, y + 7, 0xFFE9A8);
        for (int box = 0; box < rows; box++) {
            int ry = y + 22 + box * 22;
            TableSnapshot.Player owner = s.owner(box);
            int count = s.atBox(box).size();
            Text label = owner == null ? Text.translatable("mosvegas_casino.screen.box_free", box + 1)
                    : Text.translatable("mosvegas_casino.screen.box_taken", box + 1, owner.name(), count);
            ctx.drawTextWithShadow(textRenderer, label, x + 8, ry + 5, owner == null ? 0x9AE89A : 0xDDDDDD);
            final int b = box;
            if (owner == null) buttons.add(new PillButton(x + w - 92, ry, 84, 18, Text.translatable("mosvegas_casino.button.sit"), GREEN, !s.closed(), () -> act(TableAction.SIT, b, 0)));
            else buttons.add(new PillButton(x + w - 92, ry, 84, 18, Text.translatable("mosvegas_casino.button.behind"), BLUE, !s.closed(), () -> act(TableAction.BEHIND, b, 0)));
        }
    }

    // ------------------------------------------------------------------ actions by phase

    private void buildActions(DrawContext ctx, ClientTable table, TableSnapshot s, TableSnapshot.Player me, BlackjackPhase phase) {
        if (phase == BlackjackPhase.BETTING || phase == BlackjackPhase.IDLE) {
            int rackY = phase == BlackjackPhase.BETTING ? statusY() + 42 : statusY();
            buildRack(s, me, rackY);
            if (phase == BlackjackPhase.BETTING && me.main() >= s.minBet()) {
                Text deal = Text.translatable(me.ready() ? "mosvegas_casino.button.waiting" : "mosvegas_casino.button.deal");
                buttons.add(new PillButton(width / 2 - 45, rackY + 22, 90, 15, deal, GOLD, !me.ready(), () -> act(TableAction.READY, 0, 0)));
            }
            Text hint = Text.translatable("mosvegas_casino.screen.bet_hint");
            float scale = Math.min(0.75f, (width - 20) / (float) Math.max(1, textRenderer.getWidth(hint)));
            int tw = (int) (textRenderer.getWidth(hint) * scale) + 12, hy = aboveHotbar() - 30;
            ctx.fill(width / 2 - tw / 2, hy - 2, width / 2 + tw / 2, hy + 8, 0x80000000);
            text(ctx, hint, width / 2, hy, DIM, scale, true);
            return;
        }
        if (phase == BlackjackPhase.INSURANCE && me.playing() && me.controls() && !me.insuranceDecided()) {
            int y = statusY() + 40;
            buttons.add(new PillButton(width / 2 - 128, y, 124, 20, Text.translatable("mosvegas_casino.button.insurance_amount", money(s, me.main() / 2)), ORANGE, true,
                    () -> act(TableAction.INSURANCE, 1, 0)));
            buttons.add(new PillButton(width / 2 + 4, y, 124, 20, Text.translatable("mosvegas_casino.button.no_insurance"), GREY, true, () -> act(TableAction.INSURANCE, 0, 0)));
            return;
        }
        if (phase == BlackjackPhase.PLAYER_TURNS && me.decisionMask() != 0) {
            List<Object[]> list = new ArrayList<>();
            list.add(new Object[]{Decision.SPLIT, "⇆", "mosvegas_casino.button.split", BLUE});
            list.add(new Object[]{Decision.DOUBLE, "2×", "mosvegas_casino.button.double", ORANGE});
            list.add(new Object[]{Decision.HIT, "+", "mosvegas_casino.button.hit", GREEN});
            list.add(new Object[]{Decision.STAND, "−", "mosvegas_casino.button.stand", RED});
            list.add(new Object[]{Decision.SURRENDER, "½", "mosvegas_casino.button.surrender", GREY});
            list.removeIf(o -> (me.decisionMask() & (1 << ((Decision) o[0]).ordinal())) == 0);
            int spacing = 50, cy = aboveHotbar() - 26;
            int x0 = width / 2 - (list.size() - 1) * spacing / 2;
            for (int i = 0; i < list.size(); i++) {
                Decision d = (Decision) list.get(i)[0];
                buttons.add(new RoundButton(x0 + i * spacing, cy, 15, (String) list.get(i)[1], Text.translatable((String) list.get(i)[2]),
                        (Integer) list.get(i)[3], true, () -> decide(d)));
            }
            text(ctx, Text.translatable("mosvegas_casino.screen.keys"), width / 2, cy - 27, 0xFF9A9A9A, 0.7f, true);
            return;
        }
        Text waiting = switch (phase) {
            case PLAYER_TURNS -> table.turnMode() == TurnMode.COMMON && me.playing()
                    ? Text.translatable("mosvegas_casino.screen.wait_common") : Text.translatable("mosvegas_casino.screen.wait_players");
            case NO_DEALER -> Text.translatable("mosvegas_casino.screen.wait_dealer");
            case CLOSED -> Text.translatable("mosvegas_casino.phase.closed");
            default -> me.playing() ? Text.empty() : Text.translatable("mosvegas_casino.screen.next_round");
        };
        if (!waiting.getString().isEmpty()) {
            int tw = textRenderer.getWidth(waiting) + 16, wy = aboveHotbar() - 20;
            ctx.fill(width / 2 - tw / 2, wy, width / 2 + tw / 2, wy + 14, 0x90000000);
            ctx.drawCenteredTextWithShadow(textRenderer, waiting, width / 2, wy + 3, 0xDDDDDD);
        }
    }

    private void decide(Decision d) {
        act(TableAction.DECIDE, d.ordinal(), 0);
        flash = d;
        flashAt = ClientTable.now(0);
    }

    /** Undo, the chips (the selected one raised with a gold ring), repeat / double. */
    private void buildRack(TableSnapshot s, TableSnapshot.Player me, int cy) {
        List<Long> chips = s.chips();
        int size = 20, big = 26, gap = 5;
        int n = chips.size();
        int rackW = n * (size + gap) - gap + (big - size);
        int x = width / 2 - rackW / 2;
        int sel = Math.min(selectedChip, Math.max(0, n - 1));
        buttons.add(new RoundButton(x - 22, cy, 9, "↶", Text.translatable("mosvegas_casino.button.undo"), GREY, me.main() > 0 || totalSide(me) > 0,
                () -> act(TableAction.UNDO, 0, 0)));
        for (int i = 0; i < n; i++) {
            final int index = i;
            boolean selected = i == sel;
            int d = selected ? big : size;
            ChipButton chip = new ChipButton(x, cy - d / 2 - (selected ? 2 : 0), d, chips.get(i), CasinoTextures.chip(chips.get(i), chips), () -> selectedChip = index);
            chip.selected = selected;
            buttons.add(chip);
            x += d + gap;
        }
        boolean repeat = me.main() == 0 && totalSide(me) == 0;
        if (repeat) buttons.add(new RoundButton(x + 17, cy, 9, "↻", Text.translatable("mosvegas_casino.button.rebet"), GREY, true, () -> act(TableAction.REBET, 0, 0)));
        else buttons.add(new RoundButton(x + 17, cy, 9, "×2", Text.translatable("mosvegas_casino.button.double_bets"), GREY, true, () -> act(TableAction.DOUBLE_BETS, 0, 0)));
    }

    private static long totalSide(TableSnapshot.Player me) {
        long t = 0;
        for (long v : me.side()) t += v;
        return t;
    }

    // ------------------------------------------------------------------ result over the dealer

    private void drawResult(DrawContext ctx, TableSnapshot s, TableSnapshot.Player me, BlackjackPhase phase) {
        if ((phase != BlackjackPhase.PAYOUT && phase != BlackjackPhase.CLEANUP) || !me.playing()) return;
        long mainStake = Settlement.mainStake(me), mainNet = Settlement.mainPayout(me) - mainStake;
        long sideStake = totalSide(me), sideNet = Settlement.sum(me.sidePayout()) - sideStake;
        long insNet = me.insurancePayout() - me.insurance();
        long total = me.roundPayout() - totalStake(me);
        boolean blackjack = me.controls() && !me.hands().isEmpty() && Outcome.byId(me.hands().get(0).outcome()) == Outcome.BLACKJACK;
        long headline = mainStake > 0 ? mainNet : total;
        Text label, amount;
        int color;
        if (headline > 0) {
            label = Text.translatable(blackjack ? "mosvegas_casino.screen.blackjack" : "mosvegas_casino.screen.you_won");
            amount = Text.literal(signed(s, headline));
            color = GOLD;
        } else if (headline == 0) {
            label = Text.translatable("mosvegas_casino.screen.push_main");
            amount = Text.translatable("mosvegas_casino.screen.push_sub");
            color = 0xFFFFF29A;
        } else {
            label = Text.translatable("mosvegas_casino.screen.lost");
            amount = Text.literal(signed(s, headline));
            color = 0xFFFF7A7A;
        }
        int cx = width / 2, y = (int) (height * 0.30f);
        ctx.fill(cx - 120, y, cx + 120, y + 40, 0xA8000000);
        ctx.fill(cx - 120, y, cx + 120, y + 1, 0x80E0B040);
        ctx.fill(cx - 120, y + 39, cx + 120, y + 40, 0x80E0B040);
        text(ctx, upper(label), cx, y + 5, 0xFFFFFF, 0.9f, true);
        text(ctx, amount, cx, y + 17, color, headline == 0 ? 1.2f : 1.8f, true);
        // the parts, so that a won hand plus a lost side bet never reads as a "push"
        List<Text> parts = new ArrayList<>();
        if (sideStake > 0) parts.add(Text.translatable("mosvegas_casino.screen.side_net", signed(s, sideNet)));
        if (me.insurance() > 0) parts.add(Text.translatable("mosvegas_casino.screen.insurance_net", signed(s, insNet)));
        if (!parts.isEmpty()) {
            parts.add(Text.translatable("mosvegas_casino.screen.total_net", signed(s, total)));
            MutableText line = Text.empty();
            for (int i = 0; i < parts.size(); i++) {
                if (i > 0) line.append(Text.literal("  ·  ").formatted(Formatting.DARK_GRAY));
                line.append(parts.get(i));
            }
            int tw = textRenderer.getWidth(line) + 12;
            ctx.fill(cx - tw / 2, y + 42, cx + tw / 2, y + 54, 0x90000000);
            ctx.drawCenteredTextWithShadow(textRenderer, line, cx, y + 44, 0xDDDDDD);
        }
    }

    /** The chosen decision pops up big in the middle for a moment. */
    private void drawFlash(DrawContext ctx, float now) {
        float age = now - flashAt;
        if (flash == null || age < 0 || age > 14) return;
        String icon = switch (flash) {
            case HIT -> "+";
            case STAND -> "−";
            case DOUBLE -> "2×";
            case SPLIT -> "⇆";
            case SURRENDER -> "½";
        };
        int color = switch (flash) {
            case HIT -> GREEN;
            case STAND -> RED;
            case DOUBLE -> ORANGE;
            case SPLIT -> BLUE;
            case SURRENDER -> GREY;
        };
        int alpha = (int) (255 * MathHelper.clamp(1.2f - age / 14f, 0f, 1f));
        int cx = width / 2, cy = (int) (height * 0.42f), half = 18;
        ctx.fill(cx - half, cy - half, cx + half, cy + half, (alpha << 24) | (color & 0xFFFFFF));
        text(ctx, Text.literal(icon).formatted(Formatting.BOLD), cx, cy - 8, (alpha << 24) | 0xFFFFFF, 2.2f, true);
    }

    // ------------------------------------------------------------------ betting on the 3D table

    /** Spot of the local player under the mouse: a ray from the camera through the cursor hits the felt. */
    private int pickSpot(TableSnapshot s, TableSnapshot.Player me, double mouseX, double mouseY) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return ClientTable.NO_SPOT;
        BlockState state = mc.world.getBlockState(pos);
        if (!state.contains(BlackjackTableBlock.FACING) || !state.contains(BlackjackTableBlock.SIZE)) return ClientTable.NO_SPOT;
        Direction facing = state.get(BlackjackTableBlock.FACING);
        TableSize size = state.get(BlackjackTableBlock.SIZE);
        Vec3d hit = feltHit(mc, mouseX, mouseY);
        if (hit == null) return ClientTable.NO_SPOT;
        TableGeometry.Local local = TableGeometry.toLocal(pos, facing, hit);
        int best = ClientTable.NO_SPOT;
        double bestDist = Double.MAX_VALUE;
        for (Map.Entry<Integer, TableGeometry.Local> e : BetSpots.of(new TableGeometry(size, s.boxes()), s, me).entrySet()) {
            double d = Math.hypot(local.x() - e.getValue().x(), local.z() - e.getValue().z());
            if (d <= BetSpots.radius(e.getKey()) && d < bestDist) {
                best = e.getKey();
                bestDist = d;
            }
        }
        return best;
    }

    private Vec3d feltHit(MinecraftClient mc, double mouseX, double mouseY) {
        Camera cam = mc.gameRenderer.getCamera();
        Vec3d origin = cam.getPos();
        Vec3d forward = Vec3d.fromPolar(cam.getPitch(), cam.getYaw());
        Vec3d right = forward.crossProduct(new Vec3d(0, 1, 0)).normalize();
        Vec3d up = right.crossProduct(forward).normalize();
        double fov = Math.toRadians(mc.options.getFov().getValue());
        double aspect = (double) mc.getWindow().getFramebufferWidth() / Math.max(1, mc.getWindow().getFramebufferHeight());
        double nx = 2 * mouseX / width - 1, ny = 1 - 2 * mouseY / height;
        double t = Math.tan(fov / 2);
        Vec3d dir = forward.add(right.multiply(nx * t * aspect)).add(up.multiply(ny * t)).normalize();
        double planeY = pos.getY() + TableGeometry.FELT_Y + 0.01;
        if (dir.y > -1e-4) return null;
        double k = (planeY - origin.y) / dir.y;
        if (k < 0 || k > 16) return null;
        return origin.add(dir.multiply(k));
    }

    private long chipValue(TableSnapshot s) {
        List<Long> chips = s.chips();
        return chips.isEmpty() ? 0 : chips.get(Math.min(selectedChip, chips.size() - 1));
    }

    private void placeChip(TableSnapshot s, TableSnapshot.Player me, ClientTable table, int spot) {
        long value = chipValue(s);
        if (value <= 0) return;
        long current = spot == BetSpots.MAIN ? me.main() : Settlement.sideStake(me, spot);
        MinecraftClient mc = MinecraftClient.getInstance();
        Camera cam = mc.gameRenderer.getCamera();
        Vec3d from = cam.getPos().add(Vec3d.fromPolar(cam.getPitch(), cam.getYaw()).multiply(0.45)).add(0, -0.3, 0);
        table.throwChip(spot, value, from, current);
        act(TableAction.BET, spot, value);
    }

    private void drawSpotTip(DrawContext ctx, TableSnapshot s, TableSnapshot.Player me, int mouseX, int mouseY) {
        String name = hoveredSpot == BetSpots.MAIN ? Text.translatable("mosvegas_casino.button.main_bet").getString() : SideBet.values()[hoveredSpot].title;
        long current = hoveredSpot == BetSpots.MAIN ? me.main() : Settlement.sideStake(me, hoveredSpot);
        Text tip = Text.literal(name + "  ").formatted(Formatting.GOLD).append(Text.literal("+" + money(s, chipValue(s))).formatted(Formatting.WHITE))
                .append(current > 0 ? Text.literal("  (" + money(s, current) + ")").formatted(Formatting.GRAY) : Text.empty());
        int w = textRenderer.getWidth(tip) + 8;
        int x = Math.min(mouseX + 10, width - w - 2), y = mouseY + 10;
        ctx.fill(x, y, x + w, y + 12, 0xC0000000);
        ctx.drawTextWithShadow(textRenderer, tip, x + 4, y + 2, 0xFFFFFF);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int i = buttons.size() - 1; i >= 0; i--) {
                UiButton b = buttons.get(i);
                if (b.enabled && b.hit(mouseX, mouseY)) {
                    MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.6f));
                    b.action.run();
                    return true;
                }
            }
        }
        int spot = spotUnder(mouseX, mouseY);
        if (spot != ClientTable.NO_SPOT) {
            ClientTable table = ClientTables.get(pos);
            if (button == 0) placeChip(table.snapshot(), table.me(), table, spot);
            else if (button == 1) act(TableAction.UNDO, 0, 0);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** The local player's betting spot under a screen position while bets are open, or {@link ClientTable#NO_SPOT}. */
    public int spotUnder(double mouseX, double mouseY) {
        ClientTable table = ClientTables.get(pos);
        TableSnapshot.Player me = table == null ? null : table.me();
        if (me == null || table.phase() != BlackjackPhase.BETTING) return ClientTable.NO_SPOT;
        return pickSpot(table.snapshot(), me, mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        ClientTable table = ClientTables.get(pos);
        if (table != null && table.snapshot() != null && verticalAmount != 0) {
            int n = Math.max(1, table.snapshot().chips().size());
            selectedChip = Math.floorMod(Math.min(selectedChip, n - 1) + (verticalAmount < 0 ? 1 : -1), n);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        ClientTable table = ClientTables.get(pos);
        TableSnapshot.Player me = table == null ? null : table.me();
        if (me != null) {
            if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9) {
                selectedChip = keyCode - GLFW.GLFW_KEY_1;
                return true;
            }
            Decision d = switch (keyCode) {
                case GLFW.GLFW_KEY_H -> Decision.HIT;
                case GLFW.GLFW_KEY_S -> Decision.STAND;
                case GLFW.GLFW_KEY_D -> Decision.DOUBLE;
                case GLFW.GLFW_KEY_P -> Decision.SPLIT;
                default -> null;
            };
            if (d != null && (me.decisionMask() & (1 << d.ordinal())) != 0) {
                decide(d);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_ENTER) {
                if (table.phase() == BlackjackPhase.BETTING) act(TableAction.READY, 0, 0);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_B && table.phase() == BlackjackPhase.BETTING) {
                placeChip(table.snapshot(), me, table, BetSpots.MAIN);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_R) {
                act(TableAction.REBET, 0, 0);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_U) {
                act(TableAction.UNDO, 0, 0);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ------------------------------------------------------------------ widgets

    /** A chip of the rack: its own texture and value. */
    private final class ChipButton extends UiButton {
        private final Identifier texture;
        private final long value;

        ChipButton(int x, int y, int size, long value, Identifier texture, Runnable action) {
            super(x, y, size, size, Text.empty(), 0, true, action);
            this.texture = texture;
            this.value = value;
        }

        @Override
        public boolean hit(double mx, double my) { return Math.hypot(mx - (x + w / 2.0), my - (y + h / 2.0)) <= w / 2.0; }

        @Override
        public void draw(DrawContext ctx, TextRenderer tr, int mouseX, int mouseY) {
            int cx = x + w / 2, cy = y + h / 2;
            if (selected) {
                circle(ctx, cx, cy, w / 2 + 2, 0xFFFFD84A);
            } else if (hit(mouseX, mouseY)) circle(ctx, cx, cy, w / 2 + 1, 0x90FFFFFF);
            RenderSystem.enableBlend();
            ctx.drawTexture(texture, x, y, 0, 0, w, h, w, h);
            RenderSystem.disableBlend();
            ctx.getMatrices().push();
            ctx.getMatrices().translate(cx, cy - 2.5f, 200);
            float s = w >= 24 ? 0.75f : 0.6f;
            ctx.getMatrices().scale(s, s, 1f);
            ctx.drawCenteredTextWithShadow(tr, shortMoney(value), 0, 0, 0xFFFFFF);
            ctx.getMatrices().pop();
        }
    }

    /** Round action button with an icon and a caption below. */
    private final class RoundButton extends UiButton {
        private final String icon;
        private final int r;

        RoundButton(int cx, int cy, int r, String icon, Text caption, int color, boolean enabled, Runnable action) {
            super(cx - r, cy - r, 2 * r, 2 * r, caption, color, enabled, action);
            this.icon = icon;
            this.r = r;
        }

        @Override
        public boolean hit(double mx, double my) { return Math.hypot(mx - (x + r), my - (y + r)) <= r + 1; }

        @Override
        public void draw(DrawContext ctx, TextRenderer tr, int mouseX, int mouseY) {
            boolean hover = enabled && hit(mouseX, mouseY);
            int cx = x + r, cy = y + r;
            circle(ctx, cx, cy, r + 1, hover ? 0xFFFFFFFF : 0xC0000000);
            circle(ctx, cx, cy, r, enabled ? (hover ? brighten(color, 30) : color) : 0xFF3A3A3A);
            float scale = r >= 14 ? 1.6f : 1.0f;
            ctx.getMatrices().push();
            ctx.getMatrices().translate(cx, cy - 3.5f * scale, 0);
            ctx.getMatrices().scale(scale, scale, 1f);
            ctx.drawCenteredTextWithShadow(tr, Text.literal(icon).formatted(Formatting.BOLD), 0, 0, enabled ? 0xFFFFFF : 0x8A8A8A);
            ctx.getMatrices().pop();
            ctx.getMatrices().push();
            ctx.getMatrices().translate(cx, cy + r + 3, 0);
            ctx.getMatrices().scale(0.7f, 0.7f, 1f);
            ctx.drawCenteredTextWithShadow(tr, upper(label), 0, 0, enabled ? 0xFFFFFF : 0x8A8A8A);
            ctx.getMatrices().pop();
        }
    }

    /** Dark pill with a coloured outline (deal, insurance, leave, seat). */
    static final class PillButton extends UiButton {
        PillButton(int x, int y, int w, int h, Text label, int color, boolean enabled, Runnable action) {
            super(x, y, w, h, label, color, enabled, action);
        }

        @Override
        public void draw(DrawContext ctx, TextRenderer tr, int mouseX, int mouseY) {
            boolean hover = enabled && hit(mouseX, mouseY);
            int edge = enabled ? color : 0xFF4A4A4A;
            ctx.fill(x + 1, y, x + w - 1, y + h, edge);
            ctx.fill(x, y + 1, x + w, y + h - 1, edge);
            ctx.fill(x + 1, y + 1, x + w - 1, y + h - 1, hover ? 0xE0303030 : 0xE0141414);
            int textColor = !enabled ? 0x8A8A8A : color == GOLD ? 0xFFFFD86A : 0xFFFFFF;
            ctx.drawCenteredTextWithShadow(tr, upper(label), x + w / 2, y + (h - 8) / 2, textColor);
        }
    }

    static String shortMoney(long v) {
        if (v >= 1_000_000 && v % 1_000_000 == 0) return (v / 1_000_000) + "M";
        if (v >= 1_000 && v % 1_000 == 0) return (v / 1_000) + "K";
        return Long.toString(v);
    }
}
