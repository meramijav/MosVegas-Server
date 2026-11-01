package ru.mosvegas.core;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.event.player.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.TypedActionResult;
import ru.mosvegas.core.util.RpItems;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.mosvegas.core.command.Commands;
import ru.mosvegas.core.config.ConfigManager;
import ru.mosvegas.core.data.*;
import ru.mosvegas.core.service.*;
import ru.mosvegas.core.zone.*;
import ru.mosvegas.core.shop.ShopStore;
import ru.mosvegas.core.command.ZoneCommands;
import ru.mosvegas.core.integration.TaczIntegration;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

public final class MosvegasCore implements ModInitializer {
    public static final String MOD_ID="mosvegas_core";
    public static final Logger LOGGER= LoggerFactory.getLogger("Mosvegas Core");
    private int autosaveTicks;

    @Override public void onInitialize(){
        ConfigManager.load();
        Commands.register();
        ServerLifecycleEvents.SERVER_STARTING.register(server->{ZoneStore.start(server);ShopStore.start(server);});
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{ZoneService.clearSession();ZoneCommands.clear();});
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{ZoneService.disconnect(handler.player);ZoneCommands.disconnect(handler.player);});
        TaczIntegration.register();
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity,source,amount)->ZoneService.damage(entity,source));
        ServerLifecycleEvents.SERVER_STARTED.register(DataStore::load);
        ServerLifecycleEvents.SERVER_STOPPING.register(server->DataStore.flush());
        ServerTickEvents.END_SERVER_TICK.register(server->{
            QuestService.tick(server);
            EconomyService.tick(server);
            ProgressService.tick(server);
            PropertyService.tick(server);
            if(++autosaveTicks>=1200){autosaveTicks=0;DataStore.flush();}
        });
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->{
            ServerPlayerEntity player=handler.player;
            PlayerData data=DataStore.player(player.getUuid());
            EconomyService.migrateLegacyCash(player);
            if(data.faction!=null){
                var faction=FactionService.find(data.faction);
                if(faction==null){data.faction=null;data.factionLevel=0;data.hierarchyLevel=0;data.factionXp=0;DataStore.save();}
                else FactionService.giveUniqueItems(player,faction);
            }
        });
        UseEntityCallback.EVENT.register((player,world,hand,entity,hitResult)->{
            if(!world.isClient&&player instanceof ServerPlayerEntity serverPlayer){
                if(!ZoneService.entityUse(serverPlayer,entity))return ActionResult.FAIL;
                if(RpItems.isForeignFactionItem(serverPlayer,player.getStackInHand(hand))){serverPlayer.sendMessage(net.minecraft.text.Text.literal("§cЧужой фракционный предмет использовать нельзя."));return ActionResult.FAIL;}
                if(QuestService.onUseEntity(serverPlayer,entity))return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });
        UseBlockCallback.EVENT.register((player,world,hand,hit)->{
            if(world.isClient||!(player instanceof ServerPlayerEntity serverPlayer))return ActionResult.PASS;
            if(RpItems.isForeignFactionItem(serverPlayer,player.getStackInHand(hand)))return ActionResult.FAIL;
            if(PropertyService.isWand(player.getStackInHand(hand))&&PropertyService.selectSecond(serverPlayer,hit.getBlockPos()))return ActionResult.SUCCESS;
            if(PropertyService.offerAtDoor(serverPlayer,hand,hit.getBlockPos()))return ActionResult.FAIL;
            if(!ZoneService.useBlock(serverPlayer,hit.getBlockPos()))return ActionResult.FAIL;
            var held=player.getStackInHand(hand);
            if(ZoneService.changesBlocks(held)&&!(held.getItem() instanceof net.minecraft.item.BlockItem)&&!(held.getItem() instanceof net.minecraft.item.BucketItem)
                    &&(!ZoneService.check(serverPlayer,hit.getBlockPos(),ZoneAction.BUILD)||!ZoneService.check(serverPlayer,hit.getBlockPos().offset(hit.getSide()),ZoneAction.BUILD)))return ActionResult.FAIL;
            if(ZoneService.isProjectile(held)&&!ZoneService.check(serverPlayer,serverPlayer.getBlockPos(),ZoneAction.SHOOT))return ActionResult.FAIL;
            if(EconomyService.useBankTerminal(serverPlayer,hit.getBlockPos()))return ActionResult.SUCCESS;
            return ActionResult.PASS;
        });
        UseItemCallback.EVENT.register((player,world,hand)->{
            if(!world.isClient&&player instanceof ServerPlayerEntity serverPlayer&&RpItems.isForeignFactionItem(serverPlayer,player.getStackInHand(hand)))return TypedActionResult.fail(player.getStackInHand(hand));
            if(!world.isClient&&player instanceof ServerPlayerEntity serverPlayer&&ZoneService.isProjectile(player.getStackInHand(hand))&&!ZoneService.check(serverPlayer,player.getBlockPos(),ZoneAction.SHOOT))return TypedActionResult.fail(player.getStackInHand(hand));
            return TypedActionResult.pass(player.getStackInHand(hand));
        });
        AttackBlockCallback.EVENT.register((player,world,hand,pos,direction)->{
            if(world.isClient||!(player instanceof ServerPlayerEntity serverPlayer))return ActionResult.PASS;
            if(RpItems.isForeignFactionItem(serverPlayer,player.getStackInHand(hand)))return ActionResult.FAIL;
            if(PropertyService.selectFirst(serverPlayer,pos))return ActionResult.SUCCESS;
            if(!ZoneService.check(serverPlayer,pos,ZoneAction.BUILD))return ActionResult.FAIL;
            return ActionResult.PASS;
        });
        AttackEntityCallback.EVENT.register((player,world,hand,entity,hit)->{
            if(!world.isClient&&player instanceof ServerPlayerEntity serverPlayer){
                if(RpItems.isForeignFactionItem(serverPlayer,player.getStackInHand(hand)))return ActionResult.FAIL;
                if(entity instanceof net.minecraft.entity.player.PlayerEntity){
                    if(!ZoneService.check(serverPlayer,player.getBlockPos(),ZoneAction.PVP)||!ZoneService.check(serverPlayer,entity.getBlockPos(),ZoneAction.PVP))return ActionResult.FAIL;
                }else if(!ZoneService.entityUse(serverPlayer,entity))return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });
        PlayerBlockBreakEvents.BEFORE.register((world,player,pos,state,blockEntity)->{
            if(!(player instanceof ServerPlayerEntity serverPlayer))return true;
            if(!ZoneService.check(serverPlayer,pos,ZoneAction.BUILD))return false;
            if(!PropertyService.canBreak(serverPlayer,pos))return false;
            EconomyService.onBlockBroken(serverPlayer,pos);
            return true;
        });
        LOGGER.info("Mosvegas Core загружен. Конфиг: {}",ConfigManager.path());
    }
}
