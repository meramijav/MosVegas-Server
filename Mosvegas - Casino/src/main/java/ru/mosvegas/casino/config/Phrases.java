package ru.mosvegas.casino.config;

import java.util.LinkedHashMap;
import java.util.Map;

/** Dealer speech and player messages. Placeholders: {player}, {dealer}, {value}, {amount}, ... */
public final class Phrases {
    private Phrases() {}

    public static Map<String, String> defaults() {
        Map<String, String> m = new LinkedHashMap<>();
        // dealer speech
        m.put("betting_open", "Делайте ваши ставки!");
        m.put("betting_last", "Последние ставки...");
        m.put("bets_closed", "Ставки сделаны, ставок больше нет.");
        m.put("waiting_players", "Ждём ещё игроков за стол.");
        m.put("player_blackjack", "Блэкджек у {player}!");
        m.put("common_blackjack", "Блэкджек! Поздравляю!");
        m.put("player_bust", "{player}: перебор, {value}.");
        m.put("player_charlie", "{player}: шесть карт — автоматическая победа!");
        m.put("player_split", "{player} делит карты.");
        m.put("player_surrender", "{player} сдаётся, половина ставки возвращается.");
        m.put("your_turn", "{player}, ваше решение? У вас {value}.");
        m.put("decide_all", "Ваши решения, пожалуйста.");
        m.put("insurance_offer", "У дилера туз. Желаете страховку?");
        m.put("insurance_closed", "Страховка закрыта.");
        m.put("dealer_blackjack", "У дилера блэкджек.");
        m.put("dealer_no_blackjack", "У дилера нет блэкджека, продолжаем.");
        m.put("dealer_bust", "У дилера перебор — {value}!");
        m.put("dealer_total", "У дилера {value}.");
        m.put("side_win", "{player} выигрывает {bet} — {mult} к 1!");
        m.put("big_win", "Поздравляю с выигрышем!");
        m.put("shuffle", "Перемешиваю колоду, одну минуту.");
        m.put("greeting", "Добрый вечер! Меня зовут {dealer}, сегодня я ваш крупье.");
        m.put("welcome", "Добро пожаловать за стол, {player}!");
        m.put("welcome_behind", "{player}, ваша ставка за спиной бокса {box} принята.");
        m.put("goodbye_player", "Спасибо за игру, {player}!");
        m.put("farewell", "Спасибо за игру! Хорошего вечера.");
        m.put("handover", "Меня сменяет {next}. Удачи за столом!");
        m.put("break", "Ухожу на перерыв. Стол скоро продолжит работу.");
        m.put("idle_leave", "Пока никого нет — отлучусь ненадолго.");
        m.put("dealer_left", "Раунд отменён, ставки возвращены.");
        m.put("table_closed", "Стол закрывается, ставки возвращены.");
        m.put("round_aborted_all", "Раунд прерван, все ставки возвращены.");
        // private messages
        m.put("result_win", "§aВыигрыш: {amount} (чистыми +{net})");
        m.put("result_push", "§eНичья: возвращено {amount}");
        m.put("result_lose", "§cПроигрыш: -{amount}");
        // round summary, one line per part ({hand} is result_hand_one / result_hand_n / result_behind)
        m.put("result_hand_one", "Ваша рука: ");
        m.put("result_hand_n", "Рука {n}: ");
        m.put("result_behind", "Ставка за спиной: ");
        m.put("result_dealer", "{value} у дилера");
        m.put("result_dealer_bust", "перебора у дилера ({value})");
        m.put("result_hand_win", "§a{hand}{value} против {dealer} — выигрыш +{amount}");
        m.put("result_hand_blackjack", "§6{hand}блэкджек — выигрыш +{amount}");
        m.put("result_hand_push", "§e{hand}{value} против {dealer} — ничья, ставка {amount} возвращена");
        m.put("result_hand_surrender", "§7{hand}сдача — возвращено {amount}");
        m.put("result_hand_bust", "§c{hand}перебор ({value}) — проигрыш -{amount}");
        m.put("result_hand_lose", "§c{hand}{value} против {dealer} — проигрыш -{amount}");
        m.put("result_side", "§7Побочные ставки: {sign}{amount}");
        m.put("result_insurance", "§7Страховка: {sign}{amount}");
        m.put("result_total", "§fИтого за раунд: {sign}{amount}");
        m.put("side_win_private", "§6{bet}: выигрыш {amount}!");
        m.put("side_lose_private", "§7{bet}: не сыграла, крупье забирает {amount}. Основная ставка в игре.");
        m.put("bet_returned", "§eСтавка возвращена.");
        m.put("round_aborted", "§eРаунд прерван, возвращено {amount}.");
        m.put("behind_owner_left", "§eИгрок бокса ушёл — ставка за спиной отменена и возвращена.");
        m.put("err_not_seated", "§cСначала сядьте за стол (ПКМ по столу).");
        m.put("err_betting_closed", "§cСтавки сейчас не принимаются.");
        m.put("err_invalid", "§cНекорректная ставка.");
        m.put("err_over_max", "§cПревышен максимум стола.");
        m.put("err_over_side_max", "§cПревышен максимум побочной ставки.");
        m.put("err_side_unavailable", "§cЭта побочная ставка здесь недоступна.");
        m.put("err_no_money", "§cНедостаточно средств.");
        m.put("err_nothing", "§eНечего отменять.");
        m.put("err_under_min", "§cСтавка меньше минимальной для этого стола.");
        m.put("err_not_now", "§cСейчас это сделать нельзя.");
        m.put("err_not_playing", "§cВы не участвуете в этом раунде.");
        m.put("err_not_your_turn", "§eСейчас не ваш ход.");
        m.put("err_unavailable", "§cЭто действие недоступно.");
        m.put("err_wait", "§eПодождите, крупье раздаёт карты...");
        m.put("seat_taken", "§cЭтот бокс уже занят.");
        m.put("box_full", "§cЗа этим боксом нет свободных мест.");
        m.put("already_at_table", "§eВы уже за другим столом.");
        m.put("seated", "§aВы сели за бокс {box}. Меню стола: ПКМ по столу или клавиша [B].");
        m.put("behind_joined", "§aВы ставите за спиной игрока {owner} (бокс {box}).");
        m.put("left_table", "§eВы вышли из-за стола.");
        m.put("no_dealer", "Крупье скоро подойдёт к столу.");
        m.put("table_closed_info", "§cСтол закрыт.");
        m.put("stand_up_hint", "§7Чтобы встать из-за стола, нажмите Shift.");
        return m;
    }

    public static String format(String template, String... args) {
        if (template == null) return "";
        String out = template;
        for (int i = 0; i + 1 < args.length; i += 2) out = out.replace("{" + args[i] + "}", args[i + 1] == null ? "" : args[i + 1]);
        return out;
    }
}
