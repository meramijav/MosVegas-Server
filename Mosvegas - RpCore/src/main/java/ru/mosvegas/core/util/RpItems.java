package ru.mosvegas.core.util;

import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import ru.mosvegas.core.config.ConfigManager;
import ru.mosvegas.core.data.DataStore;

import java.util.*;

/** NBT-backed physical RP items. The keys are stable API and may be reused by addon mods. */
public final class RpItems {
    public static final String CASH_VALUE = "mosvegas_cash_value";
    public static final String BANK_CARD = "mosvegas_bank_card";
    public static final String FACTION = "mosvegas_faction";

    private RpItems() {}

    public static ItemStack bankCard(ServerPlayerEntity owner) {
        ItemStack stack=new ItemStack(Items.NAME_TAG);
        stack.setCustomName(Text.literal("§bБанковская карта"));
        stack.getOrCreateNbt().putUuid(BANK_CARD,owner.getUuid());
        return stack;
    }

    public static boolean isBankCard(ItemStack stack, ServerPlayerEntity player) {
        NbtCompound nbt=stack.getNbt();
        return nbt!=null&&nbt.containsUuid(BANK_CARD)&&player.getUuid().equals(nbt.getUuid(BANK_CARD));
    }

    public static void removeOwnBankCards(ServerPlayerEntity player) {
        for(int i=0;i<player.getInventory().size();i++){
            ItemStack stack=player.getInventory().getStack(i);
            if(isBankCard(stack,player)) player.getInventory().setStack(i,ItemStack.EMPTY);
        }
    }

    public static ItemStack cash(long value, int count) {
        ItemStack stack=new ItemStack(Items.PAPER,count);
        stack.setCustomName(Text.literal("§aНаличные: "+value+" "+ConfigManager.get().currency));
        stack.getOrCreateNbt().putLong(CASH_VALUE,value);
        return stack;
    }

    public static long cashValue(ItemStack stack) {
        NbtCompound nbt=stack.getNbt();
        return nbt!=null&&nbt.contains(CASH_VALUE)?Math.max(0,nbt.getLong(CASH_VALUE)):0;
    }

    public static long cashBalance(ServerPlayerEntity player) {
        long total=0;
        for(int i=0;i<player.getInventory().size();i++){
            ItemStack stack=player.getInventory().getStack(i); long value=cashValue(stack);
            if(value>0){long add=value>Long.MAX_VALUE/Math.max(1,stack.getCount())?Long.MAX_VALUE:value*stack.getCount();total=Long.MAX_VALUE-total<add?Long.MAX_VALUE:total+add;}
        }
        return total;
    }

    public static boolean giveCash(ServerPlayerEntity player,long amount) {
        if(amount<=0)return false;
        List<Long> values=denominations(); long left=amount;
        for(long value:values){int count=(int)Math.min(Integer.MAX_VALUE,left/value);left%=value;while(count>0){int batch=Math.min(64,count);ItemsUtil.give(player,cash(value,batch));count-=batch;}}
        return left==0;
    }

    public static boolean takeCash(ServerPlayerEntity player,long amount) {
        if(amount<=0)return false;
        Map<Long,Integer> available=new HashMap<>();
        for(int i=0;i<player.getInventory().size();i++){ItemStack s=player.getInventory().getStack(i);long v=cashValue(s);if(v>0)available.merge(v,s.getCount(),Integer::sum);}
        Map<Long,Integer> needed=new HashMap<>();long left=amount;
        for(long value:denominations()){int count=(int)Math.min(available.getOrDefault(value,0),left/value);if(count>0){needed.put(value,count);left-=value*count;}}
        if(left!=0)return false;
        for(int i=0;i<player.getInventory().size();i++){ItemStack s=player.getInventory().getStack(i);long v=cashValue(s);int need=needed.getOrDefault(v,0);if(need<=0)continue;int remove=Math.min(need,s.getCount());s.decrement(remove);needed.put(v,need-remove);}
        return true;
    }

    public static long takeAllCash(ServerPlayerEntity player) {
        long amount=cashBalance(player);
        for(int i=0;i<player.getInventory().size();i++)if(cashValue(player.getInventory().getStack(i))>0)player.getInventory().setStack(i,ItemStack.EMPTY);
        return amount;
    }

    private static List<Long> denominations(){List<Long> values=new ArrayList<>(ConfigManager.get().cashDenominations);values.removeIf(v->v==null||v<=0);values.sort(Comparator.reverseOrder());return values;}
    public static List<Long> configuredDenominations(){return List.copyOf(denominations());}

    public static boolean isForeignFactionItem(ServerPlayerEntity player,ItemStack stack) {
        NbtCompound nbt=stack.getNbt(); if(nbt==null||!nbt.contains(FACTION))return false;
        return !Objects.equals(DataStore.player(player.getUuid()).faction,nbt.getString(FACTION));
    }
}
