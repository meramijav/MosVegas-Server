package studio.merami.slimcore.entities.instances;

import studio.merami.slimcore.baseclasses.DamageModel;
import studio.merami.slimcore.baseclasses.Point3D;
import studio.merami.slimcore.baseclasses.SlimMath;
import studio.merami.slimcore.items.ItemKey;
import studio.merami.slimcore.items.SlimItems;
import studio.merami.slimcore.jsondefs.AJSONMultipart;
import studio.merami.slimcore.jsondefs.JSONVehicle;
import studio.merami.slimcore.physics.DrivePhysics;
import studio.merami.slimcore.sliminterface.IEntityData;
import studio.merami.slimcore.sliminterface.IWrapperEntity;
import studio.merami.slimcore.sliminterface.IWrapperItemStack;
import studio.merami.slimcore.sliminterface.IWrapperNBT;
import studio.merami.slimcore.sliminterface.IWrapperPlayer;
import studio.merami.slimcore.sliminterface.IWrapperWorld;
import studio.merami.slimcore.sliminterface.SyncedValue;
import studio.merami.slimcore.systems.SeatedInteractions;

import java.util.Locale;

public abstract class AEntityVehicleE_Powered extends AEntityVehicleD_Moving {
    public static final int ENGINE = 1;
    public static final int LIGHTS = 2;
    public static final int SIREN = 16;
    public static final int RADIO = 32;
    public static final int WIPERS = 256;
    public static final int HORN = 512;
    private static final int ENGINE_EQUIPMENT = RADIO | WIPERS;
    private static final int HORN_RELEASE = 6;
    private static final int AUTOMATIC_SHIFT_TICKS = 8;
    private static final SyncedValue<Float> ENGINE_HEALTH = SyncedValue.decimal(AEntityVehicleE_Powered.class, DamageModel.FULL);
    private static final SyncedValue<Float> FUEL = SyncedValue.decimal(AEntityVehicleE_Powered.class, 35f);
    private static final SyncedValue<Float> RPM = SyncedValue.decimal(AEntityVehicleE_Powered.class, 0f);
    private static final SyncedValue<Integer> GEAR = SyncedValue.integer(AEntityVehicleE_Powered.class, 0);
    private static final SyncedValue<Integer> SHIFT_SEQUENCE = SyncedValue.integer(AEntityVehicleE_Powered.class, 0);
    private static final SyncedValue<Integer> START_TICK = SyncedValue.integer(AEntityVehicleE_Powered.class, Integer.MIN_VALUE / 2);
    private static final SyncedValue<Integer> POP_SEQUENCE = SyncedValue.integer(AEntityVehicleE_Powered.class, 0);
    private static final SyncedValue<Integer> LOCK_FLASH = SyncedValue.integer(AEntityVehicleE_Powered.class, Integer.MIN_VALUE / 2);
    private static final SyncedValue<Integer> RADIO_STATION = SyncedValue.integer(AEntityVehicleE_Powered.class, 0);
    private static final int LOCK_FLASH_TICKS = 14;
    private static final String EXPLODE_SOUND = "minecraft:entity.generic.explode";
    private static final String EXTINGUISH_SOUND = "minecraft:block.fire.extinguish";
    private static final String ANVIL_SOUND = "minecraft:block.anvil.use";
    private static final int FRESH_START = 10;
    private static final int OVERRUN_PAUSE = 35;
    private static final int SHIFT_POP_PAUSE = 12;
    private static final double OVERRUN_REVS = .65;

    private int shiftTick = -100;
    private int hornHeld = -100;
    private int parkedUntil = -1;
    private int exhaustTick = -100;
    private double previousThrottle;
    public int shiftAnimationStart = -100;
    public int startedAt = -1000;
    public int popAt = -100;
    public float wiperPhase;
    public float oldWiperPhase;

    protected AEntityVehicleE_Powered(IWrapperEntity host, IEntityData data) {
        super(host, data);
    }

    public float engineHealth() {
        return data.get(ENGINE_HEALTH);
    }

    public float fuel() {
        return data.get(FUEL);
    }

    public void setFuel(float litres) {
        data.set(FUEL, SlimMath.clamp(litres, 0, (float) definition().fuel.tank));
    }

    public float rpm() {
        return data.get(RPM);
    }

    public int gear() {
        return data.get(GEAR);
    }

