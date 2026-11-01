package studio.merami.sliminterfacefabric.event;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import studio.merami.slimcore.systems.SeatedInteractions;
import studio.merami.sliminterfacefabric.entity.BuilderVehicle;
import studio.merami.sliminterfacefabric.wrapper.WrapperPlayer;

public final class SeatedInteractionEvents {
    private SeatedInteractionEvents() {}

    private static boolean seated(Player player) {
        return SeatedInteractions.seated(WrapperPlayer.of(player));
    }

    public static void register() {
        AttackBlockCallback.EVENT.register((player, world, hand, pos, face) -> seated(player) ? InteractionResult.FAIL : InteractionResult.PASS);
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> seated(player) ? InteractionResult.FAIL : InteractionResult.PASS);
        UseItemCallback.EVENT.register((player, world, hand) -> seated(player)
                ? InteractionResultHolder.fail(player.getItemInHand(hand)) : InteractionResultHolder.pass(player.getItemInHand(hand)));
        AttackEntityCallback.EVENT.register((player, world, hand, target, hit) -> seated(player) ? InteractionResult.FAIL : InteractionResult.PASS);
        UseEntityCallback.EVENT.register((player, world, hand, target, hit) -> SeatedInteractions.canUseEntity(WrapperPlayer.of(player),
                target instanceof BuilderVehicle vehicle ? vehicle.vehicle() : null) ? InteractionResult.PASS : InteractionResult.FAIL);
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, entity) -> !seated(player));
    }
}
