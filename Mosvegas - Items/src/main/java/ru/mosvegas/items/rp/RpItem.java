package ru.mosvegas.items.rp;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;
import java.util.UUID;

public class RpItem extends Item {
    private static final UUID KNUCKLES_DAMAGE = UUID.fromString("8d6f4a5e-31b2-4c8e-9a7d-5b0e2c6f1a43");
    public final RpKind kind;
    private final Multimap<EntityAttribute, EntityAttributeModifier> knuckles;

    public RpItem(Settings settings, RpKind kind) {
        super(settings);
        this.kind = kind;
        this.knuckles = ImmutableMultimap.of(EntityAttributes.GENERIC_ATTACK_DAMAGE,
                new EntityAttributeModifier(KNUCKLES_DAMAGE, "Knuckles", 4, EntityAttributeModifier.Operation.ADDITION));
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (kind.action == RpKind.Action.WEAPON || kind.action == RpKind.Action.TARGET) return TypedActionResult.pass(stack);
        if (user instanceof ServerPlayerEntity player && !RpServer.busy(player)) {
            RpServer.useSelf(player, hand, kind);
        }
        return TypedActionResult.success(stack, world.isClient);
    }

    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
        if (kind.action != RpKind.Action.TARGET) return ActionResult.PASS;
        if (user instanceof ServerPlayerEntity player && !RpServer.busy(player)) {
            RpServer.useOnTarget(player, hand, kind, entity);
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public Multimap<EntityAttribute, EntityAttributeModifier> getAttributeModifiers(EquipmentSlot slot) {
        return kind == RpKind.KNUCKLES && slot == EquipmentSlot.MAINHAND ? knuckles : super.getAttributeModifiers(slot);
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, net.minecraft.client.item.TooltipContext context) {
        tooltip.add(Text.translatable("item.mosvegas_items." + kind.id + ".description").formatted(Formatting.GRAY));
    }
}
