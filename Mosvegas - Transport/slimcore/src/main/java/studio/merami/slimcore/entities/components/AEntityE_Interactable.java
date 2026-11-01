package studio.merami.slimcore.entities.components;

import studio.merami.slimcore.baseclasses.BoundingBox;
import studio.merami.slimcore.baseclasses.DamageModel;
import studio.merami.slimcore.baseclasses.Hull;
import studio.merami.slimcore.baseclasses.Point3D;
import studio.merami.slimcore.baseclasses.SlimMath;
import studio.merami.slimcore.entities.instances.APart;
import studio.merami.slimcore.items.ItemKey;
import studio.merami.slimcore.jsondefs.AJSONInteractable;
import studio.merami.slimcore.sliminterface.IEntityData;
import studio.merami.slimcore.sliminterface.IInterfaceCore;
import studio.merami.slimcore.sliminterface.IWrapperEntity;
import studio.merami.slimcore.sliminterface.IWrapperNBT;
import studio.merami.slimcore.sliminterface.IWrapperPlayer;
import studio.merami.slimcore.sliminterface.InterfaceManager;
import studio.merami.slimcore.sliminterface.SyncedValue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public abstract class AEntityE_Interactable<J extends AJSONInteractable> extends AEntityD_Definable<J> {
    public static final int LOCKED = 4;
    private static final SyncedValue<Float> HEALTH = SyncedValue.decimal(AEntityE_Interactable.class, DamageModel.FULL);
    private static final List<SyncedValue<Integer>> ZONES = List.of(
            SyncedValue.integer(AEntityE_Interactable.class, 0),
            SyncedValue.integer(AEntityE_Interactable.class, 0));
    private static final SyncedValue<IWrapperNBT> DENTS = SyncedValue.nbt(AEntityE_Interactable.class);
    private static final double EMBED_MARGIN = .02;
    private static final double ESCAPE = .25;

    private final Set<UUID> trusted = new HashSet<>();
    private final float[] zoneDamage = new float[AJSONInteractable.MAX_ZONES];
    private UUID owner;
    private List<DamageModel.Dent> dents = new ArrayList<>();
    private List<DamageModel.Dent> dentView = Collections.unmodifiableList(dents);
    private Hull cachedHull;
    private Point3D hullPosition;
    private float hullYaw;

    protected AEntityE_Interactable(IWrapperEntity host, IEntityData data) {
        super(host, data);
    }

    public void setOwner(UUID id) {
        owner = id;
    }

    public UUID owner() {
        return owner;
    }

    public boolean isOwner(IWrapperPlayer player) {
        return player.uuid().equals(owner) || player.hasPermission(2);
    }

    public boolean hasKey(IWrapperPlayer player) {
        return trusted.contains(player.uuid()) || player.inventoryItems().stream().anyMatch(stack -> uuid().equals(ItemKey.vehicleOf(stack)));
    }

    public void trust(UUID id, boolean value) {
        if (value) trusted.add(id);
        else trusted.remove(id);
    }

    public float health() {
        return data.get(HEALTH);
    }

    public boolean wrecked() {
        return health() <= 0;
    }

    public float zoneDamage(int zone) {
        if (!world().isClient()) return zoneDamage[zone];
        return (data.get(ZONES.get(zone / 8)) >>> (zone % 8 * 4) & 15) * DamageModel.FULL / 15;
    }

    public boolean anyZoneDamaged() {
        for (SyncedValue<Integer> zone : ZONES) {
            if (data.get(zone) != 0) return true;
        }
        return false;
    }

    public List<DamageModel.Dent> dents() {
        return dentView;
    }

    private void setDents(List<DamageModel.Dent> list) {
        dents = list;
        dentView = Collections.unmodifiableList(list);
        invalidateHull();
    }

    private void syncZones() {
        int[] packed = new int[ZONES.size()];
        for (int i = 0; i < AJSONInteractable.MAX_ZONES; i++) {
            packed[i / 8] |= SlimMath.clamp(Math.round(zoneDamage[i] / DamageModel.FULL * 15), 0, 15) << (i % 8 * 4);
        }
        for (int i = 0; i < packed.length; i++) data.set(ZONES.get(i), packed[i]);
    }

    protected void syncDents() {
        IWrapperNBT tag = InterfaceManager.coreInterface.createNBT();
        DamageModel.save(tag, dents);
        data.set(DENTS, tag);
        invalidateHull();
    }

    protected boolean addDent(Point3D local, Point3D push, float amount) {
        if (!DamageModel.dent(dents, local, push, amount)) return false;
        syncDents();
        return true;
    }

    protected void wear(float amount, float engineShare) {
        if (amount <= 0 || world().isClient() || !ruleEnabled(IInterfaceCore.Rule.VEHICLE_DAMAGE)) return;
        boolean intact = !wrecked();
        data.set(HEALTH, Math.max(0, health() - amount));
        wearEngine(amount * engineShare);
        if (intact && wrecked()) onWrecked();
    }

    protected void wearEngine(float amount) {}

    protected void onWrecked() {}

    protected void damage(Point3D local, Point3D push, APart part, float amount) {
        if (amount <= 0 || !ruleEnabled(IInterfaceCore.Rule.VEHICLE_DAMAGE)) return;
        AJSONInteractable.Zone zone = definition().damage.zone(local);
        if (zone == null) {
            wear(amount, 1);
            return;
        }
        wear(amount, zone.engine);
        zoneDamage[zone.index] = Math.min(DamageModel.FULL, zoneDamage[zone.index] + amount);
        syncZones();
        breakInZone(zone, local, amount);
        if (push != null) addDent(local, push, amount);
        breakWheels(local, part, amount);
    }

    protected void breakInZone(AJSONInteractable.Zone zone, Point3D local, float amount) {}

    protected void breakWheels(Point3D local, APart part, float amount) {}

    public boolean damaged() {
        return health() < DamageModel.FULL || anyZoneDamaged() || !dents.isEmpty();
    }

    public void repair() {
        data.set(HEALTH, DamageModel.FULL);
        Arrays.fill(zoneDamage, 0);
        syncZones();
        dents.clear();
        syncDents();
    }

    public void setCondition(float percent) {
        boolean intact = !wrecked();
        data.set(HEALTH, SlimMath.clamp(percent, 0, DamageModel.FULL));
        if (intact && wrecked()) onWrecked();
    }

    public abstract Hull buildHull(float yaw);

    public Hull hull() {
        if (cachedHull == null || hullYaw != yaw()) {
            cachedHull = buildHull(yaw());
            hullPosition = position();
            hullYaw = yaw();
        } else if (!position().equals(hullPosition)) {
            cachedHull = cachedHull.move(position().subtract(hullPosition));
            hullPosition = position();
        }
        return cachedHull;
    }

    protected void adoptHull(Hull hull, float yaw) {
        cachedHull = hull;
        hullPosition = position();
        hullYaw = yaw;
    }

    public void invalidateHull() {
        cachedHull = null;
    }

    public void refreshHull() {
        invalidateHull();
        if (isConstructed()) host.refreshBounds();
    }

    @Override
    protected void onDefinitionChanged() {
        super.onDefinitionChanged();
        refreshHull();
    }

    public BoundingBox makeBounds() {
        return hull().bounds();
    }

    public void collisionBoxes(IWrapperEntity entity, BoundingBox query, List<BoundingBox> out) {
        if (entity != null && embeds(entity)) return;
        for (Hull.Box box : hull().entityBoxes()) box.collisionSlices(query, out);
    }

    private boolean overBody(IWrapperEntity entity) {
        Point3D local = worldToLocal(entity.position());
        J definition = definition();
        return Math.abs(local.x) < definition.bodyHalfWidth && Math.abs(local.z) < definition.bodyHalfLength;
    }

    private boolean free(IWrapperEntity entity) {
        return entity.equals(host) || entity.rootVehicle() == this || entity.isSpectator() || entity.noPhysics();
    }

    public boolean embeds(IWrapperEntity entity) {
        return !free(entity) && overBody(entity) && hull().intersectsEntity(entity.bounds().deflate(EMBED_MARGIN));
    }

    public boolean pushOut(IWrapperEntity entity) {
        if (free(entity)) return false;
        BoundingBox own = entity.bounds().deflate(EMBED_MARGIN);
        if (!hull().bounds().intersects(own)) return false;
        if (embeds(entity)) {
            escape(entity);
            return true;
        }
        Point3D deepest = null;
        for (Hull.Box box : hull().entityBoxes()) {
            if (!box.intersects(own)) continue;
            Point3D way = box.escape(own);
            if (deepest == null || way.lengthSqr() > deepest.lengthSqr()) deepest = way;
        }
        if (deepest == null) return false;
        if (deepest.y > 0) {
            entity.setPosition(entity.x(), entity.y() + deepest.y + EMBED_MARGIN, entity.z());
            return true;
        }
        double depth = deepest.length();
        if (depth < 1e-6) return true;
        Point3D out = deepest.scale(1 / depth);
        Point3D velocity = entity.velocity();
        double along = velocity.x * out.x + velocity.z * out.z;
        double need = Math.min(ESCAPE, depth + EMBED_MARGIN + .01) + carried(out);
        if (along < need) entity.setVelocity(velocity.add(out.x * (need - along), 0, out.z * (need - along)));
        return true;
    }

    private double carried(Point3D out) {
        Point3D travel = position().subtract(position(0));
        return Math.max(0, travel.x * out.x + travel.z * out.z);
    }

    private void escape(IWrapperEntity entity) {
        Point3D local = worldToLocal(entity.position());
        J definition = definition();
        double width = definition.bodyHalfWidth;
        double length = definition.bodyHalfLength;
        double margin = entity.width() / 2 / scale() + .05;
        double[] gap = {width - local.x, width + local.x, length - local.z, length + local.z};
        Point3D[] way = {new Point3D(1, 0, 0), new Point3D(-1, 0, 0), new Point3D(0, 0, 1), new Point3D(0, 0, -1)};
        Integer[] order = {0, 1, 2, 3};
        Arrays.sort(order, Comparator.comparingDouble(i -> gap[i]));
        Point3D out = directionToWorld(way[order[0]]);
        for (int i : order) {
            Point3D exit = localToWorld(local.add(way[i].scale(gap[i] + margin)));
            BoundingBox spot = entity.bounds().move(exit.x - entity.x(), 0, exit.z - entity.z()).deflate(.01);
            if (!world().blockCollides(entity, spot)) {
                out = directionToWorld(way[i]);
                break;
            }
        }
        Point3D velocity = entity.velocity();
        double along = velocity.x * out.x + velocity.z * out.z;
        double need = ESCAPE + carried(out);
        if (along < need) entity.setVelocity(velocity.add(out.x * (need - along), 0, out.z * (need - along)));
    }

    public abstract void interactPart(IWrapperPlayer player, boolean enter);

    @Override
    public void onSyncedDataUpdated(SyncedValue<?> key) {
        super.onSyncedDataUpdated(key);
        if (world().isClient() && key == DENTS) setDents(DamageModel.load(data.get(DENTS)));
    }

    @Override
    public void load(IWrapperNBT tag) {
        super.load(tag);
        if (tag.hasUUID("Owner")) owner = tag.getUUID("Owner");
        data.set(HEALTH, tag.has("Health") ? SlimMath.clamp(tag.getFloat("Health"), 0, DamageModel.FULL) : DamageModel.FULL);
        Arrays.fill(zoneDamage, 0);
        List<Float> zones = tag.getFloats("Zones");
        for (int i = 0; i < Math.min(zones.size(), zoneDamage.length); i++) zoneDamage[i] = SlimMath.clamp(zones.get(i), 0, DamageModel.FULL);
        syncZones();
        setDents(DamageModel.load(tag));
        syncDents();
        trusted.clear();
        for (IWrapperNBT user : tag.getCompounds("Trusted")) {
            if (user.hasUUID("Id")) trusted.add(user.getUUID("Id"));
        }
    }

    @Override
    public void save(IWrapperNBT tag) {
        super.save(tag);
        if (owner != null) tag.setUUID("Owner", owner);
        tag.setFloat("Health", health());
        List<Float> zones = new ArrayList<>();
        for (float zone : zoneDamage) zones.add(zone);
        tag.setFloats("Zones", zones);
        DamageModel.save(tag, dents);
        List<IWrapperNBT> list = new ArrayList<>();
        trusted.forEach(id -> {
            IWrapperNBT user = InterfaceManager.coreInterface.createNBT();
            user.setUUID("Id", id);
            list.add(user);
        });
        tag.setCompounds("Trusted", list);
    }
}
