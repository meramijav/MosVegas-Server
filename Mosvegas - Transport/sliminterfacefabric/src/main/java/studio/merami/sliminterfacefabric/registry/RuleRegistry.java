package studio.merami.sliminterfacefabric.registry;

import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.minecraft.world.level.GameRules;
import studio.merami.slimcore.sliminterface.IInterfaceCore;

import java.util.EnumMap;
import java.util.Map;

public final class RuleRegistry {
    public static final GameRules.Key<GameRules.BooleanValue> VEHICLE_DAMAGE = rule("slimVehicleDamage");
    public static final GameRules.Key<GameRules.BooleanValue> VEHICLE_HURTS_PEOPLE = rule("slimVehicleHurtsPeople");
    public static final GameRules.Key<GameRules.BooleanValue> VEHICLE_BREAKS_BLOCKS = rule("slimVehicleBreaksBlocks");
    private static final Map<IInterfaceCore.Rule, GameRules.Key<GameRules.BooleanValue>> KEYS = new EnumMap<>(IInterfaceCore.Rule.class);

    static {
        KEYS.put(IInterfaceCore.Rule.VEHICLE_DAMAGE, VEHICLE_DAMAGE);
        KEYS.put(IInterfaceCore.Rule.VEHICLE_HURTS_PEOPLE, VEHICLE_HURTS_PEOPLE);
        KEYS.put(IInterfaceCore.Rule.VEHICLE_BREAKS_BLOCKS, VEHICLE_BREAKS_BLOCKS);
    }

    private RuleRegistry() {}

    public static void register() {}

    public static GameRules.Key<GameRules.BooleanValue> key(IInterfaceCore.Rule rule) {
        return KEYS.get(rule);
    }

    private static GameRules.Key<GameRules.BooleanValue> rule(String name) {
        return GameRuleRegistry.register(name, GameRules.Category.MISC, GameRuleFactory.createBooleanRule(true));
    }
}
