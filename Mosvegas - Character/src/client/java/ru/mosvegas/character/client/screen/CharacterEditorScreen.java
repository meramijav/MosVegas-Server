package ru.mosvegas.character.client.screen;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.MessageScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import ru.mosvegas.character.client.ClientCharacters;
import ru.mosvegas.character.client.MosVegasCharacterClient;
import ru.mosvegas.character.client.skin.SkinComposer;
import ru.mosvegas.character.client.ui.Ui;
import ru.mosvegas.character.client.ui.Ui.F;
import ru.mosvegas.character.data.Appearance;
import ru.mosvegas.character.data.CharacterOptions;
import ru.mosvegas.character.data.CharacterPalette;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CharacterEditorScreen extends Screen {
    private static final String[] STEPS = {"Пол", "Рост", "Телосложение", "Кожа", "Глаза", "Волосы", "Борода", "Одежда"};
    private static final String[] GENDERS = {"Мужской", "Женский"};
    private static final int STEP_BEARD = 6;
    private static final float PANEL_W = 240, PANEL_H = 355, TOP = 62;

    private final List<Hit> hits = new ArrayList<>();
    private final List<Mini> minis = new ArrayList<>();
    private final Map<Integer, OtherClientPlayerEntity> fakes = new HashMap<>();
    private int step;
    private int furthest;
    private float yaw = 20f;
    private float zoom;
    private float zoomTarget;
    private boolean dragging;
    private boolean confirm;
    private boolean sent;
    private int outfitPage;
    private float mx, my;

    public CharacterEditorScreen() {
        super(Text.literal("Создание персонажа"));
    }

    private Appearance a() {
        return ClientCharacters.draft();
    }

    public void setStep(int s) {
        step = Math.max(0, Math.min(STEPS.length - 1, s));
        furthest = Math.max(furthest, step);
    }

    public void openConfirm() {
        confirm = true;
    }

    public void closeConfirm() {
        confirm = false;
    }

    public void setZoom(float z) {
        zoom = zoomTarget = z;
    }

    private boolean female() {
        return a().gender == Appearance.FEMALE;
    }

    private boolean disabled(int i) {
        return i == STEP_BEARD && female();
    }

    private void click() {
        client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1f));
    }

    private void hit(float x, float y, float w, float h, Runnable action) {
        hits.add(new Hit(x, y, w, h, action));
    }

    private boolean hover(float x, float y, float w, float h) {
        return !confirm && Ui.inside(mx, my, x, y, w, h);
    }

    private static String outfitName(String id) {
        String key = "mosvegas_character.outfit." + id;
        return I18n.hasTranslation(key) ? I18n.translate(key) : id;
    }

    private String value(int i) {
        Appearance a = a();
        return switch (i) {
            case 0 -> GENDERS[a.gender];
            case 1 -> CharacterPalette.heightCm(a) + " см";
            case 2 -> CharacterPalette.BUILD_NAMES[a.build];
            case 3 -> CharacterPalette.SKIN_NAMES[a.skin];
            case 4 -> CharacterPalette.EYE_NAMES[a.eyes];
            case 5 -> CharacterPalette.HAIR_NAMES[a.hairColor] + " · №" + (a.hair + 1);
            case 6 -> female() ? "Только для мужского" : CharacterPalette.BEARD_NAMES[a.beard];
            default -> ClientCharacters.outfits().isEmpty() ? "—" : outfitName(a.outfit);
        };
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        hits.clear();
        minis.clear();
        client.getToastManager().clear();
        zoom += (zoomTarget - zoom) * Math.min(1f, delta * 0.35f);
        float k = Ui.scaleFor(width, height);
        mx = Ui.toDesign(mouseX, k);
        my = Ui.toDesign(mouseY, k);

        Ui ui = Ui.begin(ctx, width, height);
        float W = ui.w, H = ui.h;
        ui.rect(0, 0, W, H, Ui.BG);
        ui.vgradient(0, H - 140, W, 140, Ui.BG, Ui.BG2);
        header(ui);
        steps(ui);
        float cx = (216 + W - 256) / 2f;
        float floor = H - 92;
        if (zoom < 0.6f) pedestal(ui, cx, floor, 1f - zoom / 0.6f);
        centerControls(ui, cx, H);
        panel(ui, W - 16 - PANEL_W, TOP);
        createButton(ui, W, H);
        ui.ghost(16, H - 44, 200, 28, "Выйти с сервера", F.M7_5, hover(16, H - 44, 200, 28));
        hit(16, H - 44, 200, 28, this::leave);
        ui.end();

        drawFigure(ctx, k, cx, floor, H);
        for (Mini m : minis) drawMini(ctx, k, m);

        if (confirm) {
            ctx.getMatrices().push();
            ctx.getMatrices().translate(0, 0, 400);
            Ui top = Ui.begin(ctx, width, height);
            confirmDialog(top);
            top.end();
            ctx.getMatrices().pop();
        }
    }

    private void header(Ui ui) {
        ui.text("СОЗДАНИЕ ПЕРСОНАЖА", F.B13, 16, 24, Ui.WHITE, 0);
        ui.image(Ui.LOGO, ui.w - 16 - 36, 6, 36, 36, Ui.WHITE);
        ui.divider(16, ui.w - 16, 49);
    }

    private void steps(Ui ui) {
        float y = TOP;
        for (int i = 0; i < STEPS.length; i++) {
            float h = 40;
            boolean active = i == step;
            boolean off = disabled(i);
            boolean hov = !active && !off && hover(16, y, 200, h - 3);
            int num, tc, vc;
            if (active) {
                ui.rr(16, y, 200, h, 7, Ui.RED3);
                ui.rr(16, y, 200, h - 3, 7, Ui.RED);
                ui.rr(17, y + 1, 198, 4, 3, Ui.RED_TOP);
                ui.rr(17, y + 3, 198, h - 7, 6, Ui.RED);
                num = Ui.WHITE;
                tc = Ui.WHITE;
                vc = Ui.GOLD3;
            } else {
                ui.rr(16, y, 200, h - 3, 7, hov ? Ui.PANEL2 : Ui.PANEL);
                boolean done = i < furthest;
                num = done && !off ? Ui.GOLD : Ui.MUTED;
                tc = off ? Ui.MUTED : Ui.WHITE;
                vc = off ? Ui.MUTED : Ui.TEXT;
                if (done && !off) ui.check(200, y + 18.5f, 10, Ui.GOLD);
            }
            ui.text(String.format("%02d", i + 1), F.B8, 30, y + 18.5f, num, 0);
            ui.text(STEPS[i], F.B8_5, 54, y + 12, tc, 0);
            ui.text(ui.fit(value(i), F.M6_8, 136), F.M6_8, 54, y + 25, vc, 0);
            if (!off && !active) {
                int target = i;
                hit(16, y, 200, h - 3, () -> {
                    setStep(target);
                    click();
                });
            }
            y += h + 5;
        }
    }

    private void pedestal(Ui ui, float cx, float floor, float alpha) {
        int a = Math.round(alpha * 255) << 24;
        float rx = 104, ry = 16, band = 7;
        ui.ellipse(cx, floor + band, rx, ry, a | (Ui.FLOOR & 0xFFFFFF));
        ui.rect(cx - rx, floor, rx * 2, band, a | (Ui.FLOOR & 0xFFFFFF));
        ui.ellipse(cx, floor + 1.3f, rx, ry, a | (Ui.RED2 & 0xFFFFFF));
        ui.ellipse(cx, floor, rx, ry, a | (Ui.PANEL & 0xFFFFFF));
        ui.ellipse(cx, floor, 44, 6, a | 0x1A1213);
    }

    private void centerControls(Ui ui, float cx, float H) {
        float sx = cx - 74;
        ui.rr(sx, TOP, 148, 20, 6, Ui.PANEL);
        boolean full = zoomTarget < 0.5f;
        ui.rr(full ? sx + 2 : sx + 74, TOP + 2, 72, 16, 5, Ui.PANEL3);
        boolean h1 = hover(sx, TOP, 74, 20), h2 = hover(sx + 74, TOP, 74, 20);
        ui.text("Весь рост", F.B7, sx + 38, TOP + 10, full || h1 ? Ui.WHITE : Ui.MUTED, 1);
        ui.text("Лицо", F.B7, sx + 111, TOP + 10, !full || h2 ? Ui.WHITE : Ui.MUTED, 1);
        hit(sx, TOP, 74, 20, () -> {
            zoomTarget = 0;
            click();
        });
        hit(sx + 74, TOP, 74, 20, () -> {
            zoomTarget = 1;
            click();
        });
        float y = H - 40;
        String[][] parts = {{"ЛКМ", "Поворот"}, {"КОЛЕСО", "Приближение"}};
        float total = 0;
        for (String[] p : parts) total += ui.width(p[0], F.B6_5, 0.5f) + 12 + 6 + ui.width(p[1], F.M7);
        total += 18;
        float x = cx - total / 2;
        for (String[] p : parts) {
            float kw = ui.keycap(x, y, p[0]);
            ui.text(p[1], F.M7, x + kw + 6, y + 6.5f, Ui.TEXT, 0);
            x += kw + 6 + ui.width(p[1], F.M7) + 18;
        }
    }

    private void createButton(Ui ui, float W, float H) {
        float x = W - 256, y = H - 52;
        ui.text("Изменить внешность потом сможет только администрация", F.M5_8, x + 120, y - 14, Ui.MUTED, 1);
        boolean h = hover(x, y, 240, 36);
        ui.primary(x, y, 240, 36, sent ? "СОХРАНЯЕМ…" : "СОЗДАТЬ ПЕРСОНАЖА", F.B9_5, h && !sent, true);
        hit(x, y, 240, 36, () -> {
            if (sent) return;
            confirm = true;
            click();
        });
    }

    private void panel(Ui ui, float x, float y) {
        ui.rr(x, y, PANEL_W, PANEL_H, 9, Ui.PANEL);
        switch (step) {
            case 0 -> panelGender(ui, x, y);
            case 1 -> panelHeight(ui, x, y);
            case 2 -> panelBuild(ui, x, y);
            case 3 -> panelSwatches(ui, x, y, "КОЖА", CharacterPalette.SKIN, CharacterPalette.SKIN_NAMES, a().skin, i -> a().skin = i,
                    "Тон ложится на лицо,", "руки и ноги.");
            case 4 -> panelSwatches(ui, x, y, "ГЛАЗА", CharacterPalette.EYES, CharacterPalette.EYE_NAMES, a().eyes, i -> a().eyes = i,
                    "Цвет радужки глаз.", "");
            case 5 -> panelHair(ui, x, y);
            case 6 -> panelBeard(ui, x, y);
            default -> panelOutfit(ui, x, y);
        }
        float by = y + PANEL_H - 44;
        boolean first = step == 0;
        boolean last = step == STEPS.length - 1;
        if (!first) {
            ui.ghost(x + 16, by, 92, 28, "Назад", F.M8, hover(x + 16, by, 92, 28));
            hit(x + 16, by, 92, 28, () -> {
                go(-1);
                click();
            });
        }
        float nx = first ? x + 16 : x + 116, nw = first ? 208 : 108;
        ui.primary(nx, by, nw, 28, last ? "Готово" : "Далее", F.B8_5, hover(nx, by, nw, 28), true);
        hit(nx, by, nw, 28, () -> {
            if (last) confirm = true;
            else go(1);
            click();
        });
    }

    private void go(int dir) {
        int s = step + dir;
        while (s >= 0 && s < STEPS.length && disabled(s)) s += dir;
        if (s >= 0 && s < STEPS.length) setStep(s);
    }

    private void caption(Ui ui, float x, float y, String cap, String right, String big) {
        ui.text(cap, F.B6_8, x + 16, y + 18, Ui.GOLD, 0, 1.1f);
        if (right != null) ui.text(right, F.M6_8, x + PANEL_W - 16, y + 18, Ui.TEXT, 2);
        if (big != null) {
            ui.text(ui.fit(big, F.B12, PANEL_W - 32), F.B12, x + 16, y + 36, Ui.WHITE, 0);
            ui.rect(x + 16, y + 52, PANEL_W - 32, 1, Ui.LINE);
        }
    }

    private void card(Ui ui, float x, float y, float w, float h, boolean selected, Runnable action) {
        boolean hov = hover(x, y, w, h);
        if (selected) ui.ring(x - 2.5f, y - 2.5f, w + 5, h + 5, 8, Ui.GOLD, true);
        ui.rr(x, y, w, h, 6, hov && !selected ? Ui.PANEL3 : Ui.PANEL2);
        hit(x, y, w, h, () -> {
            action.run();
            click();
        });
    }

    private void face(Ui ui, Appearance look, float x, float y, float size) {
        Identifier tex = SkinComposer.texture(look);
        if (tex == null) return;
        ui.region(tex, x, y, size, size, 8, 8, 8, 8, 64, 64);
        ui.region(tex, x, y, size, size, 40, 8, 8, 8, 64, 64);
    }

    private void panelGender(Ui ui, float x, float y) {
        caption(ui, x, y, "ПОЛ", null, GENDERS[a().gender]);
        float cw = 101, ch = 128;
        for (int g = 0; g < 2; g++) {
            float cx = x + 16 + g * (cw + 6);
            int gender = g;
            card(ui, cx, y + 66, cw, ch, a().gender == g, () -> setGender(gender));
            Appearance look = a().copy();
            look.gender = g;
            if (g == Appearance.FEMALE) look.beard = 0;
            minis.add(new Mini(100 + g, look, cx + cw / 2, y + 66 + ch - 26, 84, 25));
            ui.text(GENDERS[g], F.B7_5, cx + cw / 2, y + 66 + ch - 13, a().gender == g ? Ui.WHITE : Ui.TEXT, 1);
        }
    }

    private void setGender(int g) {
        a().gender = g;
        if (g == Appearance.FEMALE) a().beard = 0;
    }

    private void panelHeight(Ui ui, float x, float y) {
        caption(ui, x, y, "РОСТ", (a().height + 1) + " из " + CharacterOptions.HEIGHTS, CharacterPalette.heightCm(a()) + " см");
        float wx = x + 16, wy = y + 64, ww = PANEL_W - 32, wh = 226;
        ui.rr(wx, wy, ww, wh, 8, Ui.WELL);
        Appearance probe = a().copy();
        probe.height = 0;
        int lo = CharacterPalette.heightCm(probe) - 8;
        probe.height = CharacterOptions.HEIGHTS - 1;
        int hi = CharacterPalette.heightCm(probe) + 6;
        float top = wy + 14, bottom = wy + wh - 14;
        float perCm = (bottom - top) / (hi - lo);
        float barX = wx + 16;
        int selCm = CharacterPalette.heightCm(a());
        float selY = bottom - (selCm - lo) * perCm;
        ui.rr(barX - 1.5f, top, 3, bottom - top, 1.5f, Ui.LINE);
        ui.rr(barX - 1.5f, selY, 3, bottom - selY, 1.5f, Ui.GOLD);
        for (int cm = lo; cm <= hi; cm++) {
            float ty = bottom - (cm - lo) * perCm;
            boolean ten = cm % 10 == 0, five = cm % 5 == 0;
            float len = ten ? 10 : five ? 7 : 4;
            ui.rect(barX + 3, ty - 0.5f, len, 1, ten ? Ui.SOFT : five ? Ui.MUTED : Ui.LINE);
            if (ten) ui.text(String.valueOf(cm), F.M5_8, barX + 16, ty, Ui.MUTED, 0);
        }
        float rowX = barX + 36, rowW = wx + ww - 8 - rowX;
        for (int i = 0; i < CharacterOptions.HEIGHTS; i++) {
            probe.height = i;
            int cm = CharacterPalette.heightCm(probe);
            float ty = bottom - (cm - lo) * perCm;
            boolean sel = a().height == i;
            boolean hov = !sel && hover(rowX, ty - 11, rowW, 22);
            int idx = i;
            hit(rowX, ty - 11, rowW, 22, () -> {
                a().height = idx;
                click();
            });
            if (hov) ui.rr(rowX, ty - 11, rowW, 22, 6, Ui.PANEL2);
            if (sel) {
                ui.rect(barX + 3, ty - 0.75f, rowW + rowX - barX - 3 - 64, 1.5f, Ui.GOLD);
                ui.ellipse(barX, ty, 4, 4, Ui.GOLD);
                ui.ellipse(barX, ty, 1.6f, 1.6f, Ui.RED3);
                float pw = 60, px = rowX + rowW - pw - 2;
                ui.rr(px, ty - 9.5f, pw, 19, 9.5f, Ui.GOLD);
                ui.text(cm + " см", F.B7_5, px + pw / 2, ty, Ui.RED3, 1);
            } else {
                for (float dx = rowX + 6; dx < rowX + rowW - 70; dx += 5) ui.rect(dx, ty - 0.5f, 2, 1, hov ? 0xFF6A5052 : 0xFF3A2B2C);
                ui.text(cm + " см", F.B7, rowX + rowW - 32, ty, hov ? Ui.WHITE : Ui.TEXT, 1);
            }
        }
    }

    private void panelBuild(Ui ui, float x, float y) {
        caption(ui, x, y, "ТЕЛОСЛОЖЕНИЕ", (a().build + 1) + " из " + CharacterOptions.BUILDS, CharacterPalette.BUILD_NAMES[a().build]);
        float cw = 101, ch = 112;
        for (int i = 0; i < CharacterOptions.BUILDS; i++) {
            float cx = x + 16 + (i % 2) * (cw + 6);
            float cy = y + 64 + (i / 2) * (ch + 6);
            int idx = i;
            card(ui, cx, cy, cw, ch, a().build == i, () -> a().build = idx);
            Appearance look = a().copy();
            look.build = i;
            look.height = 2;
            minis.add(new Mini(300 + i, look, cx + cw / 2, cy + ch - 22, 76, 20));
            ui.text(CharacterPalette.BUILD_NAMES[i], F.B7, cx + cw / 2, cy + ch - 11, a().build == i ? Ui.WHITE : Ui.TEXT, 1);
        }
    }

    private void panelSwatches(Ui ui, float x, float y, String cap, int[] colors, String[] names, int selected,
                               java.util.function.IntConsumer set, String note1, String note2) {
        caption(ui, x, y, cap, (selected + 1) + " из " + colors.length, names[selected]);
        float sw = 44, gap = 10;
        for (int i = 0; i < colors.length; i++) {
            float sx = x + 16 + (i % 4) * (sw + gap);
            float sy = y + 66 + (i / 4) * (sw + gap);
            swatch(ui, sx, sy, sw, colors[i], i == selected, 7, 4);
            int idx = i;
            hit(sx, sy, sw, sw, () -> {
                set.accept(idx);
                click();
            });
        }
        ui.rect(x + 16, y + 180, PANEL_W - 32, 1, Ui.LINE);
        ui.text("ЛИЦО", F.B6_8, x + 16, y + 196, Ui.GOLD, 0, 1.1f);
        ui.rr(x + 16, y + 208, 64, 64, 8, Ui.PANEL2);
        face(ui, a(), x + 24, y + 216, 48);
    }

    private void swatch(Ui ui, float sx, float sy, float sw, int rgb, boolean selected, float r, float ledge) {
        boolean hov = hover(sx, sy, sw, sw);
        if (selected) ui.ring(sx - 3, sy - 3, sw + 6, sw + 6, r + 2, Ui.GOLD, true);
        else if (hov) ui.ring(sx - 3, sy - 3, sw + 6, sw + 6, r + 2, 0xFF6A5052, false);
        int base = 0xFF000000 | rgb;
        int dark = 0xFF000000 | (int) ((rgb >> 16 & 255) * 0.72f) << 16 | (int) ((rgb >> 8 & 255) * 0.72f) << 8 | (int) ((rgb & 255) * 0.72f);
        ui.rr(sx, sy, sw, sw, r, dark);
        ui.rr(sx, sy, sw, sw - ledge, r, base);
        if (selected && sw >= 40) {
            ui.ellipse(sx + sw - 8.5f, sy + 8.5f, 4.5f, 4.5f, Ui.GOLD);
            ui.check(sx + sw - 8.5f, sy + 8.6f, 6.5f, Ui.RED3);
        }
    }

    private void panelHair(Ui ui, float x, float y) {
        caption(ui, x, y, "ПРИЧЁСКА", "№" + (a().hair + 1) + " из " + CharacterOptions.HAIRS_PER_GENDER, null);
        float cw = 37, ch = 44, gap = 6;
        for (int i = 0; i < CharacterOptions.HAIRS_PER_GENDER; i++) {
            float cx = x + 15 + (i % 5) * (cw + gap);
            float cy = y + 32 + (i / 5) * (ch + gap);
            int idx = i;
            card(ui, cx, cy, cw, ch, a().hair == i, () -> a().hair = idx);
            Appearance look = a().copy();
            look.hair = i;
            look.height = 2;
            float block = 50f;
            float headMid = cy + 18;
            float feet = headMid + 1.75f * block * CharacterPalette.heightScale(look);
            minis.add(new Mini(200 + i, look, cx + cw / 2, feet, block * 1.875f, 30, new float[]{cx + 1, cy + 1, cw - 2, ch - 14}));
            ui.text(String.format("%02d", i + 1), F.B6_5, cx + cw / 2, cy + ch - 8, a().hair == i ? Ui.GOLD : Ui.MUTED, 1);
        }
        ui.rect(x + 16, y + 136, PANEL_W - 32, 1, Ui.LINE);
        ui.text("ЦВЕТ ВОЛОС", F.B6_8, x + 16, y + 150, Ui.GOLD, 0, 1.1f);
        ui.text(CharacterPalette.HAIR_NAMES[a().hairColor], F.M6_8, x + PANEL_W - 16, y + 150, Ui.TEXT, 2);
        float sw = 30, sg = 5.6f;
        for (int i = 0; i < CharacterPalette.HAIR.length; i++) {
            float sx = x + 16 + (i % 6) * (sw + sg);
            float sy = y + 162 + (i / 6) * (sw + sg);
            swatch(ui, sx, sy, sw, CharacterPalette.HAIR[i], i == a().hairColor, 6, 3);
            int idx = i;
            hit(sx, sy, sw, sw, () -> {
                a().hairColor = idx;
                click();
            });
        }
    }

    private void panelBeard(Ui ui, float x, float y) {
        if (female()) {
            caption(ui, x, y, "БОРОДА", null, "Недоступно");
            ui.text("Борода есть только у мужского", F.M7, x + 16, y + 70, Ui.TEXT, 0);
            ui.text("персонажа.", F.M7, x + 16, y + 82, Ui.TEXT, 0);
            return;
        }
        String name = CharacterPalette.BEARD_NAMES[a().beard];
        caption(ui, x, y, "БОРОДА", a().beard == 0 ? null : a().beard + " из " + (CharacterOptions.BEARDS - 1), name);
        float cw = 48, ch = 56, gap = 5.3f;
        for (int i = 0; i < CharacterOptions.BEARDS; i++) {
            float cx = x + 16 + (i % 4) * (cw + gap);
            float cy = y + 64 + (i / 4) * (ch + gap);
            int idx = i;
            card(ui, cx, cy, cw, ch, a().beard == i, () -> a().beard = idx);
            Appearance look = a().copy();
            look.beard = i;
            look.height = 2;
            float block = 52f;
            float feet = cy + 11 + 1.75f * block * CharacterPalette.heightScale(look);
            minis.add(new Mini(500 + i, look, cx + cw / 2, feet, block * 1.875f, 30, new float[]{cx + 1, cy + 1, cw - 2, ch - 15}));
            ui.text(i == 0 ? "Нет" : String.format("%02d", i), F.B6_5, cx + cw / 2, cy + ch - 9, a().beard == i ? Ui.GOLD : Ui.MUTED, 1);
        }
    }

    private void panelOutfit(Ui ui, float x, float y) {
        List<String> list = ClientCharacters.outfits();
        if (list.isEmpty()) {
            caption(ui, x, y, "ОДЕЖДА", null, "Нет комплектов");
            return;
        }
        int pages = (list.size() + 3) / 4;
        outfitPage = Math.min(outfitPage, pages - 1);
        caption(ui, x, y, "ОДЕЖДА", (list.indexOf(a().outfit) + 1) + " из " + list.size(), outfitName(a().outfit));
        float cw = 101, ch = 112;
        for (int n = 0; n < 4; n++) {
            int i = outfitPage * 4 + n;
            if (i >= list.size()) break;
            float cx = x + 16 + (n % 2) * (cw + 6);
            float cy = y + 64 + (n / 2) * (ch + 6);
            String id = list.get(i);
            boolean sel = id.equals(a().outfit);
            card(ui, cx, cy, cw, ch, sel, () -> a().outfit = id);
            Appearance look = a().copy();
            look.outfit = id;
            look.height = 2;
            minis.add(new Mini(400 + n, look, cx + cw / 2, cy + ch - 22, 76, 20));
            ui.text(ui.fit(outfitName(id), F.B7, cw - 10), F.B7, cx + cw / 2, cy + ch - 11, sel ? Ui.WHITE : Ui.TEXT, 1);
        }
        if (pages > 1) {
            float py = y + PANEL_H - 62;
            ui.text((outfitPage + 1) + " / " + pages, F.M6_8, x + PANEL_W / 2, py, Ui.TEXT, 1);
            ui.text("‹", F.B12, x + 80, py, Ui.GOLD, 1);
            ui.text("›", F.B12, x + PANEL_W - 80, py, Ui.GOLD, 1);
            hit(x + 68, py - 8, 24, 16, () -> outfitPage = (outfitPage + pages - 1) % pages);
            hit(x + PANEL_W - 92, py - 8, 24, 16, () -> outfitPage = (outfitPage + 1) % pages);
        }
    }

    private void confirmDialog(Ui ui) {
        ui.rect(0, 0, ui.w, ui.h, 0xB0100A0B);
        float w = 320, h = 150, x = (ui.w - w) / 2, y = (ui.h - h) / 2;
        ui.rr(x, y, w, h, 10, Ui.PANEL);
        ui.text("СОЗДАТЬ ПЕРСОНАЖА?", F.B12, x + w / 2, y + 30, Ui.WHITE, 1);
        ui.divider(x + 24, x + w - 24, y + 48);
        ui.text("Изменить внешность потом сможет", F.M7, x + w / 2, y + 68, Ui.TEXT, 1);
        ui.text("только администрация.", F.M7, x + w / 2, y + 80, Ui.TEXT, 1);
        float by = y + h - 46;
        boolean hc = Ui.inside(mx, my, x + 24, by, 128, 30), hk = Ui.inside(mx, my, x + w - 152, by, 128, 30);
        ui.ghost(x + 24, by, 128, 30, "Отмена", F.M8, hc);
        ui.primary(x + w - 152, by, 128, 30, "Создать", F.B8_5, hk, true);
        hits.clear();
        hit(x + 24, by, 128, 30, () -> {
            confirm = false;
            click();
        });
        hit(x + w - 152, by, 128, 30, () -> {
            confirm = false;
            submit();
            click();
        });
    }

    private void submit() {
        if (sent) return;
        sent = true;
        MosVegasCharacterClient.submit(a().copy());
    }

    private void drawFigure(DrawContext ctx, float k, float cx, float floor, float H) {
        if (client.player == null) return;
        float hs = CharacterPalette.heightScale(a());
        float target = Math.min(400f, (floor - TOP - 44f) / 1.08f);
        float baseSize = target / 1.875f;
        float m = 1f + 0.95f * zoom;
        float size = baseSize * m;
        float mid = (TOP + 26 + H - 56) / 2f;
        float eye = 1.62f * hs;
        float feetFull = floor;
        float feetFace = mid + (eye - 0.1f) * size;
        float feet = feetFull + (feetFace - feetFull) * zoom;
        ctx.enableScissor(Math.round(222 * k), Math.round((TOP + 24) * k), Math.round((width / k - 262) * k), Math.round((H - 50) * k));
        drawEntity(ctx, client.player, cx * k, feet * k, Math.max(1, Math.round(size * k)), yaw);
        ctx.disableScissor();
    }

    private void drawMini(DrawContext ctx, float k, Mini m) {
        OtherClientPlayerEntity e = fakes.computeIfAbsent(m.slot, s -> {
            UUID id = UUID.nameUUIDFromBytes(("mosvegas_character_preview_" + s).getBytes());
            return new OtherClientPlayerEntity(client.world, new GameProfile(id, ""));
        });
        ClientCharacters.set(e.getUuid(), m.look);
        if (m.clip != null)
            ctx.enableScissor(Math.round(m.clip[0] * k), Math.round(m.clip[1] * k), Math.round((m.clip[0] + m.clip[2]) * k), Math.round((m.clip[1] + m.clip[3]) * k));
        drawEntity(ctx, e, m.x * k, m.feet * k, Math.max(1, Math.round(m.height / 1.875f * k)), m.yaw);
        if (m.clip != null) ctx.disableScissor();
    }

    private static void drawEntity(DrawContext ctx, AbstractClientPlayerEntity e, float x, float y, int size, float yaw) {
        Quaternionf rot = new Quaternionf().rotateZ((float) Math.PI).rotateY((float) Math.toRadians(yaw));
        float bodyYaw = e.bodyYaw, prevBody = e.prevBodyYaw, headYaw = e.headYaw, prevHead = e.prevHeadYaw;
        float entYaw = e.getYaw(), prevYaw = e.prevYaw, pitch = e.getPitch(), prevPitch = e.prevPitch;
        e.bodyYaw = 180f;
        e.prevBodyYaw = 180f;
        e.setYaw(180f);
        e.prevYaw = 180f;
        e.setPitch(0f);
        e.prevPitch = 0f;
        e.headYaw = 180f;
        e.prevHeadYaw = 180f;
        boolean hud = MinecraftClient.getInstance().options.hudHidden;
        MinecraftClient.getInstance().options.hudHidden = true;
        InventoryScreen.drawEntity(ctx, x, y, size, new Vector3f(), rot, null, e);
        MinecraftClient.getInstance().options.hudHidden = hud;
        e.bodyYaw = bodyYaw;
        e.prevBodyYaw = prevBody;
        e.setYaw(entYaw);
        e.prevYaw = prevYaw;
        e.setPitch(pitch);
        e.prevPitch = prevPitch;
        e.headYaw = headYaw;
        e.prevHeadYaw = prevHead;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        float k = Ui.scaleFor(width, height);
        float x = Ui.toDesign(mouseX, k), y = Ui.toDesign(mouseY, k);
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (Ui.inside(x, y, h.x, h.y, h.w, h.h)) {
                h.action.run();
                return true;
            }
        }
        if (!confirm && x > 222 && x < width / k - 262 && y > TOP + 24 && y < height / k - 50) {
            dragging = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (dragging) {
            yaw = (yaw - (float) dx * 1.1f) % 360f;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (confirm) return false;
        zoomTarget = Math.max(0f, Math.min(1f, zoomTarget + (float) vertical * 0.2f));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (confirm) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) confirm = false;
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                confirm = false;
                submit();
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_RIGHT) {
            if (step == STEPS.length - 1 && keyCode != GLFW.GLFW_KEY_RIGHT) confirm = true;
            else go(1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            go(-1);
            return true;
        }
        return false;
    }

    private void leave() {
        ClientCharacters.releaseEditor();
        boolean single = client.isInSingleplayer();
        if (client.world != null) client.world.disconnect();
        if (single) client.disconnect(new MessageScreen(Text.translatable("menu.savingLevel")));
        else client.disconnect();
        TitleScreen title = new TitleScreen();
        client.setScreen(single ? title : new MultiplayerScreen(title));
    }

    @Override
    public void removed() {
        for (OtherClientPlayerEntity e : fakes.values()) ClientCharacters.set(e.getUuid(), null);
        fakes.clear();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private record Hit(float x, float y, float w, float h, Runnable action) {}

    private record Mini(int slot, Appearance look, float x, float feet, float height, float yaw, float[] clip) {
        Mini(int slot, Appearance look, float x, float feet, float height, float yaw) {
            this(slot, look, x, feet, height, yaw, null);
        }
    }
}
