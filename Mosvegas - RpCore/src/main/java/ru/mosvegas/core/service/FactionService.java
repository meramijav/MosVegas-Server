package ru.mosvegas.core.service;

import net.minecraft.item.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import ru.mosvegas.core.config.*;
import ru.mosvegas.core.data.*;
import ru.mosvegas.core.util.*;

import java.util.*;

public final class FactionService {
    private FactionService() {}

    public static MosvegasConfig.Faction find(String id) {
        return ConfigManager.faction(id);
    }

    public static void open(ServerPlayerEntity player) {
        PlayerData d = DataStore.player(player.getUuid());
        Ui.menu(player, "Фракции", m -> {
            if (d.faction != null) {
                MosvegasConfig.Faction faction = find(d.faction);
                String name = faction == null ? d.faction : faction.name;
                m.label(4, Ui.item(Items.NETHER_STAR, name + " | уровень " + d.factionLevel + "/5 | иерархия " + d.hierarchyLevel));
                m.button(49, Ui.item(Items.BARRIER, "Покинуть фракцию (прогресс сбросится)"), () -> leave(player));
            } else m.label(4, Ui.item(Items.PAPER, "Вы не состоите во фракции"));
            int slot=10;
            for (MosvegasConfig.Faction faction : ConfigManager.get().factions) {
                int current = slot++;
                m.button(current, Ui.item(Items.SHIELD, faction.name + " — " + faction.description), () -> join(player, faction));
                if (slot%9==8) slot+=2;
                if (slot>=45) break;
            }
        });
    }

    public static void join(ServerPlayerEntity player, MosvegasConfig.Faction faction) {
        PlayerData d = DataStore.player(player.getUuid());
        if (d.faction != null) { player.sendMessage(Text.literal("§cСначала покиньте текущую фракцию.")); return; }
        String node = faction.permission == null || faction.permission.isBlank() ? "mosvegas.faction." + faction.id : faction.permission;
        if (!Permissions.has(player, node, 0)) { player.sendMessage(Text.literal("§cНет права " + node)); return; }
        d.faction=faction.id; d.factionLevel=1; d.hierarchyLevel=1; d.factionXp=0; d.activeQuests.clear(); d.completedFactionQuests.clear();
        giveUniqueItems(player, faction);
        DataStore.save();
        player.sendMessage(Text.literal("§aВы вступили во фракцию «" + faction.name + "»."));
        open(player);
    }

    public static void leave(ServerPlayerEntity player) {
        PlayerData d = DataStore.player(player.getUuid());
        if (d.faction == null) return;
        clearFactionItems(player);
        d.faction=null; d.factionLevel=0; d.hierarchyLevel=0; d.factionXp=0; d.activeQuests.clear(); d.completedFactionQuests.clear();
        DataStore.save();
        player.closeHandledScreen();
        player.sendMessage(Text.literal("§eВы покинули фракцию. Внутренний прогресс сброшен."));
    }

    public static void giveUniqueItems(ServerPlayerEntity player, MosvegasConfig.Faction faction) {
        for (String itemId : faction.uniqueItems) {
            Item item = ItemsUtil.byId(itemId);
            if (item == Items.AIR) continue;
            boolean alreadyPresent = false;
            for (int i = 0; i < player.getInventory().size(); i++) {
                ItemStack existing = player.getInventory().getStack(i);
                if (existing.isOf(item) && existing.hasNbt()
                        && faction.id.equals(existing.getNbt().getString("mosvegas_faction"))) {
                    alreadyPresent = true;
                    break;
                }
            }
            if (alreadyPresent) continue;
            ItemStack stack = new ItemStack(item);
            stack.setCustomName(Text.literal("Предмет фракции: " + faction.name));
            stack.getOrCreateNbt().putString(RpItems.FACTION, faction.id);
            ItemsUtil.give(player, stack);
        }
    }

    private static void clearFactionItems(ServerPlayerEntity player) {
        for (int i=0; i<player.getInventory().size(); i++) {
            ItemStack stack=player.getInventory().getStack(i);
            if (stack.hasNbt() && stack.getNbt().contains("mosvegas_faction")) player.getInventory().setStack(i, ItemStack.EMPTY);
        }
    }

    public static void addXp(ServerPlayerEntity player, int amount) {
        PlayerData d=DataStore.player(player.getUuid());
        if (d.faction==null) return;
        d.factionXp += Math.max(0, amount);
        DataStore.save();
    }

    public static void setFactionLevel(ServerPlayerEntity player,int value){PlayerData d=DataStore.player(player.getUuid());d.factionLevel=d.faction==null?0:Math.max(1,Math.min(5,value));d.factionXp=0;DataStore.save();}
    public static void changeFactionLevel(ServerPlayerEntity player,int delta){setFactionLevel(player,DataStore.player(player.getUuid()).factionLevel+delta);}
    public static void setHierarchyLevel(ServerPlayerEntity player,int value){PlayerData d=DataStore.player(player.getUuid());d.hierarchyLevel=d.faction==null?0:Math.max(1,Math.min(100,value));DataStore.save();}
    public static void changeHierarchyLevel(ServerPlayerEntity player,int delta){setHierarchyLevel(player,DataStore.player(player.getUuid()).hierarchyLevel+delta);}

    public static void openAdmin(ServerPlayerEntity player) {
        if (!Permissions.hasAny(player,3,"mosvegas.admin.factions.manage","mosvegas.admin.factions")) { player.sendMessage(Text.literal("§cНет прав администратора фракций.")); return; }
        Ui.menu(player, "Админ: фракции", m -> {
            m.button(49, Ui.item(Items.LIME_DYE, "Создать фракцию"), () -> createWizard(player));
            int slot=10;
            for (MosvegasConfig.Faction f:ConfigManager.get().factions) {
                m.label(slot++, Ui.item(Items.SHIELD, f.id + ": " + f.name));
                if(slot>=45) break;
            }
        });
    }

    private static void createWizard(ServerPlayerEntity player) {
        Ui.input(player, "ID фракции", "new_faction", id -> Ui.input(player, "Название", "Новая фракция", name ->
                Ui.input(player, "Описание", "Описание фракции", description -> {
                    String safe=id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
                    if(find(safe)!=null) { player.sendMessage(Text.literal("§cТакой ID уже существует.")); return; }
                    ConfigManager.get().factions.add(new MosvegasConfig.Faction(safe,name,description,"mosvegas.faction."+safe));
                    ConfigManager.save();
                    player.sendMessage(Text.literal("§aФракция создана. Ранги и предметы настраиваются в конфиге."));
                    openAdmin(player);
                })));
    }
}
