package studio.merami.sliminterfacefabric.client.paint;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import studio.merami.slimcore.entities.instances.EntityVehicleF_Physics;
import studio.merami.slimcore.entities.instances.VehicleAction;
import studio.merami.slimcore.items.SlimItems;
import studio.merami.slimcore.paint.PaintAim;
import studio.merami.slimcore.paint.PaintJob;
import studio.merami.slimcore.paint.PaintStations;
import studio.merami.sliminterfacefabric.SlimVehicle;
import studio.merami.sliminterfacefabric.client.hud.VehicleHud;
import studio.merami.sliminterfacefabric.client.VehicleControls;
import studio.merami.sliminterfacefabric.entity.BuilderVehicle;
import studio.merami.sliminterfacefabric.registry.ItemRegistry;
import studio.merami.sliminterfacefabric.client.hud.HudText;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class PaintGuide {
    public enum Step { EXIT, TAKE_GUN, PAINT, RETURN_GUN }

    private static final List<PaintJob.Area> WALK = List.of(PaintJob.Area.LEFT, PaintJob.Area.FRONT, PaintJob.Area.RIGHT,
            PaintJob.Area.REAR, PaintJob.Area.TOP, PaintJob.Area.INTERIOR);
    private static final double SEARCH = 64;
    private static final double MARKER_RANGE = 48;
    private static final long BANNER_MS = 3500;
    private static final ResourceLocation GAUGE = texture("gauge");
    private static final ResourceLocation RING_FILL = texture("ring");
    private static final ResourceLocation RING_GLOSS = texture("gloss");
    private static final ResourceLocation RING_GLOW = texture("glow");
    private static final ResourceLocation DOT = texture("dot");
    private static final ResourceLocation DIGITS = texture("digits");
    private static final ResourceLocation PILL = texture("pill");
    private static final ResourceLocation KEY = texture("key");
    private static final ResourceLocation PIP = texture("pip");
    private static final int RING = 52;
    private static final int RING_TEXELS = 208;
    private static final int RING_STEPS = 96;
    private static final float RING_RADIUS = RING * 79f / RING_TEXELS;
    private static final float RING_REACH = RING * .75f;
    private static final float DOT_SIZE = 9.6f;
    private static final int DOT_TEXELS = 48;
    private static final int DIGIT_W = 44;
    private static final int DIGIT_H = 72;
    private static final int DIGIT_CELLS = 11;
    private static final int PERCENT_CELL = 10;
    private static final float DIGIT_SIZE = 18.5f;
    private static final float DIGIT_ADVANCE = .56f;
    private static final float SIGN_SIZE = 10.5f;
    private static final float SIGN_ADVANCE = .52f;
    private static final float WIDE_NUMBER = .8f;
    private static final int PILL_TEXTURE_W = 64;
    private static final int PILL_TEXTURE_H = 60;
    private static final int PILL_CAP = 4;
    private static final int PILL_HEIGHT = 15;
    private static final int PILL_PADDING = 8;
    private static final int KEY_TEXELS = 44;
    private static final int KEY_CAP = 3;
    private static final int PIP_WIDTH = 11;
    private static final int PIP_HEIGHT = 3;
    private static final int PIP_GAP = 2;
    private static final int PIP_TEXTURE_W = 44;
    private static final int PIP_TEXTURE_H = 12;
    private static final long FLASH_MS = 700;
    private static final float FLASH_POP = .14f;
    private static final double EASE_MS = 90;
    private static final long BANNER_IN_MS = 180;
    private static final long BANNER_OUT_MS = 500;
    private static final int LOW_SECONDS = 30;
    private static final float DARK_PAINT = .32f;
    private static final int TEXT = 0xfff2f5f7;
    private static final int DIM = 0xffb4bcc3;
    private static final int RULE = 0x38ffffff;
    private static final int GREEN = 0xff52d36a;
    private static final int AMBER = 0xffffb12a;
    private static final int RED = 0xffe23b3b;
    private static final int KEY_HEIGHT = 11;
    public static final int LINE_HEIGHT = KEY_HEIGHT + 6;
    private static final int STACK_GAP = 4;
    private static final int STACK = RING + 3 + PILL_HEIGHT + 3 + PIP_HEIGHT;
    public static final int BELOW_CROSSHAIR = 14;
    private static final int MARKER = 0xffffd23a;
    private static final float ICON_SCALE = .09f;
    private static final float LABEL_SCALE = .03f;

    private static EntityVehicleF_Physics job;
    private static Step step;
    private static PaintJob.Area side;
    private static UUID watched;
    private static int lastCoats = -1;
    private static long progressAt;
    private static long bannerAt = Long.MIN_VALUE;
    private static long flashAt = Long.MIN_VALUE;
    private static float shown;
    private static long shownAt;

    private PaintGuide() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(PaintGuide::tick);
        HudRenderCallback.EVENT.register(PaintGuide::hud);
        WorldRenderEvents.AFTER_ENTITIES.register(PaintGuide::marker);
    }

    public static PaintJob.Area highlight(EntityVehicleF_Physics vehicle) {
        return step == Step.PAINT && vehicle == job ? side : null;
    }

    public static float pulse() {
        return .07f + .13f * (float) (.5 + .5 * Math.sin(Util.getMillis() / 260.0));
    }

    private static ResourceLocation texture(String name) {
        return SlimVehicle.id("textures/gui/paint/" + name + ".png");
    }

    private static void tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || !net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(studio.merami.sliminterfacefabric.network.PacketPaintOpen.TYPE)) {
            job = null;
            step = null;
            watched = null;
            return;
        }
        EntityVehicleF_Physics found = null;
        EntityVehicleF_Physics finished = null;
        for (BuilderVehicle entity : mc.level.getEntitiesOfClass(BuilderVehicle.class, player.getBoundingBox().inflate(SEARCH))) {
            EntityVehicleF_Physics vehicle = entity.vehicle();
            if (vehicle == null) continue;
            if (vehicle.painting() && player.getUUID().equals(vehicle.paintOwner())) found = vehicle;
            else if (vehicle.uuid().equals(watched)) finished = vehicle;
        }
        long now = Util.getMillis();
        if (found == null && finished != null) bannerAt = now;
        if (found != null) {
            if (!found.uuid().equals(watched) || found.paintCoats() > lastCoats) progressAt = now;
            lastCoats = found.paintCoats();
        }
        job = found;
        watched = found == null ? null : found.uuid();
        boolean gun = hasGun(player);
        if (found != null) step = BuilderVehicle.ridden(player) == found ? Step.EXIT : gun ? Step.PAINT : Step.TAKE_GUN;
        else step = gun ? Step.RETURN_GUN : null;
        PaintJob.Area previous = side;
        side = null;
        if (found != null) {
            for (PaintJob.Area next : WALK) {
                if (PaintJob.share(found.paintCoats(), next) < 1) {
                    side = next;
                    break;
                }
            }
        }
        if (previous != null && side != null && previous != side) {
            flashAt = now;
            shown = 0;
        }
    }

    private static boolean hasGun(LocalPlayer player) {
        for (List<ItemStack> list : List.of(player.getInventory().items, player.getInventory().offhand)) {
            for (ItemStack stack : list) {
                if (stack.is(ItemRegistry.item(SlimItems.SPRAY_GUN))) return true;
            }
        }
        return false;
    }

    private static void hud(GuiGraphics gui, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.screen != null) return;
        Font font = mc.font;
        int centre = gui.guiWidth() / 2;
        int bottom = VehicleHud.freeBottom(gui);
        int top = bottom - LINE_HEIGHT;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        banner(gui, font, centre);
        if (step != null) {
            switch (step) {
                case EXIT -> line(gui, font, centre, top, VehicleControls.keyName(VehicleAction.EXIT), I18n.get("gui.slimvehicle.paint_guide.exit"), TEXT);
                case TAKE_GUN -> line(gui, font, centre, top, VehicleControls.useKeyName(), I18n.get("gui.slimvehicle.paint_guide.take"), TEXT);
                case RETURN_GUN -> line(gui, font, centre, top, VehicleControls.useKeyName(), I18n.get("gui.slimvehicle.paint_guide.return"), TEXT);
                case PAINT -> paint(gui, font, centre, Math.min(gui.guiHeight() / 2 + BELOW_CROSSHAIR, bottom - STACK - STACK_GAP), gui.guiHeight() / 2 - BELOW_CROSSHAIR - LINE_HEIGHT);
            }
        }
        RenderSystem.disableBlend();
    }

    private static void paint(GuiGraphics gui, Font font, int centre, int top, int above) {
        if (job == null || side == null) return;
        Minecraft mc = Minecraft.getInstance();
        String reach = reachHint();
        boolean inside = reach == null && side == PaintJob.Area.INTERIOR && !aimingInside();
        boolean held = mc.options.keyAttack.isDown();
        boolean spray = reach == null && !inside && !held;
        PaintAim.Aim aim = PaintSprayClient.aim();
        boolean active = held && aim != null && aim.inReach();
        long now = Util.getMillis();
        int coats = job.paintCoats();
        float share = PaintJob.share(coats, side);
        float ease = 1 - (float) Math.exp(-(now - shownAt) / EASE_MS);
        shownAt = now;
        shown = share < shown ? share : shown + (share - shown) * ease;
        int color = readable(0xff000000 | Math.max(0, side == PaintJob.Area.INTERIOR ? job.paintInterior() : job.paintBody()));
        ring(gui, centre, top + RING / 2, color, share, active, now);
        int row = top + RING + 3;
        details(gui, font, centre, row, now);
        pips(gui, centre, row + PILL_HEIGHT + 3, color, coats);
        if (reach != null) {
            hint(gui, font, centre, above, reach);
        } else if (inside) {
            hint(gui, font, centre, above, I18n.get("gui.slimvehicle.paint_guide.interior"));
        } else if (spray) {
            line(gui, font, centre, above, mc.options.keyAttack.getTranslatedKeyMessage().getString().toUpperCase(Locale.ROOT),
                    I18n.get("gui.slimvehicle.paint_guide.spray"), TEXT);
        }
    }

    private static void ring(GuiGraphics gui, int x, int y, int color, float target, boolean active, long now) {
        long flash = now - flashAt;
        float fade = flash >= 0 && flash < FLASH_MS ? 1 - flash / (float) FLASH_MS : 0;
        float pop = 1 + FLASH_POP * fade * fade;
        float fill = Mth.clamp(shown, 0, 1);
        PoseStack pose = gui.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(pop, pop, 1);
        sprite(gui, GAUGE, RING, RING_TEXELS);
        if (active) sector(gui, RING_GLOW, 0, Math.max(fill, .02f), color, .55f + .25f * (float) Math.sin(now / 110.0));
        if (target > fill) sector(gui, RING_FILL, fill, target, color, .35f);
        if (fill > 0) {
            sector(gui, RING_FILL, 0, fill, color, 1);
            sector(gui, RING_GLOSS, 0, fill, 0xffffffff, .35f);
            dot(gui, 0, color);
            dot(gui, fill, lighten(color, .55f));
        }
        if (fade > 0) sector(gui, RING_FILL, 0, 1, GREEN, fade * fade);
        percent(gui, Math.round(fill * 100));
        pose.popPose();
    }

    private static void sprite(GuiGraphics gui, ResourceLocation texture, float size, int texels) {
        PoseStack pose = gui.pose();
        pose.pushPose();
        pose.translate(-size / 2, -size / 2, 0);
        pose.scale(size / texels, size / texels, 1);
        gui.blit(texture, 0, 0, 0, 0, texels, texels, texels, texels);
        pose.popPose();
    }

    private static void sector(GuiGraphics gui, ResourceLocation texture, float from, float to, int color, float alpha) {
        gui.flush();
        Matrix4f matrix = gui.pose().last().pose();
        float red = (color >> 16 & 255) / 255f;
        float green = (color >> 8 & 255) / 255f;
        float blue = (color & 255) / 255f;
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.disableCull();
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_TEX_COLOR);
        builder.vertex(matrix, 0, 0, 0).uv(.5f, .5f).color(red, green, blue, alpha).endVertex();
        int steps = Math.max(2, Mth.ceil((to - from) * RING_STEPS));
        for (int i = 0; i <= steps; i++) {
            float angle = Mth.TWO_PI * (from + (to - from) * i / steps) - Mth.HALF_PI;
            float dx = Mth.cos(angle) * RING_REACH;
            float dy = Mth.sin(angle) * RING_REACH;
            builder.vertex(matrix, dx, dy, 0).uv(.5f + dx / RING, .5f + dy / RING).color(red, green, blue, alpha).endVertex();
        }
        BufferUploader.drawWithShader(builder.end());
        RenderSystem.enableCull();
    }

    private static void dot(GuiGraphics gui, float position, int color) {
        float angle = Mth.TWO_PI * position - Mth.HALF_PI;
        PoseStack pose = gui.pose();
        pose.pushPose();
        pose.translate(Mth.cos(angle) * RING_RADIUS, Mth.sin(angle) * RING_RADIUS, 0);
        tint(gui, color, 1);
        sprite(gui, DOT, DOT_SIZE, DOT_TEXELS);
        gui.setColor(1, 1, 1, 1);
        pose.popPose();
    }

    private static void tint(GuiGraphics gui, int color, float alpha) {
        gui.setColor((color >> 16 & 255) / 255f, (color >> 8 & 255) / 255f, (color & 255) / 255f, alpha);
    }

    private static void percent(GuiGraphics gui, int value) {
        String text = Integer.toString(value);
        float size = text.length() > 2 ? DIGIT_SIZE * WIDE_NUMBER : DIGIT_SIZE;
        float advance = size * DIGIT_ADVANCE;
        float sign = SIGN_SIZE * SIGN_ADVANCE;
        float x = -(advance * text.length() + sign) / 2;
        for (int i = 0; i < text.length(); i++) {
            glyph(gui, text.charAt(i) - '0', x + (advance - size * DIGIT_W / DIGIT_H) / 2, -size / 2, size);
            x += advance;
        }
        glyph(gui, PERCENT_CELL, x + (sign - SIGN_SIZE * DIGIT_W / DIGIT_H) / 2, -SIGN_SIZE * .72f, SIGN_SIZE);
    }

    private static void glyph(GuiGraphics gui, int cell, float x, float y, float size) {
        PoseStack pose = gui.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(size / DIGIT_H, size / DIGIT_H, 1);
        gui.blit(DIGITS, 0, 0, cell * DIGIT_W, 0, DIGIT_W, DIGIT_H, DIGIT_W * DIGIT_CELLS, DIGIT_H);
        pose.popPose();
    }

    private static void details(GuiGraphics gui, Font font, int centre, int y, long now) {
        Component area = Component.translatable(areaKey(job, side)).withStyle(ChatFormatting.BOLD);
        int seconds = (int) Math.max(0, (PaintJob.TIMEOUT_MS - (now - progressAt) + 999) / 1000);
        String timer = seconds / 60 + ":" + String.format("%02d", seconds % 60);
        int timerColor = seconds > LOW_SECONDS ? DIM : seconds > 10 || now / 400 % 2 == 0 ? AMBER : RED;
        int areaWidth = font.width(area);
        int timerWidth = font.width(timer);
        int width = PILL_PADDING + areaWidth + 5 + 1 + 5 + timerWidth + PILL_PADDING;
        int x = centre - width / 2;
        pill(gui, x, y, width, PILL_HEIGHT);
        int textY = y + (PILL_HEIGHT - 8) / 2;
        HudText.drawString(gui, font, area, x + PILL_PADDING, textY, TEXT, true);
        int rule = x + PILL_PADDING + areaWidth + 5;
        gui.fill(rule, y + 3, rule + 1, y + PILL_HEIGHT - 3, RULE);
        HudText.drawString(gui, font, timer, rule + 6, textY, timerColor, true);
    }

    private static void pips(GuiGraphics gui, int centre, int y, int color, int coats) {
        int total = WALK.size() * PIP_WIDTH + (WALK.size() - 1) * PIP_GAP;
        int x = centre - total / 2;
        for (PaintJob.Area area : WALK) {
            gui.blit(PIP, x, y, PIP_WIDTH, PIP_HEIGHT, 0, 0, PIP_TEXTURE_W, PIP_TEXTURE_H, PIP_TEXTURE_W, PIP_TEXTURE_H * 2);
            float part = area == side ? shown : PaintJob.share(coats, area);
            int filled = part <= 0 ? 0 : Math.max(2, Math.round(PIP_WIDTH * Mth.clamp(part, 0, 1)));
            if (filled > 0) {
                tint(gui, color, 1);
                gui.blit(PIP, x, y, filled, PIP_HEIGHT, 0, PIP_TEXTURE_H, PIP_TEXTURE_W * filled / PIP_WIDTH, PIP_TEXTURE_H, PIP_TEXTURE_W, PIP_TEXTURE_H * 2);
                gui.setColor(1, 1, 1, 1);
            }
            x += PIP_WIDTH + PIP_GAP;
        }
    }

    private static int readable(int color) {
        float luminance = (.2126f * (color >> 16 & 255) + .7152f * (color >> 8 & 255) + .0722f * (color & 255)) / 255;
        return luminance < DARK_PAINT ? lighten(color, DARK_PAINT - luminance + .14f) : color;
    }

    private static String areaKey(EntityVehicleF_Physics vehicle, PaintJob.Area area) {
        for (Map.Entry<String, PaintJob.Area> part : vehicle.definition().paintAreaBones.entrySet()) {
            if (part.getValue() == area) return "gui.slimvehicle.paint.part." + part.getKey();
        }
        return "message.slimvehicle.paint.side." + area.name().toLowerCase(Locale.ROOT);
    }

    private static void hint(GuiGraphics gui, Font font, int centre, int top, String text) {
        int width = font.width(text) + 16;
        pill(gui, centre - width / 2, top, width, LINE_HEIGHT);
        HudText.drawCenteredString(gui, font, text, centre, top + 5, AMBER);
    }

    private static int lighten(int color, float amount) {
        int r = color >> 16 & 255;
        int g = color >> 8 & 255;
        int b = color & 255;
        r += Math.round((255 - r) * amount);
        g += Math.round((255 - g) * amount);
        b += Math.round((255 - b) * amount);
        return color & 0xff000000 | r << 16 | g << 8 | b;
    }

    private static String reachHint() {
        PaintAim.Aim aim = PaintSprayClient.aim();
        if (aim == null) return null;
        if (aim.tooClose()) return I18n.get("gui.slimvehicle.paint_guide.back");
        if (aim.tooFar()) return I18n.get("gui.slimvehicle.paint_guide.closer");
        return null;
    }

    private static boolean aimingInside() {
        PaintAim.Aim aim = PaintSprayClient.aim();
        return aim != null && aim.area() == PaintJob.Area.INTERIOR;
    }

    private static void banner(GuiGraphics gui, Font font, int centre) {
        long since = Util.getMillis() - bannerAt;
        if (since < 0 || since > BANNER_MS) return;
        float alpha = Math.min(Math.min(1, since / (float) BANNER_IN_MS), Math.min(1, (BANNER_MS - since) / (float) BANNER_OUT_MS));
        String text = I18n.get("gui.slimvehicle.paint_guide.done");
        int y = gui.guiHeight() / 4;
        PoseStack pose = gui.pose();
        pose.pushPose();
        pose.translate(centre, y, 0);
        pose.scale(2, 2, 1);
        int width = font.width(text) + 16;
        gui.setColor(1, 1, 1, alpha);
        pill(gui, -width / 2, -4, width, 16);
        gui.setColor(1, 1, 1, 1);
        HudText.drawCenteredString(gui, font, text, 0, 0, Math.max(4, Math.round(alpha * 255)) << 24 | GREEN & 0xffffff);
        pose.popPose();
    }

    public static void line(GuiGraphics gui, Font font, int centre, int top, String key, String text, int color) {
        int keyWidth = Math.max(KEY_HEIGHT, font.width(key) + 6);
        int width = keyWidth + 5 + font.width(text) + 12;
        int x = centre - width / 2;
        pill(gui, x, top, width, LINE_HEIGHT);
        int keyX = x + 6;
        slice(gui, KEY, keyX, top + 3, keyWidth, KEY_HEIGHT, KEY_CAP, KEY_TEXELS, KEY_TEXELS);
        HudText.drawString(gui, font, key, keyX + (keyWidth - font.width(key) + 1) / 2, top + 5, TEXT, false);
        HudText.drawString(gui, font, text, keyX + keyWidth + 5, top + 5, color, true);
    }

    private static void pill(GuiGraphics gui, int x, int y, int width, int height) {
        slice(gui, PILL, x, y, width, height, PILL_CAP, PILL_TEXTURE_W, PILL_TEXTURE_H);
    }

    private static void slice(GuiGraphics gui, ResourceLocation texture, int x, int y, int width, int height, int cap, int textureW, int textureH) {
        int texels = cap * textureH / height;
        gui.blit(texture, x, y, cap, height, 0, 0, texels, textureH, textureW, textureH);
        gui.blit(texture, x + cap, y, width - 2 * cap, height, texels, 0, textureW - 2 * texels, textureH, textureW, textureH);
        gui.blit(texture, x + width - cap, y, cap, height, textureW - texels, 0, texels, textureH, textureW, textureH);
    }

    private static void marker(WorldRenderContext context) {
        if (step != Step.TAKE_GUN && step != Step.RETURN_GUN) return;
        Minecraft mc = Minecraft.getInstance();
        Vec3 camera = context.camera().getPosition();
        PaintStations.Stand stand = nearestStand(camera);
        if (stand == null) return;
        double bob = Math.sin(Util.getMillis() / 300.0) * .08;
        PoseStack pose = context.matrixStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Font font = mc.font;
        pose.pushPose();
        pose.translate(stand.x() + .5 - camera.x, stand.y() + 2.3 + bob - camera.y, stand.z() + .5 - camera.z);
        pose.mulPose(context.camera().rotation());
        pose.pushPose();
        pose.scale(-ICON_SCALE, -ICON_SCALE, ICON_SCALE);
        PaintStationView.label(font, pose, buffers, Component.literal(" ! ").withStyle(ChatFormatting.BOLD), -4, MARKER);
        pose.popPose();
        pose.translate(0, -.5, 0);
        pose.scale(-LABEL_SCALE, -LABEL_SCALE, LABEL_SCALE);
        String key = step == Step.TAKE_GUN ? "gui.slimvehicle.paint_guide.take_marker" : "gui.slimvehicle.paint_guide.return_marker";
        PaintStationView.label(font, pose, buffers, Component.translatable(key).withStyle(ChatFormatting.BOLD), 0, MARKER);
        pose.popPose();
        buffers.endBatch();
    }

    private static PaintStations.Stand nearestStand(Vec3 from) {
        PaintStations.Stand best = null;
        double bestDistance = MARKER_RANGE * MARKER_RANGE;
        for (PaintStations.Station station : PaintStationView.stations().stations) {
            for (PaintStations.Stand stand : station.stands()) {
                double distance = stand.distanceSqr(from.x, from.y, from.z);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = stand;
                }
            }
        }
        return best;
    }
}
