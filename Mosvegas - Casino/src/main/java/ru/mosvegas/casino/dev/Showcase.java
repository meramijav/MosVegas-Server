package ru.mosvegas.casino.dev;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameRules;
import ru.mosvegas.casino.MosVegasCasino;
import ru.mosvegas.casino.block.BlackjackTableBlockEntity;
import ru.mosvegas.casino.config.BuiltinStyles;
import ru.mosvegas.casino.config.ConfigManager;
import ru.mosvegas.casino.economy.Economy;
import ru.mosvegas.casino.registry.CasinoRegistry;
import ru.mosvegas.casino.table.TablePlacer;

/**
 * Development helpers, inactive unless started with {@code -Dmosvegas.casino.showcase=true}
 * (builds a demo casino hall with every table style) or {@code -Dmosvegas.casino.autostop=<ticks>}.
 */
public final class Showcase {
    public static final BlockPos ORIGIN = new BlockPos(0, 64, 0);
    public static final int SPACING = 10;

    private Showcase() {}

    public static void register() {
        boolean showcase = Boolean.getBoolean("mosvegas.casino.showcase");
        int autostop = Integer.getInteger("mosvegas.casino.autostop", 0);
        if (!showcase && autostop <= 0) return;
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (showcase) {
                ConfigManager.get().dealers.alwaysStaffed = true;
                build(server);
            }
        });
        if (showcase) {
            // demo money so a fresh tester can play right away
            ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
                ServerPlayerEntity player = handler.getPlayer();
                if (Economy.balance(player) >= 10_000) return;
                Economy.deposit(server, player.getUuid(), 100_000);
                player.sendMessage(Text.literal("§6[Казино] §fДемо-режим: на счёт зачислено 100 000 для игры."));
            });
        }
        if (autostop > 0) {
            int[] ticks = {0};
            ServerTickEvents.END_SERVER_TICK.register(server -> {
                if (++ticks[0] == autostop) {
                    MosVegasCasino.LOGGER.info("[autostop] {} ticks passed, stopping", autostop);
                    server.stop(false);
                }
            });
        }
    }

    public static BlockPos master(int index) { return ORIGIN.add(index * SPACING, 0, 0); }

    public static void build(MinecraftServer server) {
        ServerWorld world = server.getOverworld();
        world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, server);
        world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, server);
        world.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(false, server);
        world.setTimeOfDay(6000);
        world.setSpawnPos(ORIGIN.add(2 * SPACING, 0, 8), 0);
        if (world.getBlockState(master(0)).isOf(CasinoRegistry.BLACKJACK_TABLE)) return;
        int count = BuiltinStyles.ALL.size();
        // hall: floor, carpet, walls with a staff door behind every table, lights
        for (int x = -8; x <= (count - 1) * SPACING + 8; x++) {
            for (int z = -9; z <= 14; z++) {
                boolean carpet = z >= -1 && z <= 6;
                set(world, ORIGIN.add(x, -1, z), carpet ? Blocks.RED_CONCRETE.getDefaultState() : Blocks.POLISHED_DEEPSLATE.getDefaultState());
                for (int y = 0; y < 6; y++) set(world, ORIGIN.add(x, y, z), Blocks.AIR.getDefaultState());
                set(world, ORIGIN.add(x, 6, z), (x + z) % 4 == 0 ? Blocks.SEA_LANTERN.getDefaultState() : Blocks.DARK_OAK_PLANKS.getDefaultState());
            }
            for (int y = 0; y < 6; y++) set(world, ORIGIN.add(x, y, -10), Blocks.DARK_OAK_PLANKS.getDefaultState());
        }
        for (int i = 0; i < count; i++) {
            BlockPos m = master(i);
            String err = TablePlacer.place(world, m, Direction.SOUTH, BuiltinStyles.ALL.get(i).id());
            if (err != null) MosVegasCasino.LOGGER.warn("[showcase] {}", err);
            BlockPos door = m.add(0, 0, -6);
            set(world, door.add(0, 0, -4), Blocks.AIR.getDefaultState());
            set(world, door.add(0, 1, -4), Blocks.AIR.getDefaultState());
            set(world, door.add(-1, 2, -4), Blocks.GOLD_BLOCK.getDefaultState());
            set(world, door.add(1, 2, -4), Blocks.GOLD_BLOCK.getDefaultState());
            if (world.getBlockEntity(m) instanceof BlackjackTableBlockEntity be) be.setEntrance(door);
        }
        MosVegasCasino.LOGGER.info("[showcase] casino hall built at {}", ORIGIN.toShortString());
    }

    private static void set(ServerWorld world, BlockPos pos, BlockState state) {
        world.setBlockState(pos, state, Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
    }
}
