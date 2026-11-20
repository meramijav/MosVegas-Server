package studio.merami.slimcore.entities.components;

import studio.merami.slimcore.baseclasses.BoundingBox;
import studio.merami.slimcore.baseclasses.ChassisGeometry;
import studio.merami.slimcore.baseclasses.DamageModel;
import studio.merami.slimcore.baseclasses.Hull;
import studio.merami.slimcore.baseclasses.InteractionZone;
import studio.merami.slimcore.baseclasses.Point3D;
import studio.merami.slimcore.baseclasses.SlimMath;
import studio.merami.slimcore.baseclasses.Text;
import studio.merami.slimcore.entities.instances.APart;
import studio.merami.slimcore.entities.instances.PartGlass;
import studio.merami.slimcore.entities.instances.PartGroundDevice;
import studio.merami.slimcore.entities.instances.PartOpening;
import studio.merami.slimcore.entities.instances.PartSeat;
import studio.merami.slimcore.jsondefs.AJSONInteractable;
import studio.merami.slimcore.jsondefs.AJSONMultipart;
import studio.merami.slimcore.sliminterface.BlockHit;
import studio.merami.slimcore.sliminterface.IEntityData;
import studio.merami.slimcore.sliminterface.IWrapperEntity;
import studio.merami.slimcore.sliminterface.IWrapperInventory;
import studio.merami.slimcore.sliminterface.IWrapperItemStack;
import studio.merami.slimcore.sliminterface.IWrapperNBT;
import studio.merami.slimcore.sliminterface.IWrapperPlayer;
import studio.merami.slimcore.sliminterface.IWrapperWorld;
import studio.merami.slimcore.sliminterface.InterfaceManager;
import studio.merami.slimcore.sliminterface.SyncedValue;
import studio.merami.slimcore.systems.SeatedInteractions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public abstract class AEntityF_Multipart<J extends AJSONMultipart> extends AEntityE_Interactable<J> {
    private static final SyncedValue<Integer> OPEN = SyncedValue.integer(AEntityF_Multipart.class, 0);
    private static final SyncedValue<Integer> JAMMED = SyncedValue.integer(AEntityF_Multipart.class, 0);
    private static final SyncedValue<Integer> WINDOWS = SyncedValue.integer(AEntityF_Multipart.class, 0);
    private static final SyncedValue<Integer> BROKEN_GLASS = SyncedValue.integer(AEntityF_Multipart.class, 0);
    private static final SyncedValue<Integer> TYRES = SyncedValue.integer(AEntityF_Multipart.class, 0);
    private static final List<SyncedValue<Integer>> SEATS = new ArrayList<>();
    private static final String GLASS_BREAK = "minecraft:block.glass.break";
    private static final double STORAGE_AIM = .08;
    private static final double DOOR_HEADING = Math.toRadians(25);
    private static final double DOOR_DOWN = Math.toRadians(5);
    private static final int EXIT_TICKS = 14;

    static {
        for (int i = 0; i < AJSONMultipart.MAX_SEATS; i++) SEATS.add(SyncedValue.integer(AEntityF_Multipart.class, -1));
    }

    private record ExitRequest(IWrapperPlayer player, int tick) {}

    private final Map<Integer, ExitRequest> pendingExits = new HashMap<>();
    private final Map<Integer, Integer> riderTurnTick = new HashMap<>();
    private J partsDefinition;
    private List<PartSeat> seats = List.of();
    private List<PartOpening> openings = List.of();
    private List<PartGlass> glass = List.of();
    private List<PartGroundDevice> wheels = List.of();
    private int shutOnMoveMask;
    private int preferredSeat = -1;
    private int lastExitSeat;
    protected int partInteractionTick = -100;

    protected AEntityF_Multipart(IWrapperEntity host, IEntityData data) {
        super(host, data);
    }

    public abstract float speed();

    public abstract Point3D chassisToWorld(Point3D local);

    public abstract Point3D restingBodyToWorld(Point3D local);

    private void ensureParts() {
        J definition = definition();
        if (definition == partsDefinition) return;
        partsDefinition = definition;
        List<PartOpening> newOpenings = new ArrayList<>();
        int mask = 0;
        for (AJSONMultipart.Opening opening : definition.openingList) {
            newOpenings.add(new PartOpening(this, opening));
            if (opening.shutOnMove) mask |= 1 << opening.index;
        }
        List<PartSeat> newSeats = new ArrayList<>();
        for (AJSONMultipart.Seat seat : definition.seats) newSeats.add(new PartSeat(this, seat));
        List<PartGlass> newGlass = new ArrayList<>();
        for (AJSONMultipart.Glass pane : definition.glass) newGlass.add(new PartGlass(this, pane));
        List<PartGroundDevice> newWheels = new ArrayList<>();
        for (AJSONMultipart.Wheel wheel : definition.wheels) newWheels.add(new PartGroundDevice(this, wheel));
        openings = List.copyOf(newOpenings);
        seats = List.copyOf(newSeats);
        glass = List.copyOf(newGlass);
        wheels = List.copyOf(newWheels);
        shutOnMoveMask = mask;
    }

    public List<PartSeat> seats() {
        ensureParts();
        return seats;
    }

    public List<PartOpening> openings() {
        ensureParts();
        return openings;
    }

    public List<PartGlass> glassPanes() {
        ensureParts();
        return glass;
    }

    public List<PartGroundDevice> wheels() {
        ensureParts();
        return wheels;
    }

    public PartSeat seat(int index) {
        return seats().get(index);
    }

    public PartGroundDevice wheel(int index) {
        return wheels().get(index);
    }

    public PartOpening opening(String id) {
        for (PartOpening opening : openings()) {
            if (opening.id().equals(id)) return opening;
        }
        return null;
    }

    public PartOpening openingByBone(String bone) {
        AJSONMultipart.Opening opening = definition().openingsByBone.get(bone);
        return opening == null ? null : openings().get(opening.index);
    }

    public PartGlass glassByBone(String bone) {
        AJSONMultipart.Glass pane = definition().glassByBone.get(bone);
        return pane == null ? null : glassPanes().get(pane.index);
    }

    public boolean isOpen(PartOpening part) {
        return (data.get(OPEN) & 1 << part.index) != 0;
    }

    public void setOpen(PartOpening part, boolean open) {
        int bits = data.get(OPEN);
        data.set(OPEN, open ? bits | 1 << part.index : bits & ~(1 << part.index));
    }

    public boolean isJammed(PartOpening part) {
        return (data.get(JAMMED) & 1 << part.index) != 0;
    }

    public boolean windowDown(int seat) {
        return (data.get(WINDOWS) & 1 << seat) != 0;
    }

    public void toggleWindow(int seat) {
        if (seat >= 0 && definition().hasWindow(seat)) data.set(WINDOWS, data.get(WINDOWS) ^ 1 << seat);
    }

    public boolean brokenGlass(int index) {
        return (data.get(BROKEN_GLASS) & 1 << index) != 0;
    }

    public boolean punctured(int wheel) {
        return (data.get(TYRES) & 1 << wheel) != 0;
    }

    public int punctures() {
        return Integer.bitCount(data.get(TYRES));
    }

    protected boolean tickParts() {
        boolean changed = false;
        for (PartOpening opening : openings()) {
            opening.previousProgress = opening.progress;
            opening.progress = SlimMath.approach(opening.progress, opening.isOpen() ? 1 : 0, .12f);
            changed |= opening.previousProgress != opening.progress;
        }
        for (PartSeat seat : seats()) {
            seat.previousWindowProgress = seat.windowProgress;
            seat.windowProgress = SlimMath.approach(seat.windowProgress, seat.windowDown() ? 1 : 0, .085f);
            changed |= seat.previousWindowProgress != seat.windowProgress;
        }
        if (changed) invalidateHull();
        return changed;
    }

    protected boolean anyShutOnMoveOpen() {
        ensureParts();
        return (data.get(OPEN) & shutOnMoveMask) != 0;
    }

    protected void closeMovingParts() {
        for (PartOpening opening : openings()) {
            if (opening.definition.shutOnMove && opening.isOpen()) emitSound(definition().openingSound(opening.definition, false), 1);
        }
        data.set(OPEN, data.get(OPEN) & ~shutOnMoveMask);
    }

    public void toggle(PartOpening part) {
        data.set(OPEN, data.get(OPEN) ^ 1 << part.index);
        emitSound(definition().openingSound(part.definition, part.isOpen()), 1);
    }

    public void breakGlass(int index) {
        if (world().isClient() || index < 0 || index >= glassPanes().size() || brokenGlass(index)) return;
        Point3D point = hull().boxes().stream().filter(box -> box.glass == index).map(box -> box.center).findFirst().orElse(position());
        data.set(BROKEN_GLASS, data.get(BROKEN_GLASS) | 1 << index);
        emitSound(GLASS_BREAK, 1);
        world().spawnParticles(IWrapperWorld.Particle.GLASS, point, 18, .2, .18, .2, .07);
    }

    public void puncture(int wheel) {
        if (world().isClient() || wheel < 0 || wheel >= wheels().size() || punctured(wheel)) return;
        data.set(TYRES, data.get(TYRES) | 1 << wheel);
        emitSound(definition().sounds.blowout, 1);
        Point3D at = localToWorld(wheel(wheel).center());
        world().spawnParticles(IWrapperWorld.Particle.SMOKE, at, 8, .12, .1, .12, .025);
    }

    @Override
    protected void breakInZone(AJSONInteractable.Zone zone, Point3D local, float amount) {
        super.breakInZone(zone, local, amount);
        if (zone.jamIndex >= 0 && amount >= zone.jamAt) data.set(JAMMED, data.get(JAMMED) | 1 << zone.jamIndex);
        if (zone.glassIndex >= 0 && amount >= zone.glassAt) breakGlass(zone.glassIndex);
    }

    @Override
    protected void breakWheels(Point3D local, APart part, float amount) {
        super.breakWheels(local, part, amount);
        if (part instanceof PartGroundDevice wheel && amount >= DamageModel.TYRE) {
            puncture(wheel.index);
        } else if (amount >= DamageModel.WHEEL) {
            for (PartGroundDevice wheel : wheels()) {
                Point3D at = wheel.center();
                if (wheel.definition.solid && Math.hypot(at.x - local.x, at.z - local.z) < .9) {
                    puncture(wheel.index);
                    break;
                }
            }
        }
    }

    @Override
    public boolean damaged() {
        return super.damaged() || data.get(JAMMED) != 0 || data.get(BROKEN_GLASS) != 0 || data.get(TYRES) != 0;
    }

    @Override
    public void repair() {
        super.repair();
        data.set(JAMMED, 0);
        data.set(BROKEN_GLASS, 0);
        data.set(TYRES, 0);
    }

    public List<IWrapperEntity> passengers() {
        return host.passengers();
    }

    public boolean hasPassenger(IWrapperEntity entity) {
        return host.hasPassenger(entity);
    }

    public int seatOf(IWrapperEntity rider) {
        for (int i = 0; i < SEATS.size(); i++) {
            if (data.get(SEATS.get(i)) == rider.id()) return i;
        }
        return -1;
    }

    public boolean seated(IWrapperEntity rider) {
        List<PartSeat> list = seats();
        return !list.get(Math.max(0, Math.min(seatOf(rider), list.size() - 1))).standing();
    }

    public boolean seatTaken(int seat) {
        return data.get(SEATS.get(seat)) != -1;
    }

    public IWrapperEntity controllingPassenger() {
        for (IWrapperEntity entity : passengers()) {
            if (seatOf(entity) == 0 && entity.isLiving()) return entity;
        }
        return null;
    }

    public IWrapperPlayer controllingPlayer() {
        IWrapperEntity driver = controllingPassenger();
        return driver == null ? null : driver.asPlayer();
    }

    public boolean driver(IWrapperPlayer player) {
        return player.equals(controllingPassenger());
    }

    protected void reconcileSeats() {
        for (SyncedValue<Integer> seat : SEATS) {
            int rider = data.get(seat);
            if (rider != -1 && passengers().stream().noneMatch(entity -> entity.id() == rider)) data.set(seat, -1);
        }
        for (IWrapperEntity entity : passengers()) {
            if (seatOf(entity) >= 0) continue;
            for (int i = 0; i < seats().size(); i++) {
                if (!seatTaken(i)) {
                    data.set(SEATS.get(i), entity.id());
                    break;
                }
            }
        }
    }

    public boolean canAddRider(IWrapperEntity entity) {
        return passengers().size() < seats().size();
    }

    public void addRider(IWrapperEntity entity, Runnable attach) {
        attach.run();
        if (world().isClient()) return;
        int seat = preferredSeat;
        preferredSeat = -1;
        if (seat < 0) {
            for (int i = 0; i < seats().size(); i++) {
                if (!seatTaken(i)) {
                    seat = i;
                    break;
                }
            }
        }
        if (seat >= 0) data.set(SEATS.get(seat), entity.id());
    }

    public void removeRider(IWrapperEntity entity, Runnable detach) {
        int seat = seatOf(entity);
        lastExitSeat = Math.max(0, seat);
        riderTurnTick.remove(entity.id());
        detach.run();
        if (!world().isClient() && seat >= 0) {
            data.set(SEATS.get(seat), -1);
            onRiderLeft(seat);
        }
    }

    protected void onRiderLeft(int seat) {}

    public Point3D riderPosition(IWrapperEntity passenger) {
        int index = Math.max(0, seatOf(passenger));
        PartSeat seat = seats().get(Math.min(index, seats().size() - 1));
        J definition = definition();
        return seat.standing() ? localToWorld(seat.pos())
                : chassisToWorld(seat.pos().add(0, ChassisGeometry.riderThighRadius(definition), 0)).add(0, -ChassisGeometry.riderHipHeight(definition), 0);
    }

    public void alignRider(IWrapperEntity passenger) {
        passenger.setBodyYaw(yaw(), prevYaw());
        if (riderTurnTick.getOrDefault(passenger.id(), -1) != ticks()) {
            float delta = SlimMath.wrapDegrees(yaw() - prevYaw());
            passenger.setYaw(passenger.yaw() + delta);
            riderTurnTick.put(passenger.id(), ticks());
        }
    }

    public Point3D dismountLocation(IWrapperEntity passenger) {
        int index = seatOf(passenger);
        if (index < 0) index = lastExitSeat;
        PartSeat seat = seats().get(Math.min(index, seats().size() - 1));
        int preferredSide = seat.pos().x >= 0 ? 1 : -1;
        for (int side : new int[]{preferredSide, -preferredSide}) {
            for (double distance : new double[]{.78, 1.28, 1.78, 2.38}) {
                Point3D at = localToWorld(new Point3D(side * (definition().size.halfWidth + distance), 0, seat.pos().z));
                BlockHit floor = world().raycastBlocks(at.add(0, 2, 0), at.add(0, -3, 0), passenger);
                if (!floor.hit() || !floor.top()) continue;
                Point3D feet = floor.location().add(0, .02, 0);
                BoundingBox body = passenger.standingBounds(feet);
                if (!hull().intersects(body) && world().noCollision(passenger, body)) return feet;
            }
        }
        return new Point3D(x(), bounds().maxY + .05, z());
    }

    public void board(IWrapperPlayer player, int seat) {
        if (!world().allows(player, IWrapperWorld.Access.ENTER, this)) {
            message(player, "message.slimvehicle.zone.enter");
            return;
        }
        if (flag(LOCKED) && !hasKey(player)) {
            message(player, "message.slimvehicle.locked");
            return;
        }
        if (seat < 0 || seat >= seats().size() || seatTaken(seat)) {
            message(player, "message.slimvehicle.seat_taken");
            return;
        }
        PartOpening door = seats().get(seat).door();
        if (door != null && door.isJammed() && !door.isOpen()) {
            message(player, "message.slimvehicle.door_jammed");
            return;
        }
        if (door != null && (!door.isOpen() || door.progress < .5)) {
            message(player, "message.slimvehicle.open_door_first");
            return;
        }
        if (Math.abs(speed()) > .07) {
            message(player, "message.slimvehicle.stop_first");
            return;
        }
        preferredSeat = seat;
        player.startRiding(this);
        preferredSeat = -1;
        player.setYaw(yaw());
        player.setHeadYaw(yaw());
    }

    public void requestExit(IWrapperPlayer player) {
        if (!hasPassenger(player)) return;
        if (Math.abs(speed()) > .07) {
            message(player, "message.slimvehicle.stop_first");
            return;
        }
        int seat = seatOf(player);
        PartOpening door = seat >= 0 && seat < seats().size() ? seats().get(seat).door() : null;
        if (door == null || door.isJammed() && !door.isOpen()) {
            finishExit(player);
            return;
        }
        if (!door.isOpen()) emitSound(definition().openingSound(door.definition, true), 1);
        setOpen(door, true);
        pendingExits.putIfAbsent(player.id(), new ExitRequest(player, ticks()));
    }

    protected boolean exiting() {
        return !pendingExits.isEmpty();
    }

    protected void tickExits() {
        Iterator<Map.Entry<Integer, ExitRequest>> iterator = pendingExits.entrySet().iterator();
        while (iterator.hasNext()) {
            ExitRequest request = iterator.next().getValue();
            IWrapperPlayer player = request.player();
            if (!hasPassenger(player)) {
                iterator.remove();
                continue;
            }
            int seat = seatOf(player);
            PartOpening door = seat >= 0 && seat < seats().size() ? seats().get(seat).door() : null;
            if (door != null) {
                setOpen(door, true);
                if (ticks() - request.tick() >= EXIT_TICKS) {
                    iterator.remove();
                    finishExit(player);
                }
            }
        }
    }

    private void finishExit(IWrapperPlayer player) {
        Point3D safe = dismountLocation(player);
        player.stopRiding();
        player.teleport(safe);
        player.setVelocity(Point3D.ZERO);
        player.resetFallDistance();
    }

    public List<InteractionZone> interactionZones() {
        List<InteractionZone> zones = new ArrayList<>();
        for (Hull.Box box : hull().controls()) zones.add(new InteractionZone(box.part, -1, box, false, box.part.label()));
        for (Hull.Box box : hull().boxes()) {
            if (box.part instanceof PartGroundDevice wheel) zones.add(new InteractionZone(wheel, -1, box, false, wheel.label()));
        }
        for (PartSeat seat : seats()) {
            PartOpening door = seat.door();
            if (door != null && (!door.isOpen() || door.progress < .5)) continue;
            zones.add(new InteractionZone(null, seat.index, restingBox(seat.definition.click.center, seat.definition.click.half, null), false, seat.label()));
        }
        for (PartOpening opening : openings()) {
            AJSONMultipart.Storage storage = opening.definition.storage;
            if (storage != null && opening.isOpen() && opening.progress > .5) {
                zones.add(new InteractionZone(opening, -1, restingBox(storage.center, storage.half, opening), true, storage.label));
            }
        }
        return zones;
    }

    private Hull.Box restingBox(Point3D center, Point3D half, APart part) {
        float yaw = (float) -Math.toRadians(yaw());
        return new Hull.Box(restingBodyToWorld(center), half.scale(scale()),
                new Point3D[]{new Point3D(1, 0, 0).yRot(yaw), new Point3D(0, 1, 0), new Point3D(0, 0, 1).yRot(yaw)}, part, -1);
    }

    public Optional<InteractionZone.Hit> seatedTarget(int seat, Point3D eye, Point3D look, Point3D end) {
        InteractionZone.Hit direct = null;
        InteractionZone.Hit toward = null;
        PartOpening door = seat >= 0 && seat < seats().size() ? seats().get(seat).door() : null;
        for (InteractionZone zone : interactionZones()) {
            boolean own = door != null && zone.part() == door && zone.seat() < 0 && !zone.storage();
            PartOpening opening = zone.part() instanceof PartOpening part ? part : null;
            if (own) {
                zone = new InteractionZone(door, -1, zone.box(), false, door.isOpen() ? "hud.slimvehicle.close_door" : "hud.slimvehicle.open_door");
            } else if (opening == null || opening.definition.storage == null || !opening.definition.storage.seats.contains(seat)) {
                continue;
            }
            Hull.Box box = own ? zone.box() : zone.box().grow(STORAGE_AIM);
            Optional<Point3D> point = box.clip(eye, end);
            if (point.isPresent() && (direct == null || eye.distanceTo(point.get()) < direct.distance())) {
                direct = new InteractionZone.Hit(zone, point.get(), eye.distanceTo(point.get()));
            }
            if (!own) continue;
            Point3D to = zone.box().center.subtract(eye);
            double distance = to.length();
            Point3D flat = new Point3D(to.x, 0, to.z);
            Point3D view = new Point3D(look.x, 0, look.z);
            boolean towards = angle(flat, view) < DOOR_HEADING && look.y < -Math.sin(DOOR_DOWN);
            if (towards && distance < eye.distanceTo(end) + .5 && (toward == null || distance < toward.distance())) {
                toward = new InteractionZone.Hit(zone, zone.box().center, distance);
            }
        }
        return Optional.ofNullable(direct != null ? direct : toward);
    }

    private static double angle(Point3D a, Point3D b) {
        return Math.acos(Math.max(-1, Math.min(1, a.dot(b) / Math.sqrt(a.lengthSqr() * b.lengthSqr()))));
    }

    public Optional<InteractionZone.Hit> targetAlong(Point3D start, Point3D end) {
        InteractionZone.Hit nearest = null;
        double obstruction = hull().ray(start, end).map(Hull.Hit::distance).orElse(Double.POSITIVE_INFINITY);
        for (InteractionZone zone : interactionZones()) {
            Optional<Point3D> point = zone.box().clip(start, end);
            if (point.isEmpty()) continue;
            double distance = start.distanceTo(point.get());
            if (distance > obstruction + .12) continue;
            if (nearest == null || distance < nearest.distance()) nearest = new InteractionZone.Hit(zone, point.get(), distance);
        }
        return Optional.ofNullable(nearest);
    }

    protected boolean useHeldItem(IWrapperPlayer player) {
        return false;
    }

    protected boolean useItemOnPart(IWrapperPlayer player, PartOpening part) {
        return false;
    }

    @Override
    public void interactPart(IWrapperPlayer player, boolean enter) {
        if (player.distanceToSqr(host) > 49 || !SeatedInteractions.canUseEntity(player, this) || ticks() - partInteractionTick < 5) return;
        if (useHeldItem(player)) return;
        Point3D from = player.eyePosition();
        Point3D to = from.add(player.lookAngle().scale(5));
        to = world().raycastBlocks(from, to, player).location();
        Optional<InteractionZone.Hit> hit = player.vehicle() == this ? seatedTarget(seatOf(player), from, player.lookAngle(), to) : targetAlong(from, to);
        if (definition().boardOnClick) {
            board(player, 0);
            return;
        }
        if (hit.isEmpty()) return;
        InteractionZone zone = hit.get().zone();
        boolean inside = hasPassenger(player);
        if (flag(LOCKED) && !hasKey(player) && !(inside && zone.part() instanceof PartOpening door && door.isDoor())) {
            message(player, "message.slimvehicle.locked");
            return;
        }
        if (zone.seat() >= 0) {
            board(player, zone.seat());
            return;
        }
        if (!(zone.part() instanceof PartOpening part)) return;
        if (useItemOnPart(player, part)) return;
        if (part.isDoor() && !inside && (enter || player.isSneaking())) {
            PartSeat chosen = null;
            for (PartSeat seat : seats()) {
                if (seat.door() == part && (chosen == null || seatTaken(chosen.index) && !seatTaken(seat.index))) chosen = seat;
            }
            if (chosen != null) {
                board(player, chosen.index);
                return;
            }
        }
        if (zone.storage() && part.isOpen() && part.progress > .5 && !player.isSneaking()) {
            if (world().allows(player, IWrapperWorld.Access.STORAGE, this)) openInventory(player, part);
            else message(player, "message.slimvehicle.zone.storage");
            return;
        }
        if (part.isJammed() && !part.isOpen()) {
            message(player, Text.translatable("message.slimvehicle.jammed", part.text()));
            return;
        }
        if (Math.abs(speed()) > .035 && !part.definition.usableWhileMoving) {
            message(player, "message.slimvehicle.stop_first");
            return;
        }
        partInteractionTick = ticks();
        toggle(part);
    }

    public IWrapperInventory storage(PartOpening part) {
        AJSONMultipart.Storage storage = part.definition.storage;
        int rows = storage == null ? 3 : SlimMath.clamp(storage.rows, 1, 6);
        IWrapperInventory inventory = part.storage;
        if (inventory == null || inventory.size() != rows * 9) {
            IWrapperInventory resized = InterfaceManager.coreInterface.createInventory(rows * 9);
            if (inventory != null) {
                for (IWrapperItemStack left : inventory.moveTo(resized)) host.dropItem(left);
            }
            part.storage = inventory = resized;
        }
        return inventory;
    }

    private void openInventory(IWrapperPlayer player, PartOpening part) {
        AJSONMultipart.Storage storage = part.definition.storage;
        IWrapperInventory inventory = storage(part);
        int rows = inventory.size() / 9;
        Text title = Text.withFallback(storage.title, storage.title, definition().displayName());
        player.openStorage(inventory, rows, title, viewer -> isAlive() && viewer.distanceToSqr(host) < 36 && (!flag(LOCKED) || hasKey(viewer)) && part.isOpen());
    }

    @Override
    public void onSyncedDataUpdated(SyncedValue<?> key) {
        super.onSyncedDataUpdated(key);
        if (key == BROKEN_GLASS || key == TYRES) refreshHull();
        if (key == TYRES) onTyresSynced();
    }

    protected void onTyresSynced() {}

    @Override
    public void load(IWrapperNBT tag) {
        super.load(tag);
        data.set(OPEN, tag.getInt("Open"));
        data.set(JAMMED, tag.getInt("Jammed"));
        data.set(WINDOWS, tag.getInt("Windows"));
        data.set(BROKEN_GLASS, tag.getInt("BrokenGlass"));
        data.set(TYRES, tag.getInt("Tyres"));
        for (PartOpening opening : openings()) {
            opening.storage = null;
            if (opening.hasStorage()) storage(opening).load(tag, "Storage_" + opening.id());
        }
    }

    @Override
    public void save(IWrapperNBT tag) {
        super.save(tag);
        tag.setInt("Open", data.get(OPEN));
        tag.setInt("Jammed", data.get(JAMMED));
        tag.setInt("Windows", data.get(WINDOWS));
        tag.setInt("BrokenGlass", data.get(BROKEN_GLASS));
        tag.setInt("Tyres", data.get(TYRES));
        for (PartOpening opening : openings()) {
            if (opening.hasStorage()) storage(opening).save(tag, "Storage_" + opening.id());
        }
    }
}
