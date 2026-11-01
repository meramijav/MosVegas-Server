package ru.mosvegas.core.zone;

import net.minecraft.block.*;
import net.minecraft.block.enums.*;
import net.minecraft.entity.*;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import ru.mosvegas.core.MosvegasCore;
import ru.mosvegas.core.api.FactionAccessProvider;
import ru.mosvegas.core.data.DataStore;
import ru.mosvegas.core.service.PropertyService;
import ru.mosvegas.core.util.Permissions;
import java.util.*;

public final class ZoneService {
    private static final List<FactionAccessProvider> providers = new ArrayList<>();
    private static final Set<FactionAccessProvider> failedProviders = new HashSet<>();
    private static final Map<UUID, Long> lastNotice = new HashMap<>();
    private ZoneService() {}

    public static void addFactionProvider(FactionAccessProvider provider) { providers.add(Objects.requireNonNull(provider)); }
    public static void clearSession() { lastNotice.clear(); failedProviders.clear(); }
    public static void disconnect(ServerPlayerEntity player) { lastNotice.remove(player.getUuid()); }

    private static boolean fallback(String dimension, ZoneAction action) {
        return !ZoneStore.get().protectedWorlds.contains(dimension)
                || (action != ZoneAction.BUILD && action != ZoneAction.TERRAIN);
    }
    private static Set<String> factions(ServerPlayerEntity player) {
        Set<String> ids = new HashSet<>();
        String own = DataStore.player(player.getUuid()).faction;
        if (own != null) ids.add(own);
        for (FactionAccessProvider provider : providers) {
            try { ids.addAll(Objects.requireNonNull(provider.factions(player))); }
            catch (Exception e) {
                if (failedProviders.add(provider)) MosvegasCore.LOGGER.error("Ошибка внешнего провайдера фракций; его доступ не выдан", e);
            }
        }
        return ids;
    }
    public static boolean can(ServerPlayerEntity player, World world, BlockPos pos, ZoneAction action) {
        if (world.isClient) return true;
        // Someone else's apartment/parking is closed regardless of the city zone; it has its own bypass node.
        if (player != null && PropertyService.denying(player, world, pos, action) != null) return false;
        if (player != null && Permissions.has(player, "mosvegas.zone.bypass." + action.id(), 3)) return true;
        String dimension = world.getRegistryKey().getValue().toString();
        List<ZoneDefinition> zones = ZoneStore.index().at(dimension, pos.getX(), pos.getY(), pos.getZ());
        Set<String> factionIds = player == null || zones.isEmpty() ? Set.of() : factions(player);
        return ZoneIndex.permits(zones, action, player == null ? null : player.getUuidAsString(), factionIds, fallback(dimension, action));
    }
    public static boolean environment(World world, BlockPos pos, ZoneAction action) { return can(null, world, pos, action); }
    public static boolean check(ServerPlayerEntity player, BlockPos pos, ZoneAction action) {
        if (can(player, player.getWorld(), pos, action)) return true;
        if (PropertyService.denying(player, player.getWorld(), pos, action) != null) notice(player, "§cЧужая недвижимость, запрещено: " + action.description());
        else deny(player, action);
        return false;
    }
    public static void deny(ServerPlayerEntity player, ZoneAction action) { notice(player, "§cВ этой зоне запрещено: " + action.description()); }
    private static void notice(ServerPlayerEntity player, String message) {
        long now = player.getWorld().getTime();
        Long previous = lastNotice.get(player.getUuid());
        if (previous == null || now < previous || now - previous >= 20) {
            lastNotice.put(player.getUuid(), now);
            player.sendMessage(Text.literal(message), true);
        }
    }
    public static ZoneAction blockAction(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        String id = Registries.BLOCK.getId(block).toString();
        if (block instanceof DoorBlock || block instanceof TrapdoorBlock || block instanceof FenceGateBlock
                || ZoneStore.get().doorBlocks.contains(id)) return ZoneAction.DOORS;
        if (world.getBlockEntity(pos) instanceof Inventory || state.createScreenHandlerFactory(world, pos) != null
                || ZoneStore.get().containerBlocks.contains(id)) return ZoneAction.CONTAINERS;
        return ZoneAction.INTERACT;
    }
    public static boolean useBlock(ServerPlayerEntity player, BlockPos pos) {
        ZoneAction action = blockAction(player.getWorld(), pos);
        if (!check(player, pos, action)) return false;
        BlockState state = player.getWorld().getBlockState(pos);
        if (state.getBlock() instanceof DoorBlock) {
            BlockPos other = state.get(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? pos.up() : pos.down();
            if (!check(player, other, action)) return false;
        }
        if (state.getBlock() instanceof ChestBlock && state.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE) {
            if (!check(player, pos.offset(ChestBlock.getFacing(state)), ZoneAction.CONTAINERS)) return false;
        }
        return true;
    }
    public static boolean changesBlocks(ItemStack stack) {
        Item item = stack.getItem();
        return item instanceof BlockItem || item instanceof BucketItem || item instanceof FlintAndSteelItem
                || item instanceof FireChargeItem || item instanceof BoneMealItem || item instanceof HoeItem
                || item instanceof ShovelItem || item instanceof AxeItem || item instanceof DecorationItem
                || item instanceof ArmorStandItem || item instanceof SpawnEggItem;
    }
    public static boolean isProjectile(ItemStack stack) {
        Item item = stack.getItem();
        return item instanceof RangedWeaponItem || item instanceof TridentItem || item instanceof SnowballItem
                || item instanceof EggItem || item instanceof ThrowablePotionItem || item instanceof FireworkRocketItem;
    }
    public static boolean shoot(LivingEntity shooter) {
        if (shooter.getWorld().isClient) return true;
        ServerPlayerEntity player = shooter instanceof ServerPlayerEntity p ? p : null;
        boolean allowed = can(player, shooter.getWorld(), shooter.getBlockPos(), ZoneAction.SHOOT);
        if (!allowed && player != null) deny(player, ZoneAction.SHOOT);
        return allowed;
    }
    public static Entity attacker(DamageSource source) {
        Entity actor = source.getAttacker();
        if (actor == null) actor = source.getSource();
        if (actor instanceof ProjectileEntity projectile && projectile.getOwner() != null) actor = projectile.getOwner();
        if (actor instanceof TntEntity tnt && tnt.getOwner() != null) actor = tnt.getOwner();
        return actor;
    }
    public static boolean damage(Entity victim, DamageSource source) {
        if (victim.getWorld().isClient) return true;
        Entity actor = attacker(source);
        if (victim instanceof PlayerEntity && source.isIn(net.minecraft.registry.tag.DamageTypeTags.IS_EXPLOSION)
                && !can(actor instanceof ServerPlayerEntity p ? p : null, victim.getWorld(), victim.getBlockPos(), ZoneAction.PVP)) return false;
        if (!(victim instanceof PlayerEntity) && actor instanceof ServerPlayerEntity player
                && !can(player, victim.getWorld(), victim.getBlockPos(), ZoneAction.ENTITIES)) return false;
        if (actor instanceof ServerPlayerEntity player && victim instanceof PlayerEntity && actor != victim) {
            if (!can(player, victim.getWorld(), victim.getBlockPos(), ZoneAction.PVP)
                    || !can(player, player.getWorld(), player.getBlockPos(), ZoneAction.PVP)) return false;
        }
        boolean projectile = source.getSource() instanceof ProjectileEntity
                || (source.getSource() != null && Registries.ENTITY_TYPE.getId(source.getSource().getType()).getNamespace().equals("tacz"));
        if (projectile && !gunHit(actor, victim)) return false;
        return true;
    }
    public static boolean gunHit(Entity actor, Entity victim) {
        ServerPlayerEntity player = actor instanceof ServerPlayerEntity p ? p : null;
        if (!(victim instanceof PlayerEntity) && !can(player, victim.getWorld(), victim.getBlockPos(), ZoneAction.ENTITIES)) return false;
        if (!can(player, victim.getWorld(), victim.getBlockPos(), ZoneAction.SHOOT)) return false;
        if (actor != null && !can(player, actor.getWorld(), actor.getBlockPos(), ZoneAction.SHOOT)) return false;
        return !(victim instanceof PlayerEntity && player != null && victim != player)
                || (can(player, victim.getWorld(), victim.getBlockPos(), ZoneAction.PVP)
                && can(player, player.getWorld(), player.getBlockPos(), ZoneAction.PVP));
    }
    public static boolean entityUse(ServerPlayerEntity player, Entity entity) {
        return check(player, entity.getBlockPos(), ZoneAction.ENTITIES);
    }
    public static boolean decorationDamage(Entity entity, DamageSource source) {
        Entity actor = attacker(source);
        return can(actor instanceof ServerPlayerEntity p ? p : null, entity.getWorld(), entity.getBlockPos(), ZoneAction.BUILD);
    }
}
