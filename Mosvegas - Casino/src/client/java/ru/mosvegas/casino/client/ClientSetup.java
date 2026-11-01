package ru.mosvegas.casino.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.hit.BlockHitResult;
import org.lwjgl.glfw.GLFW;
import ru.mosvegas.casino.block.BlackjackTableBlock;
import ru.mosvegas.casino.block.TablePartBlock;
import ru.mosvegas.casino.client.screen.BlackjackScreen;
import ru.mosvegas.casino.client.state.ClientTable;
import ru.mosvegas.casino.client.state.ClientTables;

/** Key binding, HUD and automatic opening of the table menu when the player has to act. */
public final class ClientSetup {
    public static final KeyBinding MENU_KEY = new KeyBinding("key.mosvegas_casino.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_B, "key.categories.mosvegas_casino");

    private ClientSetup() {}

    public static void register() {
        KeyBindingHelper.registerKeyBinding(MENU_KEY);
        HudRenderCallback.EVENT.register(TableHud::render);
        ClientTickEvents.END_CLIENT_TICK.register(ClientSetup::tick);
        // the table stays clickable, but its big collision box is not outlined over the felt and the cards
        WorldRenderEvents.BEFORE_BLOCK_OUTLINE.register((context, hit) -> {
            if (!(hit instanceof BlockHitResult block) || context.world() == null) return true;
            Block b = context.world().getBlockState(block.getBlockPos()).getBlock();
            return !(b instanceof BlackjackTableBlock || b instanceof TablePartBlock);
        });
    }

    private static void tick(MinecraftClient mc) {
        if (mc.player == null) return;
        ClientTables.tickAll(mc);
        while (MENU_KEY.wasPressed()) {
            ClientTable table = ClientTables.mine();
            if (table == null) table = ClientTables.nearest(10);
            if (table != null && table.snapshot() != null) mc.setScreen(new BlackjackScreen(table.snapshot().pos()));
        }
        ClientTable mine = ClientTables.mine();
        if (mine == null || mine.snapshot() == null) return;
        boolean decision = mine.consumeDecisionNotice();
        boolean insurance = mine.consumeInsuranceNotice();
        boolean betting = mine.consumeBettingNotice();
        if ((decision || insurance || betting) && mc.currentScreen == null) mc.setScreen(new BlackjackScreen(mine.snapshot().pos()));
    }
}
