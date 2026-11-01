package ru.mosvegas.core.service;

import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import ru.mosvegas.core.config.ConfigManager;
import ru.mosvegas.core.data.*;
import ru.mosvegas.core.util.RpItems;

/** Online-time level and continuous enforcement of faction-owned items. */
public final class ProgressService {
    private static int ticks;
    private ProgressService() {}

    public static void tick(MinecraftServer server){
        if(++ticks%20!=0)return;
        long interval=Math.max(20L,ConfigManager.get().serverLevelIntervalSeconds*20L);
        for(ServerPlayerEntity player:server.getPlayerManager().getPlayerList()){
            PlayerData d=DataStore.player(player.getUuid());d.serverLevelTicks+=20;
            while(d.serverLevelTicks>=interval){d.serverLevelTicks-=interval;d.serverLevel++;player.sendMessage(Text.literal("§6Серверный уровень повышен: "+d.serverLevel));}
            boolean removed=false;
            for(int i=0;i<player.getInventory().size();i++){ItemStack stack=player.getInventory().getStack(i);if(RpItems.isForeignFactionItem(player,stack)){player.getInventory().setStack(i,ItemStack.EMPTY);removed=true;}}
            if(removed)player.sendMessage(Text.literal("§cЧужие фракционные предметы нельзя подбирать и использовать."));
            DataStore.save();
        }
    }

    public static void changeServerLevel(ServerPlayerEntity player,int delta){PlayerData d=DataStore.player(player.getUuid());d.serverLevel=Math.max(0,d.serverLevel+delta);DataStore.save();}
    public static void setServerLevel(ServerPlayerEntity player,int value){PlayerData d=DataStore.player(player.getUuid());d.serverLevel=Math.max(0,value);d.serverLevelTicks=0;DataStore.save();}
}
