package ru.mosvegas.core.integration;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import ru.mosvegas.core.MosvegasCore;
import ru.mosvegas.core.zone.ZoneService;
import java.lang.reflect.*;

/** Uses the supplied TaCZ Fabric public event API, without a mandatory gun-mod dependency. */
public final class TaczIntegration {
    private TaczIntegration() {}
    public static void register() {
        if (!FabricLoader.getInstance().isModLoaded("tacz")) return;
        try {
            shooting("GunShootEvent");
            shooting("GunFireEvent"); // Every burst/automatic shot must be checked again.
            Class<?> eventType = Class.forName("com.tacz.guns.api.event.common.EntityHurtByGunEvent");
            Method attacker = eventType.getMethod("getAttacker"), target = eventType.getMethod("getHurtEntity");
            Method cancel = Class.forName("com.tacz.guns.api.event.common.EntityHurtByGunEvent$Pre").getMethod("setCanceled", boolean.class);
            subscribe(eventType, "PRE", "PreCallBack", event -> {
                Entity victim = (Entity) target.invoke(event);
                if (victim != null && !victim.getWorld().isClient && !ZoneService.gunHit((Entity) attacker.invoke(event), victim)) cancel.invoke(event, true);
            });
            MosvegasCore.LOGGER.info("TaCZ: защита GunShootEvent, GunFireEvent и EntityHurtByGunEvent подключена.");
        } catch (ReflectiveOperationException | LinkageError e) {
            // Fail closed: a mismatched gun API must not silently leave the server unprotected.
            throw new IllegalStateException("Несовместимый TaCZ: не удалось подключить серверную защиту оружия", e);
        }
    }
    private static void shooting(String name) throws ReflectiveOperationException {
        Class<?> type = Class.forName("com.tacz.guns.api.event.common." + name);
        Method shooter = type.getMethod("getShooter"), cancel = type.getMethod("setCanceled", boolean.class);
        subscribe(type, "CALLBACK", "Callback", event -> {
            LivingEntity entity = (LivingEntity) shooter.invoke(event);
            if (!entity.getWorld().isClient && !ZoneService.shoot(entity)) cancel.invoke(event, true);
        });
    }
    @SuppressWarnings("unchecked")
    private static void subscribe(Class<?> type, String field, String callbackName, Handler handler) throws ReflectiveOperationException {
        Class<?> callback = Class.forName(type.getName() + "$" + callbackName);
        Event<Object> bus = (Event<Object>) type.getField(field).get(null);
        Object listener = Proxy.newProxyInstance(callback.getClassLoader(), new Class<?>[]{callback}, (proxy, method, args) -> {
            if (method.getName().equals("post")) { handler.handle(args[0]); return null; }
            if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
            if (method.getName().equals("equals")) return proxy == args[0];
            return "Mosvegas zone protection";
        });
        bus.register(listener);
    }
    @FunctionalInterface private interface Handler { void handle(Object event) throws ReflectiveOperationException; }
}
