package studio.merami.slimcore.entities.components;

import studio.merami.slimcore.baseclasses.Point3D;
import studio.merami.slimcore.baseclasses.Text;
import studio.merami.slimcore.jsondefs.JSONSounds;
import studio.merami.slimcore.sliminterface.IEntityData;
import studio.merami.slimcore.sliminterface.IInterfaceCore;
import studio.merami.slimcore.sliminterface.IWrapperEntity;
import studio.merami.slimcore.sliminterface.IWrapperNBT;
import studio.merami.slimcore.sliminterface.IWrapperPlayer;
import studio.merami.slimcore.sliminterface.IWrapperWorld;
import studio.merami.slimcore.sliminterface.SyncedValue;

import java.util.List;
import java.util.Random;
import java.util.UUID;

public abstract class AEntityA_Base {
    public final IWrapperEntity host;
    protected final IEntityData data;
    protected final Random random = new Random();
    private boolean constructed;

    protected AEntityA_Base(IWrapperEntity host, IEntityData data) {
        this.host = host;
        this.data = data;
    }

    protected final void finishConstruction() {
        constructed = true;
    }

    public final boolean isConstructed() {
        return constructed;
    }

    public IWrapperWorld world() {
        return host.world();
    }

    public int id() {
        return host.id();
    }

    public UUID uuid() {
        return host.uuid();
    }

    public int ticks() {
        return host.tickCount();
    }

    public boolean isAlive() {
        return host.isAlive();
    }

    public boolean isRemoved() {
        return host.isRemoved();
    }

    public void remove() {
        host.discard();
    }

    public void tick() {}

    public void onSyncedDataUpdated(SyncedValue<?> key) {}

    public void onSyncedDataUpdated(List<SyncedValue<?>> keys) {}

    public void load(IWrapperNBT tag) {}

    public void save(IWrapperNBT tag) {}

    public boolean ruleEnabled(IInterfaceCore.Rule rule) {
        return world().rule(rule);
    }

    public void emitSound(String sound, float pitch) {
        if (JSONSounds.silent(sound)) return;
        world().playSound(host.position(), sound, 1, pitch);
    }

    public void emitSoundAt(Point3D at, String sound, float volume, float pitch) {
        if (JSONSounds.silent(sound)) return;
        world().playSound(at, sound, volume, pitch);
    }

    public static void message(IWrapperPlayer player, Text text) {
        player.message(text);
    }

    public static void message(IWrapperPlayer player, String key, Object... arguments) {
        message(player, Text.translatable(key, arguments));
    }
}
