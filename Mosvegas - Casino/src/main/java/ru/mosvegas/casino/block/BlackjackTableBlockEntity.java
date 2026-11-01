package ru.mosvegas.casino.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import ru.mosvegas.casino.config.BuiltinStyles;
import ru.mosvegas.casino.registry.CasinoRegistry;
import ru.mosvegas.casino.table.BlackjackTable;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Persistent settings of a table (style, staff entrance, limits, open flag, lifetime stats) and the
 * holder of its live {@link BlackjackTable} runtime on the server.
 */
public final class BlackjackTableBlockEntity extends BlockEntity implements GeoBlockEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.table.idle");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private String style = BuiltinStyles.DEFAULT;
    private BlockPos entrance;
    private long minBet;
    private long maxBet;
    private boolean open = true;
    private long wagered;
    private long paid;
    private long rounds;
    private BlackjackTable runtime;

    public BlackjackTableBlockEntity(BlockPos pos, BlockState state) {
        super(CasinoRegistry.BLACKJACK_TABLE_ENTITY, pos, state);
    }

    public static void serverTick(World world, BlockPos pos, BlockState state, BlackjackTableBlockEntity be) {
        if (world instanceof ServerWorld serverWorld) be.tickServer(serverWorld);
    }

    private void tickServer(ServerWorld world) {
        if (runtime == null) runtime = new BlackjackTable(this, world);
        runtime.tick();
    }

    public BlackjackTable runtime() { return runtime; }

    @Override
    public void markRemoved() {
        super.markRemoved();
        if (runtime != null) {
            runtime.dispose();
            runtime = null;
        }
    }

    // ------------------------------------------------------------------ settings

    public String style() { return style; }
    public BlockPos entrance() { return entrance; }
    public long minBetOverride() { return minBet; }
    public long maxBetOverride() { return maxBet; }
    public boolean isOpen() { return open; }
    public long wagered() { return wagered; }
    public long paid() { return paid; }
    public long rounds() { return rounds; }

    public void setStyle(String style) {
        this.style = style;
        changed();
    }

    public void setEntrance(BlockPos entrance) {
        this.entrance = entrance;
        changed();
    }

    public void setLimits(long min, long max) {
        this.minBet = Math.max(0, min);
        this.maxBet = Math.max(0, max);
        changed();
    }

    public void setOpen(boolean open) {
        this.open = open;
        changed();
    }

    public void addStats(long wagered, long paid, long rounds) {
        this.wagered += wagered;
        this.paid += paid;
        this.rounds += rounds;
        markDirty();
    }

    private void changed() {
        markDirty();
        if (world != null && !world.isClient) world.updateListeners(pos, getCachedState(), getCachedState(), 3);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        if (nbt.contains("style")) style = nbt.getString("style");
        entrance = nbt.contains("entrance") ? NbtHelper.toBlockPos(nbt.getCompound("entrance")) : null;
        minBet = nbt.getLong("minBet");
        maxBet = nbt.getLong("maxBet");
        open = !nbt.contains("open") || nbt.getBoolean("open");
        wagered = nbt.getLong("statWagered");
        paid = nbt.getLong("statPaid");
        rounds = nbt.getLong("statRounds");
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putString("style", style);
        if (entrance != null) nbt.put("entrance", NbtHelper.fromBlockPos(entrance));
        nbt.putLong("minBet", minBet);
        nbt.putLong("maxBet", maxBet);
        nbt.putBoolean("open", open);
        nbt.putLong("statWagered", wagered);
        nbt.putLong("statPaid", paid);
        nbt.putLong("statRounds", rounds);
    }

    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() { return BlockEntityUpdateS2CPacket.create(this); }

    @Override
    public NbtCompound toInitialChunkDataNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("style", style);
        nbt.putBoolean("open", open);
        return nbt;
    }

    // ------------------------------------------------------------------ GeckoLib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, state -> state.setAndContinue(IDLE)));
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
