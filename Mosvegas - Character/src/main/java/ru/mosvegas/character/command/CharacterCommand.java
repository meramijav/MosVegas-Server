package ru.mosvegas.character.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import ru.mosvegas.character.MosVegasCharacter;
import ru.mosvegas.character.server.CharacterGate;

import java.util.Collection;

public final class CharacterCommand {
    private CharacterCommand() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("character")
                .requires(src -> src.hasPermissionLevel(3))
                .then(CommandManager.literal("reset")
                        .then(CommandManager.argument("targets", GameProfileArgumentType.gameProfile())
                                .executes(ctx -> reset(ctx.getSource(), GameProfileArgumentType.getProfileArgument(ctx, "targets")))))
                .then(CommandManager.literal("info")
                        .then(CommandManager.argument("target", GameProfileArgumentType.gameProfile())
                                .executes(ctx -> info(ctx.getSource(), GameProfileArgumentType.getProfileArgument(ctx, "target"))))));
    }

    private static int reset(ServerCommandSource src, Collection<GameProfile> targets) {
        int done = 0;
        for (GameProfile profile : targets) {
            if (CharacterGate.reset(src.getServer(), profile.getId())) done++;
        }
        int count = done;
        src.sendFeedback(() -> Text.literal("Персонаж сброшен: " + count + ". Редактор откроется при входе."), true);
        return count;
    }

    private static int info(ServerCommandSource src, Collection<GameProfile> targets) throws CommandSyntaxException {
        for (GameProfile profile : targets) {
            var a = MosVegasCharacter.STORE.get(profile.getId());
            String line = a == null
                    ? profile.getName() + ": персонаж не создан"
                    : profile.getName() + ": пол " + (a.gender == 0 ? "М" : "Ж") + ", рост " + (a.height + 1)
                    + ", телосложение " + (a.build + 1) + ", кожа " + (a.skin + 1) + ", глаза " + (a.eyes + 1)
                    + ", волосы " + (a.hair + 1) + "/" + (a.hairColor + 1) + ", борода " + a.beard + ", одежда " + a.outfit;
            src.sendFeedback(() -> Text.literal(line), false);
        }
        return targets.size();
    }
}
