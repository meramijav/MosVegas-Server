package ru.mosvegas.character.client.dev;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.text.Text;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import ru.mosvegas.character.client.ClientCharacters;
import ru.mosvegas.character.data.Appearance;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class GalleryScreen extends Screen {
    private final int page;
    private final List<Cell> cells = new ArrayList<>();

    public GalleryScreen(int page) {
        super(Text.literal("Gallery"));
        this.page = page;
    }

    private static Appearance look(int gender, int build, int height, int skin, int eyes, int hairColor, String outfit) {
        Appearance a = new Appearance();
        a.gender = gender;
        a.build = build;
        a.height = height;
        a.skin = skin;
        a.eyes = eyes;
        a.hairColor = hairColor;
        a.outfit = outfit;
        return a;
    }

    private void add(Appearance a, float yaw, String label) {
        UUID id = UUID.nameUUIDFromBytes(("gallery" + cells.size() + page).getBytes());
        OtherClientPlayerEntity e = new OtherClientPlayerEntity(client.world, new GameProfile(id, "g" + cells.size()));
        ClientCharacters.set(id, a);
        e.calculateDimensions();
        cells.add(new Cell(e, yaw, label));
    }

    @Override
    protected void init() {
        cells.clear();
        if (client.world == null) return;
        if (page == 0) {
            String[] builds = {"худ", "обыч", "спорт", "крупн"};
            for (int g = 0; g < 2; g++)
                for (int b = 0; b < 4; b++) add(look(g, b, 2, 2, 0, 2, "casual"), 0, (g == 0 ? "М " : "Ж ") + builds[b]);
            add(look(0, 1, 2, 2, 0, 2, "casual"), 180, "М спина");
            add(look(0, 1, 2, 2, 0, 2, "casual"), 90, "М бок");
            add(look(1, 1, 2, 2, 0, 2, "casual"), 180, "Ж спина");
            add(look(1, 1, 2, 2, 0, 2, "casual"), 90, "Ж бок");
            add(look(0, 1, 0, 2, 0, 2, "classic"), 0, "М рост 1");
            add(look(0, 1, 4, 2, 0, 2, "classic"), 0, "М рост 5");
            add(look(1, 1, 0, 2, 0, 2, "classic"), 0, "Ж рост 1");
            add(look(1, 1, 4, 2, 0, 2, "classic"), 0, "Ж рост 5");
        } else if (page == 1) {
            String[] outfits = {"casual", "street", "classic"};
            for (int i = 0; i < 8; i++) add(look(i % 2, 1, 2, i, i, (i * 5) % 12, outfits[i % 3]), 0, "кожа " + (i + 1));
            for (int i = 0; i < 8; i++) add(look(i % 2, 1, 2, i, i, (i * 5 + 3) % 12, outfits[(i + 1) % 3]), 30, "");
        } else if (page == 2) {
            add(look(1, 0, 2, 2, 4, 5, "casual"), 90, "Ж худ бок");
            add(look(1, 1, 2, 2, 4, 5, "street"), 90, "Ж обыч бок");
            add(look(1, 3, 2, 2, 4, 5, "classic"), 90, "Ж крупн бок");
            add(look(1, 1, 2, 2, 4, 5, "casual"), 35, "Ж 3/4");
            add(look(0, 1, 2, 3, 1, 1, "classic"), 35, "М 3/4");
            add(look(0, 3, 2, 3, 1, 1, "street"), 90, "М крупн бок");
        } else if (page == 3) {
            for (int i = 0; i < 8; i++) add(look(i < 4 ? 0 : 1, 1, 2, i, i, (i * 3) % 12, ""), 0, "без одежды " + (i + 1));
        } else if (page == 6) {
            int[][] styles = {{0, 8}, {1, 0}, {1, 1}, {1, 4}, {1, 6}, {1, 7}, {1, 8}};
            for (int color : new int[]{1, 5})
                for (int[] st : styles) {
                    Appearance a = look(st[0], 1, 2, 3, 1, color, "classic");
                    a.hair = st[1];
                    add(a, 0, (st[0] == 0 ? "М" : "Ж") + (st[1] + 1));
                }
        } else {
            int style = Integer.getInteger("mosvegas.character.hair", 0);
            int gender = Integer.getInteger("mosvegas.character.hairGender", 0);
            float[] yaws = {0, 35, 90, 180};
            int[] colors = {1, 5};
            for (int c = 0; c < 2; c++)
                for (float yw : yaws) {
                    Appearance a = look(gender, 1, 2, 3, 1, colors[c], "classic");
                    a.hair = style;
                    if (gender == Appearance.MALE) a.beard = Integer.getInteger("mosvegas.character.beard", 0);
                    add(a, yw, "");
                }
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xFF2A2A2E);
        int cols = page == 6 ? 7 :page == 2 ? 6 : page >= 4 ? 4 : 8;
        client.options.hudHidden = true;
        client.getToastManager().clear();
        int rows = (cells.size() + cols - 1) / cols;
        int cw = width / cols;
        int ch = height / Math.max(1, rows);
        int size = (int) (page == 6 ? cw * 0.95f : ch * (page == 2 ? 0.30f : page == 5 ? 0.42f : page >= 4 ? 1.05f : 0.36f));
        for (int i = 0; i < cells.size(); i++) {
            Cell c = cells.get(i);
            int cx = (i % cols) * cw + cw / 2;
            int base = (i / cols) * ch + ch - 18;
            Quaternionf rot = new Quaternionf().rotateZ((float) Math.PI);
            if (page == 6) rot.rotateX((float) Math.toRadians(-70));
            rot.rotateY((float) Math.toRadians(c.yaw));
            c.entity.bodyYaw = 180f;
            c.entity.prevBodyYaw = 180f;
            c.entity.setYaw(180f);
            c.entity.headYaw = 180f;
            c.entity.prevHeadYaw = 180f;
            ctx.fill(cx - cw / 2 + 2, base, cx + cw / 2 - 2, base + 1, 0xFF55555C);
            if (page == 6) {
                ctx.enableScissor((i % cols) * cw + 2, (i / cols) * ch + 2, (i % cols) * cw + cw - 2, (i / cols) * ch + ch - 2);
                InventoryScreen.drawEntity(ctx, cx, (i / cols) * ch + ch * 0.5f + size * 1.2f, size, new Vector3f(), rot, null, c.entity);
                ctx.disableScissor();
            } else if (page == 4) {
                ctx.enableScissor((i % cols) * cw + 2, (i / cols) * ch + 2, (i % cols) * cw + cw - 2, (i / cols) * ch + ch - 2);
                InventoryScreen.drawEntity(ctx, cx, (i / cols) * ch + ch * 0.5f + size * 1.78f, size, new Vector3f(), rot, null, c.entity);
                ctx.disableScissor();
            } else {
                InventoryScreen.drawEntity(ctx, cx, base, size, new Vector3f(), rot, null, c.entity);
            }
            ctx.drawCenteredTextWithShadow(textRenderer, c.label, cx, base + 4, 0xFFECAA38);
        }
    }

    @Override
    public void removed() {
        client.options.hudHidden = false;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private record Cell(OtherClientPlayerEntity entity, float yaw, String label) {}
}
