package ru.mosvegas.core.service;

import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import ru.mosvegas.core.config.*;
import ru.mosvegas.core.data.*;
import ru.mosvegas.core.util.*;

import java.time.Duration;

public final class EconomyService {
    private static int ticks;
    private EconomyService() {}

    public static String money(long amount){return amount+" "+ConfigManager.get().currency;}

    public static boolean isBankTerminal(ServerPlayerEntity player,BlockPos pos){
        String dimension=player.getWorld().getRegistryKey().getValue().toString();
        for(MosvegasConfig.Point point:ConfigManager.get().bankTerminals)if(dimension.equals(point.dimension)&&pos.getX()==(int)Math.floor(point.x)&&pos.getY()==(int)Math.floor(point.y)&&pos.getZ()==(int)Math.floor(point.z))return true;
        return false;
    }

    public static boolean addBankTerminal(ServerPlayerEntity player,BlockPos pos){
        if(isBankTerminal(player,pos)){player.sendMessage(Text.literal("§eЭтот блок уже является банковским терминалом."));return false;}
        ConfigManager.get().bankTerminals.add(new MosvegasConfig.Point(player.getWorld().getRegistryKey().getValue().toString(),pos.getX(),pos.getY(),pos.getZ(),0));
        ConfigManager.save();player.sendMessage(Text.literal("§aБанковский терминал создан: "+pos.toShortString()));return true;
    }

    public static boolean removeBankTerminal(ServerPlayerEntity player,BlockPos pos){
        String dimension=player.getWorld().getRegistryKey().getValue().toString();
        boolean removed=ConfigManager.get().bankTerminals.removeIf(point->dimension.equals(point.dimension)&&pos.getX()==(int)Math.floor(point.x)&&pos.getY()==(int)Math.floor(point.y)&&pos.getZ()==(int)Math.floor(point.z));
        if(removed){ConfigManager.save();player.sendMessage(Text.literal("§eБанковский терминал удалён: "+pos.toShortString()));}
        return removed;
    }

    public static void onBlockBroken(ServerPlayerEntity player,BlockPos pos){if(isBankTerminal(player,pos))removeBankTerminal(player,pos);}

    public static boolean useBankTerminal(ServerPlayerEntity player,BlockPos pos){
        if(!isBankTerminal(player,pos))return false;
        if(!RpItems.isBankCard(player.getMainHandStack(),player)&&!RpItems.isBankCard(player.getOffHandStack(),player)){
            player.sendMessage(Text.literal("§cВставьте свою банковскую карту: возьмите её в руку."));return true;
        }
        openBank(player);return true;
    }

    public static void openCardMenu(ServerPlayerEntity player){
        Ui.menu(player,"Банковская карта",m->{
            m.label(13,Ui.item(Items.NAME_TAG,"Карта открывает банковские терминалы"));
            m.button(31,Ui.item(Items.EMERALD,"Получить / перевыпустить карту"),()->{
                if(DataStore.player(player.getUuid()).passport==null){player.sendMessage(Text.literal("§cСначала оформите паспорт в /mfc"));return;}
                RpItems.removeOwnBankCards(player);ItemsUtil.give(player,RpItems.bankCard(player));player.closeHandledScreen();player.sendMessage(Text.literal("§aБанковская карта выдана."));
            });
        });
    }

    public static void openBank(ServerPlayerEntity player){
        PlayerData d=DataStore.player(player.getUuid());
        Ui.menu(player,"Банк",m->{
            m.label(4,Ui.item(Items.GOLD_INGOT,"Счёт: "+money(d.bankBalance)));
            m.label(13,Ui.item(Items.PAPER,"Наличные в инвентаре: "+money(RpItems.cashBalance(player))));
            m.button(20,Ui.item(Items.EMERALD,"Снять наличные"),()->openWithdraw(player));
            m.button(22,Ui.item(Items.CHEST,"Внести наличные"),()->openDeposit(player));
            m.button(24,Ui.item(Items.BARREL,"Внести все наличные"),()->depositAll(player));
            m.button(40,Ui.item(Items.WRITABLE_BOOK,"Перевести игроку"),()->transferInput(player));
        });
    }

    private static void openWithdraw(ServerPlayerEntity player){Ui.menu(player,"Снять наличные",m->{int slot=9;for(long value:RpItems.configuredDenominations()){int s=slot++;m.button(s,Ui.item(Items.EMERALD,"Снять "+money(value)),()->withdraw(player,value));}m.button(40,Ui.item(Items.WRITABLE_BOOK,"Снять другую сумму"),()->moneyInput(player,true));m.button(49,Ui.item(Items.ARROW,"Назад"),()->openBank(player));});}
    private static void openDeposit(ServerPlayerEntity player){Ui.menu(player,"Внести наличные",m->{int slot=9;for(long value:RpItems.configuredDenominations()){int s=slot++;m.button(s,Ui.item(Items.CHEST,"Внести "+money(value)),()->deposit(player,value));}m.button(40,Ui.item(Items.WRITABLE_BOOK,"Внести другую сумму"),()->moneyInput(player,false));m.button(49,Ui.item(Items.ARROW,"Назад"),()->openBank(player));});}
    private static void moneyInput(ServerPlayerEntity player,boolean withdraw){Ui.input(player,withdraw?"Сумма снятия":"Сумма внесения","5000",text->{try{long amount=Long.parseLong(text.replace(" ",""));if(withdraw)withdraw(player,amount);else deposit(player,amount);}catch(NumberFormatException e){player.sendMessage(Text.literal("§cВведите целую положительную сумму."));}});}

