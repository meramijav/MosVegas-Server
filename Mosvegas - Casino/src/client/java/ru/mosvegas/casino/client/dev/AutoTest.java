package ru.mosvegas.casino.client.dev;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import ru.mosvegas.casino.MosVegasCasino;
import ru.mosvegas.casino.client.ClientNet;
import ru.mosvegas.casino.client.screen.BlackjackScreen;
import ru.mosvegas.casino.client.state.BetSpots;
import ru.mosvegas.casino.client.state.ClientTable;
import ru.mosvegas.casino.client.state.ClientTables;
import ru.mosvegas.casino.dev.Showcase;
import ru.mosvegas.casino.game.blackjack.BlackjackPhase;
import ru.mosvegas.casino.game.blackjack.Decision;
import ru.mosvegas.casino.net.TableAction;
import ru.mosvegas.casino.net.TableSnapshot;
import ru.mosvegas.casino.table.BlackjackTable;
import ru.mosvegas.casino.table.TableRegistry;

import java.util.function.BooleanSupplier;

/**
 * Scripted visual check, only with {@code -Dmosvegas.casino.autotest=true}: joins the showcase world,
 * sits down, plays a round and saves screenshots to {@code run/screenshots/casino_*.png}.
 */
public final class AutoTest {
    private interface Step { boolean run(MinecraftClient mc); }

    private static int tick;
    private static int stepIndex;
    private static int stepTicks;
    private static final int TABLE = Integer.getInteger("mosvegas.casino.autotest.table", 0);

    private AutoTest() {}

    public static void register() {
        if (!Boolean.getBoolean("mosvegas.casino.autotest")) return;
        ClientTickEvents.END_CLIENT_TICK.register(AutoTest::tick);
    }

