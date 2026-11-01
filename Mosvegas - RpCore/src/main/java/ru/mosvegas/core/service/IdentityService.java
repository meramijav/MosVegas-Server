package ru.mosvegas.core.service;

import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import ru.mosvegas.core.config.ConfigManager;
import ru.mosvegas.core.data.*;
import ru.mosvegas.core.util.*;

import java.util.*;

public final class IdentityService {
    private IdentityService() {}

    public static void openMfc(ServerPlayerEntity player) {
        PlayerData d = DataStore.player(player.getUuid());
        Ui.menu(player, "МФЦ", m -> {
            if (d.passport == null) {
                m.label(13, Ui.item(Items.PAPER, "Паспорт не оформлен"));
                m.button(31, Ui.item(Items.WRITABLE_BOOK, "Получить паспорт"), () -> startPassport(player));
            } else {
                m.label(12, Ui.item(Items.NAME_TAG, "Имя: " + d.passport.firstName));
                m.label(13, Ui.item(Items.NAME_TAG, "Фамилия: " + d.passport.lastName));
                m.label(14, Ui.item(Items.PLAYER_HEAD, "Пол: " + d.passport.gender));
                m.label(22, Ui.item(Items.CLOCK, "Возраст: " + d.passport.age));
                m.button(31, Ui.item(Items.NAME_TAG, "Получить банковскую карту"), () -> EconomyService.openCardMenu(player));
                if(d.passport.age==0)m.button(40,Ui.item(Items.CLOCK,"Указать возраст после миграции"),()->setMigratedAge(player));
            }
        });
    }

    private static void startPassport(ServerPlayerEntity player) {
        if (!Permissions.has(player, "mosvegas.mfc.passport", 0)) {
            player.sendMessage(Text.literal("§cНет права mosvegas.mfc.passport")); return;
        }
        enterFirstName(player);
    }

    private static void enterFirstName(ServerPlayerEntity player) {
        Ui.input(player, "Введите имя", "Имя", first -> {
            if (!validName(first)) {
                player.sendMessage(Text.literal("§cИмя: от 2 до 24 букв."));
                enterFirstName(player);
                return;
            }
            enterLastName(player, first);
        });
    }

    private static void enterLastName(ServerPlayerEntity player, String first) {
        Ui.input(player, "Введите фамилию", "Фамилия", last -> {
            if (!validName(last)) {
                player.sendMessage(Text.literal("§cФамилия: от 2 до 24 букв."));
                enterLastName(player, first);
                return;
            }
            enterAge(player, first, last);
        });
    }

    private static void enterAge(ServerPlayerEntity player, String first, String last) {
        Ui.input(player, "Введите возраст", "18", ageText -> {
            try {
                int age=Integer.parseInt(ageText.trim());
                if(age<14||age>100) throw new NumberFormatException();
                chooseGender(player, first, last, age);
            } catch(NumberFormatException e) {
                player.sendMessage(Text.literal("§cВозраст должен быть целым числом от 14 до 100 лет."));
                enterAge(player, first, last);
            }
        });
    }

    private static void chooseGender(ServerPlayerEntity player, String first, String last, int age) {
        Ui.menu(player, "Выберите пол", m -> {
            m.button(20, Ui.item(Items.BLUE_WOOL, "Мужской"), () -> finishPassport(player, first, last, "Мужской", age));
            m.button(24, Ui.item(Items.PINK_WOOL, "Женский"), () -> finishPassport(player, first, last, "Женский", age));
        });
    }

    private static void finishPassport(ServerPlayerEntity player, String first, String last, String gender, int age) {
        PlayerData.Passport passport = new PlayerData.Passport();
        passport.firstName=first; passport.lastName=last; passport.gender=gender; passport.age=age; passport.issuedAt=System.currentTimeMillis();
        DataStore.player(player.getUuid()).passport = passport;
        DataStore.save();
        player.sendMessage(Text.literal("§aПаспорт успешно оформлен: " + last + " " + first));
        openMfc(player);
    }

    private static boolean validName(String value) { return value != null && value.matches("[A-Za-zА-Яа-яЁё-]{2,24}"); }

    private static void setMigratedAge(ServerPlayerEntity player){Ui.input(player,"Введите возраст","18",value->{try{int age=Integer.parseInt(value.trim());if(age<14||age>100)throw new NumberFormatException();DataStore.player(player.getUuid()).passport.age=age;DataStore.save();openMfc(player);}catch(NumberFormatException e){player.sendMessage(Text.literal("§cВозраст должен быть от 14 до 100 лет."));}});}

    public static void openLicence(ServerPlayerEntity player, String id) {
        PlayerData d = DataStore.player(player.getUuid());
        String name="car".equals(id)?"Водительская лицензия":"Лицензия на оружие";
        Ui.menu(player, name, m -> {
            licenceButton(m, player, 22, id, name, "car".equals(id)?Items.MINECART:Items.CROSSBOW);
            m.label(31, Ui.item(Items.BOOK, d.licences.contains(id)?"§aЛицензия получена":"Лицензия ещё не получена"));
        });
    }

    private static void licenceButton(Ui.MenuBuilder m, ServerPlayerEntity player, int slot, String id, String name, net.minecraft.item.Item icon) {
        PlayerData d = DataStore.player(player.getUuid());
        long price = ConfigManager.get().licencePrices.getOrDefault(id, 0L);
        String status = d.licences.contains(id) ? " §a[получено]" : " §e[" + EconomyService.money(price) + "]";
        m.button(slot, Ui.item(icon, name + status), () -> buyLicence(player, id, name));
    }

    private static void buyLicence(ServerPlayerEntity player, String id, String name) {
        PlayerData d = DataStore.player(player.getUuid());
        if (d.passport == null) { player.sendMessage(Text.literal("§cСначала оформите паспорт в /mfc")); return; }
        if (!Permissions.has(player, "mosvegas.licence." + id, 0)) {
            player.sendMessage(Text.literal("§cНет права mosvegas.licence." + id)); return;
        }
        if (d.licences.contains(id)) { player.sendMessage(Text.literal("§eЭта лицензия уже получена.")); return; }
        long price = ConfigManager.get().licencePrices.getOrDefault(id, 0L);
        if (!EconomyService.debit(player, price)) { player.sendMessage(Text.literal("§cНедостаточно денег на банковском счёте.")); return; }
        d.licences.add(id);
        DataStore.save();
        player.sendMessage(Text.literal("§aПолучено: " + name));
        openLicence(player,id);
    }
}