    private static void transferInput(ServerPlayerEntity player){Ui.input(player,"Имя получателя","Ник игрока",name->Ui.input(player,"Сумма перевода","1000",text->{try{long amount=Long.parseLong(text.replace(" ",""));ServerPlayerEntity target=player.getServer().getPlayerManager().getPlayer(name);if(target==null){player.sendMessage(Text.literal("§cПолучатель должен быть онлайн."));return;}transfer(player,target,amount);}catch(NumberFormatException e){player.sendMessage(Text.literal("§cВведите целую положительную сумму."));}}));}

    public static boolean debit(ServerPlayerEntity player,long amount){PlayerData d=DataStore.player(player.getUuid());if(amount<=0||d.bankBalance<amount)return false;d.bankBalance-=amount;DataStore.save();return true;}
    public static void credit(ServerPlayerEntity player,long amount){if(amount<=0)return;DataStore.player(player.getUuid()).bankBalance+=amount;DataStore.save();}

    public static void withdraw(ServerPlayerEntity player,long amount){
        if(!Permissions.has(player,"mosvegas.bank.withdraw",0)){player.sendMessage(Text.literal("§cНет права mosvegas.bank.withdraw"));return;}
        if(!debit(player,amount)){player.sendMessage(Text.literal("§cНедостаточно денег на счёте."));return;}
        if(!RpItems.giveCash(player,amount)){credit(player,amount);player.sendMessage(Text.literal("§cСумма не раскладывается на доступные номиналы."));return;}
        player.sendMessage(Text.literal("§aСнято наличными: "+money(amount)));openBank(player);
    }

    public static void deposit(ServerPlayerEntity player,long amount){
        if(!Permissions.has(player,"mosvegas.bank.deposit",0)){player.sendMessage(Text.literal("§cНет права mosvegas.bank.deposit"));return;}
        if(!RpItems.takeCash(player,amount)){player.sendMessage(Text.literal("§cВ инвентаре нет подходящих купюр на "+money(amount)+"."));return;}
        credit(player,amount);player.sendMessage(Text.literal("§aВнесено: "+money(amount)));openBank(player);
    }

    public static void depositAll(ServerPlayerEntity player){
        if(!Permissions.has(player,"mosvegas.bank.deposit",0)){player.sendMessage(Text.literal("§cНет права mosvegas.bank.deposit"));return;}
        long amount=RpItems.takeAllCash(player);if(amount<=0){player.sendMessage(Text.literal("§eВ инвентаре нет наличных."));return;}credit(player,amount);player.sendMessage(Text.literal("§aВнесено: "+money(amount)));openBank(player);
    }

    public static void migrateLegacyCash(ServerPlayerEntity player){PlayerData d=DataStore.player(player.getUuid());if(d.cash<=0)return;long amount=d.cash;if(RpItems.giveCash(player,amount)){d.cash=0;DataStore.save();player.sendMessage(Text.literal("§eСтарые наличные перенесены в физические купюры: "+money(amount)));}}

    public static void transfer(ServerPlayerEntity from,ServerPlayerEntity to,long amount){if(!Permissions.has(from,"mosvegas.bank.transfer",0)){from.sendMessage(Text.literal("§cНет права mosvegas.bank.transfer"));return;}if(amount<=0||from.getUuid().equals(to.getUuid())){from.sendMessage(Text.literal("§cНекорректный перевод."));return;}if(!debit(from,amount)){from.sendMessage(Text.literal("§cНедостаточно денег."));return;}credit(to,amount);from.sendMessage(Text.literal("§aПереведено "+money(amount)+" игроку "+to.getName().getString()));to.sendMessage(Text.literal("§aПолучен перевод "+money(amount)+" от "+from.getName().getString()));}

    public static void openSalary(ServerPlayerEntity player){PlayerData d=DataStore.player(player.getUuid());long interval=ConfigManager.get().salaryIntervalSeconds*1000L;long remaining=Math.max(0,interval-(System.currentTimeMillis()-d.lastSalaryAt));Ui.menu(player,"Автоматическая зарплата",m->{m.label(13,Ui.item(Items.GOLD_INGOT,"Начисление: "+money(salaryFor(d))));m.label(22,Ui.item(Items.PAPER,"Налог: "+ConfigManager.get().taxPercent+"%"));m.label(31,Ui.item(Items.CLOCK,"До автозачисления: "+formatDuration(remaining)));});}

    public static void tick(MinecraftServer server){if(++ticks%20!=0)return;long now=System.currentTimeMillis();long interval=Math.max(1000,ConfigManager.get().salaryIntervalSeconds*1000L);for(ServerPlayerEntity player:server.getPlayerManager().getPlayerList()){PlayerData d=DataStore.player(player.getUuid());if(now-d.lastSalaryAt>=interval)paySalary(player,d,now);}}
    private static void paySalary(ServerPlayerEntity player,PlayerData d,long now){long gross=salaryFor(d);int percent=Permissions.has(player,"mosvegas.tax.bypass",2)?0:ConfigManager.get().taxPercent;long tax=Math.max(0,gross*percent/100);long net=Math.max(0,gross-tax);d.bankBalance+=net;d.lastSalaryAt=now;DataStore.save();player.sendMessage(Text.literal("§aЗарплата начислена автоматически: "+money(net)+(tax>0?" (налог "+money(tax)+")":"")));}
    private static long salaryFor(PlayerData d){String level=String.valueOf(d.faction==null?0:Math.max(1,Math.min(5,d.factionLevel)));return ConfigManager.get().salaryByFactionLevel.getOrDefault(level,0L);}
    private static String formatDuration(long millis){Duration d=Duration.ofMillis(Math.max(0,millis));long minutes=d.toMinutes();long seconds=d.minusMinutes(minutes).toSeconds();return minutes+"м "+seconds+"с";}
}