    public int radioStation() {
        return data.get(RADIO_STATION);
    }

    public int startTick() {
        return data.get(START_TICK);
    }

    public int pops() {
        return data.get(POP_SEQUENCE);
    }

    public float fire() {
        return DamageModel.fire(engineHealth(), health());
    }

    public float fuelLeak() {
        float leak = 0;
        for (var zone : definition().damage.zones) {
            if (zone.leak) leak += Math.max(0, zoneDamage(zone.index) - 30) / 70f * .004f;
        }
        return leak;
    }

    protected void tickWipers() {
        oldWiperPhase = wiperPhase;
        JSONVehicle.Wipers wipers = definition().wipers;
        if (!world().isClient() || wipers == null) return;
        boolean running = flag(WIPERS) && flag(ENGINE);
        if (!running && wiperPhase > 0 && wiperPhase < .5f) wiperPhase = oldWiperPhase = 1 - wiperPhase;
        if (running || wiperPhase > 0) {
            wiperPhase += 1 / wipers.cycle;
            if (wiperPhase >= 1) {
                wiperPhase = 0;
                oldWiperPhase -= 1;
            }
        }
    }

    protected void tickEngine(DrivePhysics.Input applied) {
        JSONVehicle definition = definition();
        if (definition.exhaustEffects.overrun && flag(ENGINE) && rpm() > definition.engine.limitRpm * OVERRUN_REVS && previousThrottle > .7 && applied.throttle() < .1
                && ticks() - exhaustTick > OVERRUN_PAUSE) pop();
        previousThrottle = applied.throttle();
        float leak = fuelLeak();
        if (leak > 0 && fuel() > 0) data.set(FUEL, Math.max(0, fuel() - leak));
        if (fuel() <= 0 || host.inWater()) {
            setFlag(ENGINE, false);
            data.set(GEAR, 0);
        }
        if (flag(ENGINE) && (wrecked() || engineHealth() <= 0 || engineHealth() < 20 && random.nextInt(400) == 0)) stall();
        if (!flag(ENGINE) && (flags() & ENGINE_EQUIPMENT) != 0) setFlags(flags() & ~ENGINE_EQUIPMENT);
    }

    private void pop() {
        JSONVehicle definition = definition();
        exhaustTick = ticks();
        if (definition.tailpipes.isEmpty()) return;
        data.set(POP_SEQUENCE, data.get(POP_SEQUENCE) + 1);
        JSONVehicle.Tailpipe pipe = definition.tailpipes.get(random.nextInt(definition.tailpipes.size()));
        emitSoundAt(chassisToWorld(pipe.pos()), definition.sounds.exhaustPop, (float) definition.sounds.exhaustPopVolume, .92f + random.nextFloat() * .16f);
    }

    public Point3D enginePosition() {
        JSONVehicle definition = definition();
        return definition.hasEngineBay() ? chassisToWorld(definition.engine.pos) : position();
    }

    private void engineSound(String sound) {
        emitSoundAt(enginePosition(), sound, 1, 1);
    }

    protected void tickTransmission(DrivePhysics.Input applied) {
        JSONVehicle definition = definition();
        if (!definition.transmission.automatic || !flag(ENGINE) || controllingPassenger() == null || ticks() - shiftTick < AUTOMATIC_SHIFT_TICKS) return;
        int gear = gear();
        int next = gear == 0 ? (applied.throttle() > .1 && Math.abs(forward) < .05 ? 1 : 0)
                : DrivePhysics.automaticGear(definition.tune, gear, forward, applied.throttle(), DamageModel.power(engineHealth()), punctures(),
                definition.transmission.shiftUp, definition.transmission.shiftDown);
        if (next == gear) return;
        data.set(GEAR, next);
        shiftTick = ticks();
        if (gear == 0) emitSound(definition.sounds.gearShift, 1);
        if (definition.exhaustEffects.shift && next > gear && gear >= 1 && applied.throttle() > .8 && ticks() - exhaustTick > SHIFT_POP_PAUSE) pop();
    }

