package studio.merami.sliminterfacefabric.item;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import studio.merami.slimcore.baseclasses.Point3D;
import studio.merami.slimcore.baseclasses.Text;
import studio.merami.slimcore.items.AItemBase;
import studio.merami.sliminterfacefabric.wrapper.Convert;
import studio.merami.sliminterfacefabric.wrapper.WrapperItemStack;
import studio.merami.sliminterfacefabric.wrapper.WrapperPlayer;

public class BuilderItem extends Item {
    public final AItemBase item;

    public BuilderItem(AItemBase item) {
        super(new Item.Properties().stacksTo(item.maxStack));
        this.item = item;
    }

    @Override
    public Component getName(ItemStack stack) {
        Text name = item.displayName();
        return name == null ? super.getName(stack) : Convert.text(name);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!item.usedOnBlock()) return super.useOn(context);
        if (context.getPlayer() instanceof ServerPlayer player) {
            BlockPos clicked = context.getClickedPos();
            BlockPos pos = context.getLevel().getBlockState(clicked).canBeReplaced() ? clicked : clicked.relative(context.getClickedFace());
            Point3D at = new Point3D(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
            if (!item.useOnBlock(WrapperPlayer.of(player), new WrapperItemStack(context.getItemInHand()), at)) return InteractionResult.FAIL;
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!item.usedInAir()) return super.use(level, player, hand);
        if (player instanceof ServerPlayer server) item.useInAir(WrapperPlayer.of(server), new WrapperItemStack(stack));
        player.getCooldowns().addCooldown(this, 8);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
