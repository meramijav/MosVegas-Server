package ru.mosvegas.core.command;

import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import ru.mosvegas.core.config.*;
import ru.mosvegas.core.data.DataStore;
import ru.mosvegas.core.service.*;
import ru.mosvegas.core.util.Permissions;

import static net.minecraft.server.command.CommandManager.*;

public final class Commands {
    private Commands() {}

    public static void register(){CommandRegistrationCallback.EVENT.register((dispatcher,registryAccess,environment)->{
        dispatcher.register(literal("mfc").requires(s->Permissions.has(s,"mosvegas.command.mfc",0)).executes(c->{IdentityService.openMfc(c.getSource().getPlayerOrThrow());return 1;}));
        dispatcher.register(literal("driverlicence").requires(s->Permissions.has(s,"mosvegas.command.driverlicence",0)).executes(c->{IdentityService.openLicence(c.getSource().getPlayerOrThrow(),"car");return 1;}));
        dispatcher.register(literal("weaponlicence").requires(s->Permissions.has(s,"mosvegas.command.weaponlicence",0)).executes(c->{IdentityService.openLicence(c.getSource().getPlayerOrThrow(),"weapon");return 1;}));
        dispatcher.register(literal("bankcard").requires(s->Permissions.has(s,"mosvegas.command.bankcard",0)).executes(c->{EconomyService.openCardMenu(c.getSource().getPlayerOrThrow());return 1;}));
        dispatcher.register(literal("salary").requires(s->Permissions.has(s,"mosvegas.command.salary",0)).executes(c->{EconomyService.openSalary(c.getSource().getPlayerOrThrow());return 1;}));

        dispatcher.register(literal("quests").requires(s->Permissions.has(s,"mosvegas.command.quests",0)).executes(c->{QuestService.open(c.getSource().getPlayerOrThrow());return 1;})
                .then(literal("start").then(argument("id",StringArgumentType.word())
                        .suggests((context,builder)->CommandSource.suggestMatching(ConfigManager.get().quests.stream().map(q->q.id),builder))
                        .executes(c->{QuestService.startById(c.getSource().getPlayerOrThrow(),StringArgumentType.getString(c,"id"));return 1;})))
                .then(literal("admin").requires(s->Permissions.hasAny(s,3,"mosvegas.admin.quests.manage","mosvegas.admin.quests")).executes(c->{QuestService.openAdmin(c.getSource().getPlayerOrThrow());return 1;})));

        dispatcher.register(literal("faction").requires(s->Permissions.has(s,"mosvegas.command.faction",0)).executes(c->{FactionService.open(c.getSource().getPlayerOrThrow());return 1;})
                .then(literal("admin").requires(s->Permissions.hasAny(s,3,"mosvegas.admin.factions.manage","mosvegas.admin.factions")).executes(c->{FactionService.openAdmin(c.getSource().getPlayerOrThrow());return 1;}))
                .then(levelBranch("level",false)).then(levelBranch("hierarchy",true)));

        dispatcher.register(literal("serverlevel").requires(s->Permissions.hasAny(s,3,"mosvegas.admin.serverlevel","mosvegas.admin.levels"))
                .then(literal("set").then(targetAndAmount((p,n)->ProgressService.setServerLevel(p,n))))
                .then(literal("give").then(targetAndAmount((p,n)->ProgressService.changeServerLevel(p,n))))
                .then(literal("take").then(targetAndAmount((p,n)->ProgressService.changeServerLevel(p,-n)))));

        dispatcher.register(literal("rpplaceholder").then(argument("id",StringArgumentType.word()).executes(c->showPlaceholder(c,c.getSource().getPlayerOrThrow()))
                .then(argument("player",EntityArgumentType.player()).requires(s->Permissions.hasAny(s,3,"mosvegas.admin.placeholders","mosvegas.admin")).executes(c->showPlaceholder(c,EntityArgumentType.getPlayer(c,"player"))))));

        dispatcher.register(literal("property").requires(s->Permissions.has(s,"mosvegas.command.property",0)).executes(c->{PropertyService.open(c.getSource().getPlayerOrThrow());return 1;})
                .then(literal("wand").requires(s->Permissions.hasAny(s,3,"mosvegas.admin.properties.wand","mosvegas.admin.properties")).executes(c->{PropertyService.giveWand(c.getSource().getPlayerOrThrow());return 1;}))
                .then(literal("admin").requires(s->Permissions.hasAny(s,3,"mosvegas.admin.properties.view","mosvegas.admin.properties")).executes(c->{PropertyService.openAdmin(c.getSource().getPlayerOrThrow());return 1;})));

        dispatcher.register(literal("mosvegas")
                .then(ShopCommands.shop()).then(ShopCommands.admin()).then(ZoneCommands.branch())
                .then(literal("reload").requires(s->Permissions.hasAny(s,3,"mosvegas.admin.config","mosvegas.admin")).executes(c->ShopCommands.run(c,()->{ru.mosvegas.core.zone.ZoneStore.reload();ru.mosvegas.core.shop.ShopStore.reload();ConfigManager.load();c.getSource().sendFeedback(()->Text.literal("Mosvegas Core: конфиг, зоны и магазины перезагружены."),true);})))
                .then(literal("save").requires(s->Permissions.hasAny(s,3,"mosvegas.admin.config","mosvegas.admin")).executes(c->{ConfigManager.save();DataStore.flush();c.getSource().sendFeedback(()->Text.literal("Mosvegas Core: данные сохранены."),false);return 1;}))
                .then(literal("bankterminal").requires(s->Permissions.hasAny(s,3,"mosvegas.admin.bankterminal","mosvegas.admin")).then(literal("add").executes(c->bankTerminal(c.getSource(),true))).then(literal("remove").executes(c->bankTerminal(c.getSource(),false))))
                .then(moneyBranch())
                .then(literal("permission").requires(s->Permissions.hasAny(s,3,"mosvegas.admin.permissions","mosvegas.admin")).then(literal("grant").then(argument("player",EntityArgumentType.player()).then(argument("node",StringArgumentType.word()).executes(c->updatePermission(c,true)))))
                        .then(literal("revoke").then(argument("player",EntityArgumentType.player()).then(argument("node",StringArgumentType.word()).executes(c->updatePermission(c,false)))))));
    });}

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> levelBranch(String name,boolean hierarchy){
        String specific=hierarchy?"mosvegas.admin.factions.hierarchy":"mosvegas.admin.factions.level";
        var root=literal(name).requires(s->Permissions.hasAny(s,3,specific,"mosvegas.admin.factions"));
        root.then(literal("set").then(targetAndAmount((p,n)->{if(hierarchy)FactionService.setHierarchyLevel(p,n);else FactionService.setFactionLevel(p,n);}))); 
        root.then(literal("give").then(targetAndAmount((p,n)->{if(hierarchy)FactionService.changeHierarchyLevel(p,n);else FactionService.changeFactionLevel(p,n);}))); 
        root.then(literal("take").then(targetAndAmount((p,n)->{if(hierarchy)FactionService.changeHierarchyLevel(p,-n);else FactionService.changeFactionLevel(p,-n);}))); 
        return root;
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<ServerCommandSource,net.minecraft.command.EntitySelector> targetAndAmount(LevelAction action){
        return argument("player",EntityArgumentType.player()).then(argument("amount",IntegerArgumentType.integer(0,100000)).executes(c->{ServerPlayerEntity p=EntityArgumentType.getPlayer(c,"player");int n=IntegerArgumentType.getInteger(c,"amount");action.apply(p,n);c.getSource().sendFeedback(()->Text.literal("Уровень игрока "+p.getName().getString()+" обновлён."),true);return 1;}));
    }
    @FunctionalInterface private interface LevelAction{void apply(ServerPlayerEntity player,int amount);}

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> moneyBranch(){
        var root=literal("money").requires(s->Permissions.hasAny(s,3,"mosvegas.admin.money","mosvegas.admin"));
        // A negative result means the change is impossible: not enough money to take, or the balance would overflow.
        root.then(literal("give").then(playerAndSum(1,(balance,n)->balance>Long.MAX_VALUE-n?-1:balance+n)));
        root.then(literal("take").then(playerAndSum(1,(balance,n)->balance<n?-1:balance-n)));
        root.then(literal("set").then(playerAndSum(0,(balance,n)->n)));
        return root;
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<ServerCommandSource,net.minecraft.command.EntitySelector> playerAndSum(long min,java.util.function.LongBinaryOperator change){
        return argument("player",EntityArgumentType.player()).then(argument("amount",LongArgumentType.longArg(min)).executes(c->{
            ServerPlayerEntity p=EntityArgumentType.getPlayer(c,"player");String name=p.getName().getString();var d=DataStore.player(p.getUuid());
            long balance=change.applyAsLong(d.bankBalance,LongArgumentType.getLong(c,"amount"));
            if(balance<0){c.getSource().sendError(Text.literal("Операция невозможна: на счёте игрока "+name+" сейчас "+EconomyService.money(d.bankBalance)+"."));return 0;}
            d.bankBalance=balance;DataStore.save();
            c.getSource().sendFeedback(()->Text.literal("Счёт игрока "+name+": "+EconomyService.money(balance)+"."),true);return 1;}));
    }

    private static int showPlaceholder(CommandContext<ServerCommandSource> c,ServerPlayerEntity player){String id=StringArgumentType.getString(c,"id");c.getSource().sendFeedback(()->Text.literal(id+" = "+PlaceholderService.resolve(player,id)),false);return 1;}
    private static int bankTerminal(ServerCommandSource source,boolean add) throws com.mojang.brigadier.exceptions.CommandSyntaxException {ServerPlayerEntity p=source.getPlayerOrThrow();var hit=p.raycast(6,0,false);if(!(hit instanceof net.minecraft.util.hit.BlockHitResult blockHit)){source.sendError(Text.literal("Наведитесь на банковский блок."));return 0;}return (add?EconomyService.addBankTerminal(p,blockHit.getBlockPos()):EconomyService.removeBankTerminal(p,blockHit.getBlockPos()))?1:0;}
    private static int updatePermission(CommandContext<ServerCommandSource> c,boolean grant) throws com.mojang.brigadier.exceptions.CommandSyntaxException {ServerPlayerEntity target=EntityArgumentType.getPlayer(c,"player");String node=StringArgumentType.getString(c,"node");if(grant)Permissions.grant(target.getUuid(),node);else Permissions.revoke(target.getUuid(),node);c.getSource().sendFeedback(()->Text.literal((grant?"Выдано ":"Отозвано ")+node+": "+target.getName().getString()),true);return 1;}
}