    protected double driveStep(DrivePhysics.Input applied, float steer) {
        JSONVehicle definition = definition();
        drift = DrivePhysics.drift(definition.tune, forward, steer, applied, drift);
        DrivePhysics.Result result = DrivePhysics.step(definition.tune, forward, lateral, steer, gear(), flag(ENGINE), fuel(), punctures(),
                applied, drift, DamageModel.power(engineHealth()));
        forward = result.forward();
        lateral = result.lateral();
        data.set(FUEL, (float) result.fuel());
        data.set(RPM, (float) DrivePhysics.revsToward(rpm(), result.rpm()));
        setSlip((float) result.slip());
        return result.yawDelta();
    }

    protected boolean shouldShutOpenings() {
        return !exiting() && (Math.abs(forward) > .035 || (input.throttle() > .05 && gear() != 0 && flag(ENGINE))) && anyShutOnMoveOpen();
    }

    protected void warnLowFuel() {
        IWrapperPlayer player = controllingPlayer();
        if (ticks() % 40 == 0 && flag(ENGINE) && fuel() < 3 && player != null) {
            message(player, energyKey("message.slimvehicle.low_fuel"), energy());
        }
    }

    private String energyKey(String key) {
        return definition().engine.electric ? key + ".electric" : key;
    }

    private String energy() {
        JSONVehicle definition = definition();
        return definition.engine.electric ? String.format(Locale.ROOT, "%.0f", fuel() / definition.fuel.tank * 100) : String.format(Locale.ROOT, "%.1f", fuel());
    }

    private void stall() {
        setFlag(ENGINE, false);
        data.set(GEAR, 0);
        engineSound(definition().sounds.stop);
        IWrapperPlayer player = controllingPlayer();
        if (player != null && !wrecked()) {
            message(player, energyKey(engineHealth() <= 0 ? "message.slimvehicle.engine_dead" : "message.slimvehicle.stalled"));
        }
    }

    @Override
    protected void wearEngine(float amount) {
        data.set(ENGINE_HEALTH, Math.max(0, engineHealth() - amount));
    }

    @Override
    protected void onWrecked() {
        if (flag(ENGINE)) stall();
        world().playSound(position(), EXPLODE_SOUND, .45f, 1.5f);
        JSONVehicle definition = definition();
        if (!world().isClient()) {
            Point3D at = localToWorld(definition.hasEngineBay() ? definition.engine.pos : new Point3D(0, definition.size.height * .6, 0));
            world().spawnParticles(IWrapperWorld.Particle.LARGE_SMOKE, at, 14, .4, .2, .4, .02);
        }
        IWrapperPlayer player = controllingPlayer();
        if (player != null) message(player, "message.slimvehicle.wrecked");
    }

    @Override
    public boolean damaged() {
        return super.damaged() || engineHealth() < DamageModel.FULL;
    }

    @Override
    public void repair() {
        boolean burning = fire() > 0;
        super.repair();
        data.set(ENGINE_HEALTH, DamageModel.FULL);
        if (burning && !world().isClient()) world().playSound(position(), EXTINGUISH_SOUND, 1, 1);
    }

    @Override
    public void setCondition(float percent) {
        data.set(ENGINE_HEALTH, SlimMath.clamp(percent, 0, DamageModel.FULL));
        super.setCondition(percent);
    }

    @Override
    protected boolean useHeldItem(IWrapperPlayer player) {
        if (player.mainHand().item() != SlimItems.REPAIR_KIT || hasPassenger(player)) return false;
        partInteractionTick = ticks();
        if (Math.abs(speed()) > .035) {
            message(player, "message.slimvehicle.stop_first");
            return true;
        }
        if (!damaged()) {
            message(player, "message.slimvehicle.not_damaged");
            return true;
        }
        repair();
        if (!player.instabuild()) player.mainHand().shrink(1);
        emitSound(ANVIL_SOUND, 1.2f);
        message(player, "message.slimvehicle.repaired");
        return true;
    }

    @Override
    protected boolean useItemOnPart(IWrapperPlayer player, PartOpening part) {
        if (part.kind() != AJSONMultipart.OpeningType.FUEL || player.mainHand().item() != SlimItems.FUEL_CAN) return false;
        if (!part.isOpen()) {
            message(player, energyKey("message.slimvehicle.open_fuel_flap"));
            return true;
        }
        if (flag(ENGINE)) {
            message(player, energyKey("message.slimvehicle.stop_engine_to_refuel"));
            return true;
        }
        IWrapperItemStack can = player.mainHand();
        int litres = can.dataOrCreate().has("Litres") ? can.data().getInt("Litres") : 20;
        float amount = Math.min(litres, (float) definition().fuel.tank - fuel());
        if (amount <= 0) {
            message(player, litres <= 0 ? "message.slimvehicle.can_empty" : energyKey("message.slimvehicle.tank_full"));
            return true;
        }
        data.set(FUEL, fuel() + amount);
        if (!player.instabuild()) can.dataOrCreate().setInt("Litres", Math.max(0, litres - (int) Math.ceil(amount)));
        emitSound(definition().sounds.refuel, 1);
        message(player, energyKey("message.slimvehicle.fuel"), energy());
        return true;
    }