    private static final Step[] STEPS = {
            mc -> tick > 60,
            mc -> server(mc, (server, player) -> {
                player.changeGameMode(GameMode.CREATIVE);
                ru.mosvegas.casino.economy.Economy.deposit(server, player.getUuid(), 250_000);
                MosVegasCasino.LOGGER.info("[autotest] balance {} via {}", ru.mosvegas.casino.economy.Economy.balance(player),
                        ru.mosvegas.casino.economy.Economy.get().name());
                BlockPos m = Showcase.master(TABLE);
                player.networkHandler.requestTeleport(m.getX() + 0.5, m.getY() + 2.2, m.getZ() + 6.5, 180, 22);
            }),
            mc -> waitFor(mc, 20 * 40, () -> dealerSeated(mc)),
            mc -> shot(mc, "01_overview"),
            mc -> server(mc, (server, player) -> {
                BlockPos m = Showcase.master(TABLE);
                player.networkHandler.requestTeleport(m.getX() + 5.5, m.getY() + 1.6, m.getZ() + 1.0, 90, 15);
            }),
            mc -> waitTicks(30),
            mc -> shot(mc, "02_dealer_side"),
            mc -> server(mc, (server, player) -> {
                BlackjackTable t = TableRegistry.get(player.getWorld(), Showcase.master(TABLE));
                if (t != null) t.sit(player, t.geometry().boxes / 2);
            }),
            mc -> waitFor(mc, 20 * 30, () -> phase(mc) == BlackjackPhase.BETTING),
            mc -> waitTicks(5),
            mc -> shot(mc, "03_betting_menu"),
            // the plain view from the chair (no menu, no HUD): all spots of the box must be visible
            mc -> {
                mc.setScreen(null);
                mc.options.hudHidden = true;
                return true;
            },
            mc -> waitTicks(3),
            mc -> shot(mc, "03b_seat_view"),
            mc -> {
                mc.options.hudHidden = false;
                mc.setScreen(new BlackjackScreen(Showcase.master(TABLE)));
                return true;
            },
            mc -> waitTicks(2),
            // a real click on the main spot of the 3D table (ray from the cursor), the chip flies onto the felt
            mc -> {
                clickSpot(mc, BetSpots.MAIN);
                return true;
            },
            mc -> waitTicks(3),
            mc -> shot(mc, "04a_chip_throw"),
            mc -> {
                clickSpot(mc, BetSpots.MAIN);
                ClientNet.send(Showcase.master(TABLE), TableAction.BET, -1, 1000);
                ClientNet.send(Showcase.master(TABLE), TableAction.BET, 1, 100);
                return true;
            },
            mc -> waitTicks(10),
            mc -> shot(mc, "04_bet_placed"),
            mc -> {
                ClientNet.send(Showcase.master(TABLE), TableAction.READY, 0, 0);
                return true;
            },
            // first card of the round: out of the shoe, in the hand, released, sliding
            mc -> waitFor(mc, 20 * 20, () -> phase(mc) == BlackjackPhase.DEALING),
            mc -> waitTicks(3),
            mc -> shot(mc, "05a_grab"),
            mc -> waitTicks(3),
            mc -> shot(mc, "05b_in_hand"),
            mc -> waitTicks(3),
            mc -> shot(mc, "05c_release"),
            mc -> waitTicks(12),
            mc -> shot(mc, "05_dealing"),
            mc -> waitFor(mc, 20 * 40, () -> myDecision(mc) || phase(mc) == BlackjackPhase.PAYOUT),
            mc -> waitTicks(10),
            mc -> shot(mc, "06_decision"),
            mc -> {
                if (myDecision(mc)) ClientNet.send(Showcase.master(TABLE), TableAction.DECIDE, Decision.STAND.ordinal(), 0);
                return true;
            },
            mc -> waitFor(mc, 20 * 40, () -> phase(mc) == BlackjackPhase.PAYOUT),
            mc -> shot(mc, "07_payout"),
            mc -> {
                mc.setScreen(null);
                return true;
            },
            // the dealer reaches for the bet (pays or takes it), then back to the tray
            mc -> waitTicks(10),
            mc -> shot(mc, "07a_pay_reach"),
            mc -> waitTicks(6),
            mc -> shot(mc, "08_payout_world"),
            // cleanup: hands sweep the cards together and drop them in the discard holder
            mc -> waitFor(mc, 20 * 20, () -> phase(mc) == BlackjackPhase.CLEANUP),
            mc -> waitTicks(10),
            mc -> shot(mc, "08a_collect_sweep"),
            mc -> waitTicks(13),
            mc -> shot(mc, "08b_collect_gather"),
            mc -> waitTicks(11),
            mc -> shot(mc, "08c_collect_drop"),
            mc -> server(mc, (server, player) -> {
                BlackjackTable t = TableRegistry.get(player.getWorld(), Showcase.master(TABLE));
                if (t != null) t.leave(player.getUuid(), true);
                BlockPos m = Showcase.master(TABLE);
                player.networkHandler.requestTeleport(m.getX() + 0.5, m.getY() + 3.4, m.getZ() + 5.2, 180, 42);
            }),
            mc -> waitTicks(30),
            mc -> shot(mc, "09_top_down"),
            // dealer shift change: shorten the shift, watch the colleague arrive and the old dealer leave
            mc -> server(mc, (server, player) -> {
                BlackjackTable table = TableRegistry.get(player.getWorld(), Showcase.master(TABLE));
                if (table != null) table.director().handoverNow();
                BlockPos m = Showcase.master(TABLE);
                player.networkHandler.requestTeleport(m.getX() + 6.5, m.getY() + 2.2, m.getZ() + 1.5, 125, 18);
            }),
            mc -> waitFor(mc, 20 * 30, () -> !dealerSeated(mc)),
            mc -> waitTicks(18),
            mc -> shot(mc, "12_handover_goodbye"),
            mc -> waitTicks(40),
            mc -> shot(mc, "13_handover_walk"),
            mc -> waitFor(mc, 20 * 30, () -> dealerSeated(mc)),
            mc -> shot(mc, "14_new_dealer"),

            // infinite blackjack: shared cards in the middle
            mc -> server(mc, (server, player) -> {
                BlackjackTable t = TableRegistry.get(player.getWorld(), Showcase.master(2));
                if (t != null) t.sit(player, 3);
            }),
            mc -> waitFor(mc, 20 * 30, () -> phaseAt(2) == BlackjackPhase.BETTING),
            mc -> {
                ClientNet.send(Showcase.master(2), TableAction.BET, -1, 500);
                ClientNet.send(Showcase.master(2), TableAction.READY, 0, 0);
                return true;
            },
            mc -> waitFor(mc, 20 * 40, () -> myDecisionAt(2) || phaseAt(2) == BlackjackPhase.PAYOUT),
            mc -> {
                if (myDecisionAt(2)) ClientNet.send(Showcase.master(2), TableAction.DECIDE, Decision.HIT.ordinal(), 0);
                return true;
            },
            mc -> waitTicks(30),
            mc -> shot(mc, "15_infinite_hit"),
            mc -> waitFor(mc, 20 * 40, () -> phaseAt(2) == BlackjackPhase.PAYOUT),
            mc -> waitTicks(6),
            mc -> shot(mc, "16_infinite_payout"),
            mc -> {
                mc.setScreen(null);
                return true;
            },
            mc -> server(mc, (server, player) -> {
                BlackjackTable t = TableRegistry.get(player.getWorld(), Showcase.master(2));
                if (t != null) t.leave(player.getUuid(), true);
            }),
            mc -> server(mc, (server, player) -> {
                BlockPos m = Showcase.master(3);
                player.networkHandler.requestTeleport(m.getX() + 0.5, m.getY() + 2.4, m.getZ() + 5.0, 180, 28);
            }),
            mc -> waitTicks(80),
            mc -> shot(mc, "10_free_bet_table"),
            mc -> server(mc, (server, player) -> {
                BlockPos m = Showcase.master(0);
                player.networkHandler.requestTeleport(m.getX() + 22, m.getY() + 3.5, m.getZ() + 9, 180, 18);
            }),
            mc -> waitTicks(60),
            mc -> shot(mc, "11_hall"),
            mc -> {
                MosVegasCasino.LOGGER.info("[autotest] finished");
                mc.scheduleStop();
                return true;
            }
    };

