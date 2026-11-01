package studio.merami.slimcore.entities.instances;

import studio.merami.slimcore.baseclasses.SlimMath;
import studio.merami.slimcore.physics.DrivePhysics;
import studio.merami.slimcore.sliminterface.IEntityData;
import studio.merami.slimcore.sliminterface.IWrapperEntity;
import studio.merami.slimcore.sliminterface.IWrapperNBT;
import studio.merami.slimcore.sliminterface.SyncedValue;

public class EntityVehicleF_Physics extends AEntityVehicleE_Powered {
    private static final SyncedValue<Integer> TINT = SyncedValue.integer(EntityVehicleF_Physics.class, 25);
    private static final SyncedValue<Integer> COLOR = SyncedValue.integer(EntityVehicleF_Physics.class, 0xffffff);

    public String rimStyle = "stock";
    public String bumperStyle = "stock";
    public String spoilerStyle = "none";
    public String skin = "stock";

    public EntityVehicleF_Physics(IWrapperEntity host, IEntityData data) {
        super(host, data);
        finishConstruction();
    }

    public int tint() {
        return data.get(TINT);
    }

    public void setTint(int percent) {
        data.set(TINT, SlimMath.clamp(percent, 0, 95));
    }

    public int color() {
        return data.get(COLOR);
    }

    public void setColor(int rgb) {
        data.set(COLOR, rgb & 0xffffff);
    }

    @Override
    public void tick() {
        super.tick();
        tickParts();
        tickVisualMotion();
        tickWipers();
        if (world().isClient()) {
            tickClientMotion();
            return;
        }
        reconcileSeats();
        tickExits();
        DrivePhysics.Input applied = tickControls();
        tickHorn();
        tickEngine(applied);
        tickParkedEngine();
        tickTransmission(applied);
        float steer = tickSteering();
        double lastForward = forward;
        double lastY = y();
        if (shouldShutOpenings()) closeMovingParts();
        double lastLateral = lateral;
        double yawDelta = driveStep(applied, steer);
        tickMotion(yawDelta, lastForward, lastLateral, lastY);
        warnLowFuel();
    }

    @Override
    public boolean animationActive(String condition) {
        if (condition.startsWith("!")) return !animationActive(condition.substring(1));
        int colon = condition.indexOf(':');
        String name = colon < 0 ? condition : condition.substring(0, colon);
        String argument = colon < 0 ? "" : condition.substring(colon + 1);
        return switch (name) {
            case "always" -> true;
            case "engine" -> flag(ENGINE);
            case "lights" -> flag(LIGHTS);
            case "siren" -> flag(SIREN);
            case "radio" -> flag(RADIO);
            case "wipers" -> flag(WIPERS);
            case "braking" -> flag(BRAKING);
            case "locked" -> flag(LOCKED);
            case "moving" -> Math.abs(speed()) > .02;
            case "reverse" -> gear() < 0;
            case "wrecked" -> wrecked();
            case "burning" -> fire() > 0;
            case "driver" -> controllingPassenger() != null;
            case "open" -> {
                PartOpening opening = opening(argument);
                yield opening != null && opening.isOpen();
            }
            case "gear" -> argument.equals(Integer.toString(gear()));
            case "seat" -> {
                int seat = parse(argument);
                yield seat >= 0 && seat < seats().size() && seatTaken(seat);
            }
            default -> false;
        };
    }

    private static int parse(String number) {
        try {
            return Integer.parseInt(number);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Override
    public void load(IWrapperNBT tag) {
        super.load(tag);
        setTint(tag.has("Tint") ? tag.getInt("Tint") : 25);
        setColor(tag.has("Color") ? tag.getInt("Color") : 0xffffff);
        rimStyle = tag.getString("Rims");
        bumperStyle = tag.getString("Bumper");
        spoilerStyle = tag.getString("Spoiler");
        skin = tag.getString("Skin");
    }

    @Override
    public void save(IWrapperNBT tag) {
        super.save(tag);
        tag.setInt("Tint", tint());
        tag.setInt("Color", color());
        tag.setString("Rims", rimStyle);
        tag.setString("Bumper", bumperStyle);
        tag.setString("Spoiler", spoilerStyle);
        tag.setString("Skin", skin);
    }
}