    @Override
    protected void onRiderLeft(int seat) {
        super.onRiderLeft(seat);
        if (seat != 0) return;
        if (flag(ENGINE) && keepRunningTicks() > 0) {
            setFlags(flags() & ~(SIREN | HORN | BRAKING | TURN_LEFT | TURN_RIGHT));
            parkedUntil = ticks() + keepRunningTicks();
        } else {
            switchOff();
        }
        data.set(GEAR, 0);
        input = DrivePhysics.IDLE;
        brakePressure = 0;
    }

    private int keepRunningTicks() {
        return (int) Math.round(Math.max(0, definition().engine.keepRunning) * 20);
    }

    protected void tickParkedEngine() {
        if (controllingPassenger() != null || !flag(ENGINE)) {
            parkedUntil = -1;
            return;
        }
        if (parkedUntil < 0) parkedUntil = ticks() + keepRunningTicks();
        if (ticks() >= parkedUntil) {
            switchOff();
            parkedUntil = -1;
        }
    }

    public int parkedEngineTicksLeft() {
        return parkedUntil < 0 ? -1 : Math.max(0, parkedUntil - ticks());
    }

    private void switchOff() {
        if (flag(ENGINE)) engineSound(definition().sounds.stop);
        setFlags(flags() & ~(ENGINE | LIGHTS | SIREN | HORN | ENGINE_EQUIPMENT | BRAKING | TURN_LEFT | TURN_RIGHT));
        data.set(GEAR, 0);
        data.set(RPM, 0f);
    }

    public boolean toggleLock(IWrapperPlayer player) {
        if (!hasKey(player)) {
            message(player, "message.slimvehicle.no_key");
            return false;
        }
        setFlag(LOCKED, !flag(LOCKED));
        data.set(LOCK_FLASH, (int) world().gameTime());
        emitSound(definition().sounds.lock, 1);
        message(player, flag(LOCKED) ? "message.slimvehicle.locked" : "message.slimvehicle.unlocked");
        return true;
    }

    public void hornInput(IWrapperPlayer player, boolean held) {
        if (!driver(player)) return;
        if (held) hornHeld = ticks();
        if (flag(HORN) != held) setFlag(HORN, held);
    }

    protected void tickHorn() {
        if (flag(HORN) && (controllingPassenger() == null || ticks() - hornHeld > HORN_RELEASE)) setFlag(HORN, false);
    }

    public boolean lockFlash(long gameTime) {
        int since = (int) gameTime - data.get(LOCK_FLASH);
        if (since < 0 || since >= LOCK_FLASH_TICKS) return false;
        return flag(LOCKED) ? since < 10 : since < 5 || since >= 8 && since < 13;
    }