    private static void tick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;
        tick++;
        stepTicks++;
        if (stepIndex >= STEPS.length) return;
        try {
            if (STEPS[stepIndex].run(mc)) {
                MosVegasCasino.LOGGER.info("[autotest] step {} done at tick {}", stepIndex, tick);
                stepIndex++;
                stepTicks = 0;
            }
        } catch (Exception e) {
            MosVegasCasino.LOGGER.error("[autotest] step {} failed", stepIndex, e);
            stepIndex++;
            stepTicks = 0;
        }
    }

    private interface ServerTask { void run(IntegratedServer server, ServerPlayerEntity player); }

    private static boolean server(MinecraftClient mc, ServerTask task) {
        IntegratedServer server = mc.getServer();
        if (server == null) return true;
        var uuid = mc.player.getUuid();
        server.execute(() -> {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(uuid);
            if (p != null) task.run(server, p);
        });
        return true;
    }

    private static boolean waitTicks(int ticks) { return stepTicks >= ticks; }

    private static boolean waitFor(MinecraftClient mc, int timeout, BooleanSupplier condition) {
        if (condition.getAsBoolean()) return true;
        if (stepTicks > timeout) {
            MosVegasCasino.LOGGER.warn("[autotest] step {} timed out", stepIndex);
            return true;
        }
        return false;
    }

    private static boolean shot(MinecraftClient mc, String name) {
        ScreenshotRecorder.saveScreenshot(mc.runDirectory, "casino_" + name + ".png", mc.getFramebuffer(),
                text -> MosVegasCasino.LOGGER.info("[autotest] screenshot {}: {}", name, text.getString()));
        return true;
    }

    /** Finds where the spot is on screen through the menu's own ray picking and clicks there. */
    private static void clickSpot(MinecraftClient mc, int spot) {
        if (!(mc.currentScreen instanceof BlackjackScreen screen)) {
            MosVegasCasino.LOGGER.warn("[autotest] no table menu open for a click");
            return;
        }
        double sx = 0, sy = 0;
        int hits = 0;
        for (int y = 0; y < screen.height; y += 2) {
            for (int x = 0; x < screen.width; x += 2) {
                if (screen.spotUnder(x, y) == spot) {
                    sx += x;
                    sy += y;
                    hits++;
                }
            }
        }
        if (hits == 0) {
            MosVegasCasino.LOGGER.warn("[autotest] spot {} not found on screen", spot);
            return;
        }
        MosVegasCasino.LOGGER.info("[autotest] clicking spot {} at {}, {} ({} px)", spot, (int) (sx / hits), (int) (sy / hits), hits);
        screen.mouseClicked(sx / hits, sy / hits, 0);
    }

    private static ClientTable table() { return ClientTables.get(Showcase.master(TABLE)); }

    private static BlackjackPhase phase(MinecraftClient mc) {
        ClientTable t = table();
        return t == null ? BlackjackPhase.NO_DEALER : t.phase();
    }

    private static boolean dealerSeated(MinecraftClient mc) {
        ClientTable t = table();
        return t != null && t.snapshot() != null && t.snapshot().dealerPresent();
    }

    private static BlackjackPhase phaseAt(int index) {
        ClientTable t = ClientTables.get(Showcase.master(index));
        return t == null ? BlackjackPhase.NO_DEALER : t.phase();
    }

    private static boolean myDecisionAt(int index) {
        ClientTable t = ClientTables.get(Showcase.master(index));
        TableSnapshot.Player me = t == null ? null : t.me();
        return me != null && me.decisionMask() != 0;
    }

    private static boolean myDecision(MinecraftClient mc) {
        ClientTable t = table();
        TableSnapshot.Player me = t == null ? null : t.me();
        return me != null && me.decisionMask() != 0;
    }
}
