package studio.merami.slimcore.entities.instances;

import studio.merami.slimcore.baseclasses.SlimMath;
import studio.merami.slimcore.paint.PaintShop;
import studio.merami.slimcore.physics.DrivePhysics;
import studio.merami.slimcore.plates.PlateNumber;
import studio.merami.slimcore.sliminterface.IEntityData;
import studio.merami.slimcore.sliminterface.IWrapperEntity;
import studio.merami.slimcore.sliminterface.IWrapperNBT;
import studio.merami.slimcore.sliminterface.InterfaceManager;
import studio.merami.slimcore.sliminterface.SyncedValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EntityVehicleF_Physics extends AEntityVehicleE_Powered {
    private static final SyncedValue<Integer> TINT = SyncedValue.integer(EntityVehicleF_Physics.class, 25);
    private static final SyncedValue<Integer> COLOR = SyncedValue.integer(EntityVehicleF_Physics.class, -1);
    private static final SyncedValue<Integer> INTERIOR_COLOR = SyncedValue.integer(EntityVehicleF_Physics.class, -1);
    private static final SyncedValue<IWrapperNBT> PART_COLORS = SyncedValue.nbt(EntityVehicleF_Physics.class);
    private static final SyncedValue<String> PLATE = SyncedValue.text(EntityVehicleF_Physics.class, PlateNumber.NONE);

    public String rimStyle = "stock";
    public String bumperStyle = "stock";
    public String spoilerStyle = "none";
    public String skin = "stock";
    private Map<String, Integer> partColors = Map.of();
    public volatile PaintShop.Order paintPreview;

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
        int own = data.get(COLOR);
        return own >= 0 ? own : definition().bodyColor;
    }

    public void setColor(int rgb) {
        data.set(COLOR, rgb < 0 ? -1 : rgb & 0xffffff);
        if (!partColors.isEmpty()) setPartColors(Map.of());
    }

    public int interiorColor() {
        int own = data.get(INTERIOR_COLOR);
        return own >= 0 ? own : definition().interiorColor;
    }

    public void setInteriorColor(int rgb) {
        data.set(INTERIOR_COLOR, rgb < 0 ? -1 : rgb & 0xffffff);
    }

    public String plate() {
        return data.get(PLATE);
    }

    public void setPlate(String number) {
        data.set(PLATE, PlateNumber.valid(number) ? number : PlateNumber.NONE);
    }

    public Map<String, Integer> partColors() {
        return partColors;
    }

    public int partColor(String bone) {
        int own = definition().interiorPaintBones.contains(bone) ? interiorColor() : partColors.getOrDefault(bone, color());
        PaintShop.Order preview = paintPreview;
        return definition().interiorShade(bone, preview == null ? own : preview.colorOf(this, bone, own));
    }

    public void setPartColor(String bone, int rgb) {
        if (!definition().paintBones.contains(bone)) return;
        Map<String, Integer> next = new HashMap<>(partColors);
        if ((rgb & 0xffffff) == color()) next.remove(bone);
        else next.put(bone, rgb & 0xffffff);
        setPartColors(next);
    }

    private void setPartColors(Map<String, Integer> colors) {
        partColors = Collections.unmodifiableMap(new HashMap<>(colors));
        IWrapperNBT tag = InterfaceManager.coreInterface.createNBT();
        savePartColors(tag);
        data.set(PART_COLORS, tag);
    }

    private void savePartColors(IWrapperNBT tag) {
        List<IWrapperNBT> parts = new ArrayList<>();
        for (Map.Entry<String, Integer> part : partColors.entrySet()) {
            IWrapperNBT entry = InterfaceManager.coreInterface.createNBT();
            entry.setString("Bone", part.getKey());
            entry.setInt("Color", part.getValue());
            parts.add(entry);
        }
        tag.setCompounds("PartColors", parts);
    }

    private static Map<String, Integer> loadPartColors(IWrapperNBT tag) {
        Map<String, Integer> colors = new HashMap<>();
        if (tag == null || !tag.hasList("PartColors")) return colors;
        for (IWrapperNBT entry : tag.getCompounds("PartColors")) colors.put(entry.getString("Bone"), entry.getInt("Color") & 0xffffff);
        return colors;
    }

    @Override
    public void onSyncedDataUpdated(SyncedValue<?> key) {
        super.onSyncedDataUpdated(key);
        if (world().isClient() && key == PART_COLORS) partColors = Collections.unmodifiableMap(loadPartColors(data.get(PART_COLORS)));
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
        boolean painted = tag.has("Painted") || tag.has("Color") && tag.getInt("Color") != 0xffffff;
        setColor(painted ? tag.getInt("Color") : -1);
        setInteriorColor(tag.has("InteriorColor") ? tag.getInt("InteriorColor") : -1);
        Map<String, Integer> parts = loadPartColors(tag);
        parts.keySet().retainAll(definition().paintBones);
        setPartColors(parts);
        rimStyle = tag.getString("Rims");
        bumperStyle = tag.getString("Bumper");
        spoilerStyle = tag.getString("Spoiler");
        skin = tag.getString("Skin");
        setPlate(tag.getString("Plate"));
    }

    @Override
    public void save(IWrapperNBT tag) {
        super.save(tag);
        tag.setInt("Tint", tint());
        int own = data.get(COLOR);
        tag.setInt("Color", own >= 0 ? own : 0xffffff);
        if (own == 0xffffff) tag.setInt("Painted", 1);
        if (data.get(INTERIOR_COLOR) >= 0) tag.setInt("InteriorColor", data.get(INTERIOR_COLOR));
        if (!partColors.isEmpty()) savePartColors(tag);
        tag.setString("Rims", rimStyle);
        tag.setString("Bumper", bumperStyle);
        tag.setString("Spoiler", spoilerStyle);
        tag.setString("Skin", skin);
        if (PlateNumber.valid(plate())) tag.setString("Plate", plate());
    }
}
