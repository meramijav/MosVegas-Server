package studio.merami.sliminterfacefabric.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.phys.EntityHitResult;
import org.lwjgl.glfw.GLFW;
import studio.merami.slimcore.entities.instances.EntityVehicleF_Physics;
import studio.merami.slimcore.entities.instances.VehicleAction;
import studio.merami.slimcore.items.ItemKey;
import studio.merami.sliminterfacefabric.client.hud.InteractionOverlay;
import studio.merami.sliminterfacefabric.client.paint.PaintShopClient;
import studio.merami.sliminterfacefabric.entity.BuilderVehicle;
import studio.merami.sliminterfacefabric.network.PacketVehicleAction;
import studio.merami.sliminterfacefabric.network.PacketVehicleInput;
import studio.merami.sliminterfacefabric.wrapper.WrapperEntity;
import studio.merami.sliminterfacefabric.wrapper.WrapperItemStack;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class VehicleControls {
    private static final String CATEGORY = "key.categories.slimvehicle";
    private static final Map<KeyMapping, Boolean> HELD = new IdentityHashMap<>();
    private static final String KEYBOARD = "key.keyboard.";
    private static final Map<String, String> SYMBOLS = Map.ofEntries(Map.entry("semicolon", ";"), Map.entry("apostrophe", "'"),
            Map.entry("comma", ","), Map.entry("period", "."), Map.entry("slash", "/"), Map.entry("backslash", "\\"),
            Map.entry("left.bracket", "["), Map.entry("right.bracket", "]"), Map.entry("grave.accent", "`"),
            Map.entry("minus", "-"), Map.entry("equal", "="));
    private static KeyMapping engine;
    private static KeyMapping up;
    private static KeyMapping down;
    private static KeyMapping lights;
    private static KeyMapping horn;
    private static KeyMapping lock;
    private static KeyMapping siren;
    private static KeyMapping radio;
    private static KeyMapping radioNext;
    private static KeyMapping radioPrevious;
    private static KeyMapping window;
    private static KeyMapping handbrake;
    private static KeyMapping enter;
    private static KeyMapping interact;
    private static KeyMapping megaphone;
    private static KeyMapping wipers;
    private static int interactionCooldown;

    private VehicleControls() {}

    static void register() {
        engine = key("engine", GLFW.GLFW_KEY_R);
        up = key("gear_up", GLFW.GLFW_KEY_X);
        down = key("gear_down", GLFW.GLFW_KEY_Z);
        lights = key("lights", GLFW.GLFW_KEY_H);
        horn = key("horn", GLFW.GLFW_KEY_G);
        lock = key("lock", GLFW.GLFW_KEY_K);
        siren = key("siren", GLFW.GLFW_KEY_V);
        radio = key("radio", GLFW.GLFW_KEY_M);
        radioNext = key("radio_next", GLFW.GLFW_KEY_PERIOD);
        radioPrevious = key("radio_previous", GLFW.GLFW_KEY_COMMA);
        window = key("window", GLFW.GLFW_KEY_J);
        handbrake = key("handbrake", GLFW.GLFW_KEY_B);
        enter = key("enter", GLFW.GLFW_KEY_F);
        interact = key("interact", GLFW.GLFW_KEY_U);
        megaphone = key("megaphone", GLFW.GLFW_KEY_N);
        wipers = key("wipers", GLFW.GLFW_KEY_Y);
    }

    private static KeyMapping key(String id, int key) {
        return KeyBindingHelper.registerKeyBinding(new KeyMapping("key.slimvehicle." + id, InputConstants.Type.KEYSYM, key, CATEGORY));
    }

    public static String keyName(VehicleAction action) {
        KeyMapping key = switch (action) {
            case ENGINE -> engine;
            case GEAR_UP -> up;
            case GEAR_DOWN -> down;
            case LIGHTS -> lights;
            case HORN -> horn;
            case LOCK -> lock;
            case SIREN -> siren;
            case RADIO -> radio;
            case RADIO_NEXT -> radioNext;
            case RADIO_PREVIOUS -> radioPrevious;
            case WINDOW -> window;
            case WIPERS -> wipers;
            case EXIT, ENTER -> enter;
            case INTERACT -> interact;
            case MEGAPHONE -> megaphone;
        };
        return label(key);
    }

    public static String handbrakeName() {
        return label(handbrake);
    }

    private static String label(KeyMapping key) {
        if (key == null) return "?";
        InputConstants.Key bound = KeyBindingHelper.getBoundKeyOf(key);
        if (bound.getType() == InputConstants.Type.KEYSYM && bound.getName().startsWith(KEYBOARD)) {
            String name = bound.getName().substring(KEYBOARD.length());
            if (name.length() == 1) return name.toUpperCase(Locale.ROOT);
            String symbol = SYMBOLS.get(name);
            if (symbol != null) return symbol;
        }
        return key.getTranslatedKeyMessage().getString().toUpperCase(Locale.ROOT);
    }

    public static boolean handbrakeHeld() {
        return handbrake != null && Minecraft.getInstance().screen == null && isDown(handbrake);
    }

    static void tickSettings(Minecraft mc) {
        if (interactionCooldown > 0) interactionCooldown--;
    }

    static void tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null) return;
        if (mc.screen != null) {
            for (KeyMapping key : new KeyMapping[]{engine, up, down, lights, horn, lock, siren, radio, radioNext, radioPrevious, window, handbrake, enter, interact, megaphone, wipers}) {
                HELD.put(key, isDown(key));
                while (key.consumeClick()) {
                }
            }
        }
        DriverPose.clampLook(player);
        if (player.getVehicle() == null && !player.noPhysics) {
            for (BuilderVehicle vehicle : mc.level.getEntitiesOfClass(BuilderVehicle.class, player.getBoundingBox())) {
                if (vehicle.vehicle().pushOut(WrapperEntity.of(player))) break;
            }
        }
        EntityVehicleF_Physics vehicle = BuilderVehicle.ridden(player);
        if (vehicle != null) {
            if (pressed(enter)) {
                send(vehicle, VehicleAction.EXIT);
                return;
            }
            long windowHandle = mc.getWindow().getWindow();
            float throttle = mc.options.keyUp.isDown() || InputConstants.isKeyDown(windowHandle, GLFW.GLFW_KEY_UP) ? 1 : 0;
            boolean craft = vehicle.craft();
            float brake = mc.options.keyDown.isDown() || !craft && mc.options.keyJump.isDown() || InputConstants.isKeyDown(windowHandle, GLFW.GLFW_KEY_DOWN) ? 1 : 0;
            float steer = (mc.options.keyLeft.isDown() || InputConstants.isKeyDown(windowHandle, GLFW.GLFW_KEY_LEFT) ? 1 : 0)
                    - (mc.options.keyRight.isDown() || InputConstants.isKeyDown(windowHandle, GLFW.GLFW_KEY_RIGHT) ? 1 : 0);
            float lift = craft ? (mc.options.keyJump.isDown() ? 1 : 0) - (mc.options.keySprint.isDown() || InputConstants.isKeyDown(windowHandle, GLFW.GLFW_KEY_LEFT_CONTROL) ? 1 : 0) : 0;
            if (mc.screen != null) {
                throttle = 0;
                brake = 0;
                steer = 0;
                lift = 0;
            }
            EntityVehicleF_Physics paintShop = PaintShopClient.parkedVehicle(mc);
            if (paintShop != null && pressed(horn)) PaintShopClient.request(mc, paintShop);
            ClientPlayNetworking.send(new PacketVehicleInput(vehicle.id(), throttle, brake, steer, mc.screen == null && isDown(handbrake), mc.screen == null && paintShop == null && isDown(horn), lift));
            action(engine, VehicleAction.ENGINE);
            action(up, VehicleAction.GEAR_UP);
            action(down, VehicleAction.GEAR_DOWN);
            action(lights, VehicleAction.LIGHTS);
            action(lock, VehicleAction.LOCK);
            action(siren, VehicleAction.SIREN);
            action(radio, VehicleAction.RADIO);
            action(radioNext, VehicleAction.RADIO_NEXT);
            action(radioPrevious, VehicleAction.RADIO_PREVIOUS);
            action(window, VehicleAction.WINDOW);
            action(megaphone, VehicleAction.MEGAPHONE);
            action(wipers, VehicleAction.WIPERS);
            if (pressed(interact)) interactSeated(mc);
        } else {
            if (pressed(interact)) targetAction(mc, VehicleAction.INTERACT);
            if (pressed(enter)) targetAction(mc, VehicleAction.ENTER);
            if (pressed(lock)) lockFromOutside(mc);
        }
    }

    private static void lockFromOutside(Minecraft mc) {
        EntityVehicleF_Physics aimed = InteractionOverlay.target;
        if (aimed == null && mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof BuilderVehicle vehicle) aimed = vehicle.vehicle();
        if (aimed != null) {
            send(aimed, VehicleAction.LOCK);
            return;
        }
        LocalPlayer player = mc.player;
        Set<UUID> keys = keys(player);
        BuilderVehicle nearest = null;
        for (BuilderVehicle vehicle : mc.level.getEntitiesOfClass(BuilderVehicle.class, player.getBoundingBox().inflate(ItemKey.REACH))) {
            if (!keys.contains(vehicle.getUUID()) || vehicle.distanceToSqr(player) > ItemKey.REACH * ItemKey.REACH) continue;
            if (nearest == null || vehicle.distanceToSqr(player) < nearest.distanceToSqr(player)) nearest = vehicle;
        }
        if (nearest != null) send(nearest.vehicle(), VehicleAction.LOCK);
        else player.displayClientMessage(Component.translatable(keys.isEmpty() ? "message.slimvehicle.no_key" : "message.slimvehicle.key_out_of_range"), true);
    }

    private static Set<UUID> keys(LocalPlayer player) {
        Set<UUID> keys = new HashSet<>();
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            UUID id = ItemKey.vehicleOf(new WrapperItemStack(inventory.getItem(i)));
            if (id != null) keys.add(id);
        }
        return keys;
    }

    public static boolean holdsKey(EntityVehicleF_Physics vehicle) {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && keys(player).contains(vehicle.uuid());
    }

    public static boolean hornHeld() {
        return horn != null && Minecraft.getInstance().screen == null && isDown(horn) && PaintShopClient.parkedVehicle(Minecraft.getInstance()) == null;
    }

    public static String useKeyName() {
        return label(Minecraft.getInstance().options.keyUse);
    }

    public static String forwardName() {
        return label(Minecraft.getInstance().options.keyUp);
    }

    public static String backName() {
        return label(Minecraft.getInstance().options.keyDown);
    }

    public static String liftName() {
        return label(Minecraft.getInstance().options.keyJump);
    }

    public static String leftName() {
        return label(Minecraft.getInstance().options.keyLeft);
    }

    public static String rightName() {
        return label(Minecraft.getInstance().options.keyRight);
    }

    public static String sinkName() {
        return label(Minecraft.getInstance().options.keySprint);
    }

    public static void interactSeated(Minecraft mc) {
        EntityVehicleF_Physics vehicle = mc.player == null ? null : BuilderVehicle.ridden(mc.player);
        if (mc.screen == null && interactionCooldown == 0 && vehicle != null) {
            send(vehicle, VehicleAction.INTERACT);
            interactionCooldown = 6;
        }
    }

    private static void targetAction(Minecraft mc, VehicleAction action) {
        if (InteractionOverlay.target != null) send(InteractionOverlay.target, action);
        else if (mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof BuilderVehicle vehicle) send(vehicle.vehicle(), action);
    }

    private static void action(KeyMapping key, VehicleAction action) {
        if (!pressed(key)) return;
        Minecraft mc = Minecraft.getInstance();
        EntityVehicleF_Physics vehicle = mc.player == null ? null : BuilderVehicle.ridden(mc.player);
        if (vehicle != null) send(vehicle, action);
    }

    private static boolean isDown(KeyMapping key) {
        InputConstants.Key bound = KeyBindingHelper.getBoundKeyOf(key);
        Minecraft mc = Minecraft.getInstance();
        return key.isDown() || (bound.getType() == InputConstants.Type.KEYSYM && bound.getValue() >= 0
                && InputConstants.isKeyDown(mc.getWindow().getWindow(), bound.getValue()));
    }

    private static boolean pressed(KeyMapping key) {
        boolean down = isDown(key);
        boolean was = HELD.getOrDefault(key, false);
        boolean click = false;
        while (key.consumeClick()) click = true;
        HELD.put(key, down);
        return Minecraft.getInstance().screen == null && (click || (down && !was));
    }

    private static void send(EntityVehicleF_Physics vehicle, VehicleAction action) {
        ClientPlayNetworking.send(new PacketVehicleAction(vehicle.id(), action));
    }
}
