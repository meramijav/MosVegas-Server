package ru.mosvegas.core.util;

import net.minecraft.block.AbstractSignBlock;
import net.minecraft.block.Block;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

public final class BlockUseGuard {
    private BlockUseGuard() {}

    public static boolean isBlocked(ServerPlayerEntity player, Hand hand, BlockPos pos) {
        if (player.isSpectator()) return false;
        if (player.shouldCancelInteraction() && !player.getStackInHand(hand).isEmpty()) return false;
        if (!isGuarded(player.getWorld().getBlockState(pos).getBlock())) return false;
        return !Permissions.hasAny(player, 2, "mosvegas.admin.interact", "mosvegas.admin");
    }

    public static boolean isGuarded(Block block) {
        return block instanceof AbstractSignBlock || block instanceof TrapdoorBlock;
    }
}
