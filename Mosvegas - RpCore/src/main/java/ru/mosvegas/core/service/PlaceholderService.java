package ru.mosvegas.core.service;

import net.minecraft.server.network.ServerPlayerEntity;
import ru.mosvegas.core.data.*;
import ru.mosvegas.core.util.RpItems;

import java.util.Locale;

/** Stable placeholder API for scoreboards, chat bridges and addon mods. */
public final class PlaceholderService {
    private PlaceholderService() {}

    public static String resolve(ServerPlayerEntity player,String placeholder){
        String key=placeholder.toLowerCase(Locale.ROOT).replace("%","");
        if(key.startsWith("mosvegas_"))key=key.substring("mosvegas_".length());
        PlayerData d=DataStore.player(player.getUuid());PlayerData.Passport p=d.passport;
        return switch(key){
            case "first_name"->p==null?"":p.firstName;
            case "last_name"->p==null?"":p.lastName;
            case "age"->p==null?"":String.valueOf(p.age);
            case "gender"->p==null?"":p.gender;
            case "licences"->String.join(",",d.licences);
            case "driver_licence"->d.licences.contains("car")?"есть":"нет";
            case "weapon_licence"->d.licences.contains("weapon")?"есть":"нет";
            case "cash"->String.valueOf(RpItems.cashBalance(player));
            case "bank"->String.valueOf(d.bankBalance);
            case "faction"->d.faction==null?"":d.faction;
            case "faction_level"->String.valueOf(d.factionLevel);
            case "hierarchy_level"->String.valueOf(d.hierarchyLevel);
            case "server_level"->String.valueOf(d.serverLevel);
            default->"";
        };
    }
}
