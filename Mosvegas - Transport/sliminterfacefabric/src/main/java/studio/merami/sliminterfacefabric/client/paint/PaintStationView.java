package studio.merami.sliminterfacefabric.client.paint;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import studio.merami.slimcore.SlimCore;
import studio.merami.slimcore.items.AItemBase;
import studio.merami.slimcore.items.SlimItems;
import studio.merami.slimcore.paint.PaintStations;
import studio.merami.sliminterfacefabric.client.render.PropModel;
import studio.merami.sliminterfacefabric.item.BuilderItem;

public final class PaintStationView {
    private static final double DRAW_DISTANCE = 96;
    private static final float LABEL_SCALE = .04f;
    private static final int LABEL_BACKGROUND = 0x60000000;
    private static final int HIDDEN_TEXT = 0x20ffffff;
    private static volatile PaintStations stations = PaintStations.EMPTY;

    private PaintStationView() {}

    public static void registerTooltips() {
        ItemTooltipCallback.EVENT.register((stack, context, lines) -> {
            if (!(stack.getItem() instanceof BuilderItem builder)) return;
            AItemBase item = builder.item;
            if (item != SlimItems.ZONE_MARKER && item != SlimItems.PAINT_STAND) return;
            String key = item == SlimItems.ZONE_MARKER ? "item.slimvehicle.zone_marker.hint" : "item.slimvehicle.paint_stand.hint";
            for (String line : I18n.get(key).split("\n")) lines.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
        });
    }

    public static void accept(String json) {
        try {
            stations = PaintStations.fromJson(json);
        } catch (RuntimeException e) {
            SlimCore.LOGGER.error("Paint stations from the server were not read: {}", e.getMessage());
        }
    }

    public static void clear() {
        stations = PaintStations.EMPTY;
    }

    public static PaintStations stations() {
        return stations;
    }

    public static void render(WorldRenderContext context) {
        PaintStations current = stations;
        ClientLevel level = context.world();
        LocalPlayer player = Minecraft.getInstance().player;
        if (current.stations.isEmpty() || level == null || player == null) return;
        Vec3 camera = context.camera().getPosition();
        PoseStack pose = context.matrixStack();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        for (PaintStations.Station station : current.stations) {
            for (PaintStations.Stand stand : station.stands()) {
                if (stand.distanceSqr(camera.x, camera.y, camera.z) > DRAW_DISTANCE * DRAW_DISTANCE) continue;
                pose.pushPose();
                pose.translate(stand.x() + .5 - camera.x, stand.y() - camera.y, stand.z() + .5 - camera.z);
                pose.mulPose(Axis.YP.rotationDegrees(180 - stand.facing()));
                PropModel.PAINT_STATION.draw(pose, buffers, LevelRenderer.getLightColor(level, new BlockPos(stand.x(), stand.y(), stand.z())), OverlayTexture.NO_OVERLAY);
                pose.popPose();
            }
        }
        buffers.endBatch(PropModel.PAINT_STATION.renderType());
        if (!holdsMarker(player)) return;
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        for (PaintStations.Station station : current.stations) {
            PaintStations.Zone zone = station.zone();
            LevelRenderer.renderLineBox(pose, lines, zone.minX() - camera.x, zone.minY() - camera.y, zone.minZ() - camera.z,
                    zone.maxX() + 1 - camera.x, zone.maxY() + 1 - camera.y, zone.maxZ() + 1 - camera.z, 1, .25f, .2f, 1);
        }
        buffers.endBatch(RenderType.lines());
        Font font = Minecraft.getInstance().font;
        for (PaintStations.Station station : current.stations) {
            PaintStations.Zone zone = station.zone();
            double x = (zone.minX() + zone.maxX() + 1) / 2.0;
            double z = (zone.minZ() + zone.maxZ() + 1) / 2.0;
            if (station.zone().distanceSqr(camera.x, camera.y, camera.z) > DRAW_DISTANCE * DRAW_DISTANCE) continue;
            pose.pushPose();
            pose.translate(x - camera.x, zone.maxY() + 1.6 - camera.y, z - camera.z);
            pose.mulPose(context.camera().rotation());
            pose.scale(-LABEL_SCALE, -LABEL_SCALE, LABEL_SCALE);
            label(font, pose, buffers, Component.translatable("gui.slimvehicle.station.label", station.id()).withStyle(ChatFormatting.BOLD), 0, 0xffffffff);
            label(font, pose, buffers, Component.translatable("gui.slimvehicle.station.label_hint"), 11, 0xffd0d6dc);
            pose.popPose();
        }
        buffers.endBatch();
    }

    public static void label(Font font, PoseStack pose, MultiBufferSource buffers, Component text, int y, int color) {
        float x = -font.width(text) / 2f;
        font.drawInBatch(text, x, y, HIDDEN_TEXT, false, pose.last().pose(), buffers, Font.DisplayMode.SEE_THROUGH, LABEL_BACKGROUND, LightTexture.FULL_BRIGHT);
        font.drawInBatch(text, x, y, color, false, pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
    }

    private static boolean holdsMarker(LocalPlayer player) {
        return marker(player.getMainHandItem().getItem()) || marker(player.getOffhandItem().getItem());
    }

    private static boolean marker(net.minecraft.world.item.Item item) {
        return item instanceof BuilderItem builder && builder.item == SlimItems.ZONE_MARKER;
    }
}