    public void action(IWrapperPlayer player, VehicleAction action) {
        double reach = action == VehicleAction.LOCK ? ItemKey.REACH : 8;
        if (action == null || player.distanceToSqr(host) > reach * reach || !SeatedInteractions.canUseEntity(player, this)) return;
        JSONVehicle definition = definition();
        switch (action) {
            case EXIT -> {
                requestExit(player);
                return;
            }
            case INTERACT -> {
                interactPart(player, false);
                return;
            }
            case ENTER -> {
                interactPart(player, true);
                return;
            }
            case LOCK -> {
                toggleLock(player);
                return;
            }
            case WINDOW -> {
                toggleWindow(seatOf(player));
                return;
            }
            default -> {
            }
        }
        if (!driver(player)) return;
        switch (action) {
            case ENGINE -> {
                if (!hasKey(player)) message(player, "message.slimvehicle.need_key");
                else if (!flag(ENGINE) && !driveAllowed()) message(player, "message.slimvehicle.zone.use");
                else toggleEngine(player);
            }
            case GEAR_UP -> shift(definition.transmission.automatic && gear() >= 1 ? gear() : gear() + 1, player);
            case GEAR_DOWN -> shift(definition.transmission.automatic && gear() >= 1 ? 0 : gear() - 1, player);
            case LIGHTS -> setFlag(LIGHTS, !flag(LIGHTS));
            case SIREN -> {
                if (definition.emergency) setFlag(SIREN, !flag(SIREN));
            }
            case RADIO -> {
                if (flag(ENGINE)) setFlag(RADIO, !flag(RADIO));
                else message(player, "message.slimvehicle.start_engine_first");
            }
            case RADIO_NEXT, RADIO_PREVIOUS -> {
                if (!flag(ENGINE)) {
                    message(player, "message.slimvehicle.start_engine_first");
                    return;
                }
                if (flag(RADIO)) data.set(RADIO_STATION, radioStation() + (action == VehicleAction.RADIO_NEXT ? 1 : -1));
                else setFlag(RADIO, true);
            }
            case WIPERS -> {
                if (definition.wipers == null) return;
                if (!flag(ENGINE)) {
                    message(player, "message.slimvehicle.start_engine_first");
                    return;
                }
                setFlag(WIPERS, !flag(WIPERS));
            }
            case MEGAPHONE -> message(player, "message.slimvehicle.megaphone");
            default -> {
            }
        }
    }

    private void toggleEngine(IWrapperPlayer player) {
        JSONVehicle definition = definition();
        if (flag(ENGINE)) {
            engineSound(definition.sounds.stop);
            setFlags(flags() & ~(ENGINE | ENGINE_EQUIPMENT));
            data.set(GEAR, 0);
        } else if (wrecked()) {
            message(player, "message.slimvehicle.wrecked");
        } else if (engineHealth() <= 0) {
            message(player, energyKey("message.slimvehicle.engine_dead_repair"));
        } else if (fuel() > 0) {
            setFlag(ENGINE, true);
            data.set(START_TICK, (int) world().gameTime());
        } else {
            message(player, energyKey("message.slimvehicle.tank_empty"));
        }
    }

    public void shift(int target, IWrapperPlayer player) {
        if (target == gear() || ticks() - shiftTick < 5) return;
        int gears = definition().gears();
        if (target < -1 || target > gears) return;
        if (target > gear() && target > 1 && Math.abs(forward) < .06) {
            message(player, "message.slimvehicle.start_in_first");
            return;
        }
        if (!DrivePhysics.canShift(gear(), target, forward, gears)) {
            message(player, "message.slimvehicle.stop_to_reverse");
            return;
        }
        int from = gear();
        data.set(GEAR, target);
        data.set(SHIFT_SEQUENCE, data.get(SHIFT_SEQUENCE) + 1);
        shiftTick = ticks();
        emitSound(target < from ? definition().sounds.gearShiftDown : definition().sounds.gearShift, 1);
    }

    @Override
    public void onSyncedDataUpdated(SyncedValue<?> key) {
        super.onSyncedDataUpdated(key);
        if (!world().isClient()) return;
        if (key == SHIFT_SEQUENCE) shiftAnimationStart = ticks();
        if (key == POP_SEQUENCE) popAt = ticks();
        if (key == START_TICK && world().gameTime() - data.get(START_TICK) < FRESH_START) startedAt = ticks();
    }

    @Override
    public void load(IWrapperNBT tag) {
        super.load(tag);
        setFlags(flags() & ~(ENGINE | BRAKING | SIREN | HORN | ENGINE_EQUIPMENT));
        data.set(FUEL, SlimMath.clamp(tag.getFloat("Fuel"), 0, (float) definition().fuel.tank));
        data.set(GEAR, 0);
        data.set(ENGINE_HEALTH, tag.has("EngineHealth") ? SlimMath.clamp(tag.getFloat("EngineHealth"), 0, DamageModel.FULL) : DamageModel.FULL);
        data.set(RADIO_STATION, tag.getInt("RadioStation"));
    }

    @Override
    public void save(IWrapperNBT tag) {
        super.save(tag);
        tag.setFloat("Fuel", fuel());
        tag.setFloat("EngineHealth", engineHealth());
        if (radioStation() != 0) tag.setInt("RadioStation", radioStation());
    }
}
