package studio.merami.sliminterfacefabric.gametest;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import studio.merami.slimcore.baseclasses.Point3D;
import studio.merami.slimcore.entities.instances.EntityVehicleF_Physics;
import studio.merami.slimcore.entities.instances.VehicleAction;
import studio.merami.slimcore.items.ItemVehicle;
import studio.merami.slimcore.packloading.PackParser;
import studio.merami.slimcore.physics.CraftPhysics;
import studio.merami.slimcore.systems.VehicleSpawner;

import studio.merami.sliminterfacefabric.wrapper.WrapperDamage;
import studio.merami.sliminterfacefabric.wrapper.WrapperItemStack;

import java.util.UUID;

import static studio.merami.sliminterfacefabric.gametest.GameTestSupport.*;

public class CraftGameTests implements FabricGameTest {

    private static Player pilot(EntityVehicleF_Physics craft, GameTestHelper h) {
        Player player = h.makeMockPlayer();
        place(player, craft.position());
        own(craft, player);
        ride(player, craft);
        craft.action(w(player), VehicleAction.ENGINE);
        return player;
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "craft_heli", timeoutTicks = 200)
    public void helicopterSpoolsClimbsHoversAndFlies(GameTestHelper h) {
        floor(h, 1, 12, 1, 12);
        EntityVehicleF_Physics heli = car(h, 6, 6, 0, "slimvehicle:r44");
        spawn(h, heli);
        Player player = pilot(heli, h);
        h.assertTrue(heli.flag(EntityVehicleF_Physics.ENGINE), "Engine must start");
        double ground = heli.y();
        for (int tick = 0; tick < 60; tick++) {
            heli.acceptInput(w(player), 0, 0, 0, false, 1);
            tick(heli);
        }
        h.assertTrue(heli.y() < ground + .01, "Must not lift before the rotor is spun up, rose " + (heli.y() - ground));
        for (int tick = 0; tick < 200 && heli.rotor() < CraftPhysics.HOVER_ROTOR; tick++) {
            heli.acceptInput(w(player), 0, 0, 0, false, 1);
            tick(heli);
        }
        for (int tick = 0; tick < 30; tick++) {
            heli.acceptInput(w(player), 0, 0, 0, false, 1);
            tick(heli);
        }
        double climbed = heli.y() - ground;
        h.assertTrue(climbed > 4, "Collective up must climb, climbed " + climbed);
        for (int tick = 0; tick < 20; tick++) {
            heli.acceptInput(w(player), 0, 0, 0, false, 0);
            tick(heli);
        }
        double hover = heli.y();
        for (int tick = 0; tick < 20; tick++) {
            heli.acceptInput(w(player), 0, 0, 0, false, 0);
            tick(heli);
        }
        h.assertTrue(Math.abs(heli.y() - hover) < .05, "Must hover without collective, drifted " + (heli.y() - hover));
        double startZ = heli.z();
        float yaw = heli.yaw();
        for (int tick = 0; tick < 60; tick++) {
            heli.acceptInput(w(player), 1, 0, 1, false, 0);
            tick(heli);
        }
        h.assertTrue(Math.abs(heli.z() - startZ) > 2 || Math.abs(heli.x() - h.absoluteVec(new Vec3(6, 0, 6)).x) > 2, "Must fly forward");
        h.assertTrue(Math.abs(heli.yaw() - yaw) > 30, "Must yaw with the pedals");
        heli.remove();
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "craft_jetski", timeoutTicks = 200)
    public void jetskiFloatsAndRidesOnWater(GameTestHelper h) {
        for (int x = 1; x < 11; x++) {
            for (int z = 1; z < 23; z++) {
                h.setBlock(x, 0, z, Blocks.STONE);
                h.setBlock(x, 1, z, Blocks.WATER);
                h.setBlock(x, 2, z, Blocks.WATER);
                h.setBlock(x, 3, z, Blocks.AIR);
            }
        }
        EntityVehicleF_Physics ski = car(h, 6, 4, 0, "slimvehicle:jetski");
        spawn(h, ski);
        double surface = h.absoluteVec(new Vec3(0, 2 + 8 / 9.0, 0)).y;
        Player player = pilot(ski, h);
        for (int tick = 0; tick < 60; tick++) tick(ski);
        double floatLine = surface - ski.definition().craft.draft;
        h.assertTrue(Math.abs(ski.y() - floatLine) < .15, "Must float at its draft, y=" + ski.y() + " expected " + floatLine);
        h.assertTrue(ski.flag(EntityVehicleF_Physics.ENGINE), "Water must not drown the jet ski engine");
        double startZ = ski.z();
        for (int tick = 0; tick < 30; tick++) {
            ski.acceptInput(w(player), 1, 0, 0, false);
            tick(ski);
        }
        h.assertTrue(ski.z() - startZ > 4, "Must ride forward on water, moved " + (ski.z() - startZ));
        h.assertTrue(Math.abs(ski.y() - floatLine) < .3, "Must stay on the surface while riding");
        ski.remove();
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "craft_jetski_place", timeoutTicks = 100)
    public void jetskiIsPlacedOnTheWaterSurface(GameTestHelper h) {
        for (int x = 1; x < 11; x++) {
            for (int z = 1; z < 11; z++) {
                h.setBlock(x, 0, z, Blocks.STONE);
                for (int y = 1; y < 4; y++) h.setBlock(x, y, z, Blocks.WATER);
            }
        }
        Player player = h.makeMockPlayer();
        Vec3 shore = h.absoluteVec(new Vec3(6.5, 4, 1.5));
        place(player, new Point3D(shore.x, shore.y, shore.z));
        Vec3 bottom = h.absoluteVec(new Vec3(6.5, 1, 6.5));
        ItemVehicle item = new ItemVehicle(PackParser.vehicle("slimvehicle:jetski"));
        h.assertTrue(item.useOnBlock(w(player), new WrapperItemStack(ItemStack.EMPTY), new Point3D(bottom.x, bottom.y, bottom.z)), "The jet ski must be placed on water");
        EntityVehicleF_Physics ski = VehicleSpawner.nearby(w(player));
        h.assertTrue(ski != null, "The placed jet ski must be next to the player");
        double floatLine = h.absoluteVec(new Vec3(0, 3 + 8 / 9.0, 0)).y - ski.definition().craft.draft;
        h.assertTrue(Math.abs(ski.y() - floatLine) < .05, "Must be placed afloat, y=" + ski.y() + " expected " + floatLine);
        ski.remove();
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "craft_jetski_shove", timeoutTicks = 100)
    public void beachedJetskiIsShovedWithSneakAttack(GameTestHelper h) {
        floor(h, 1, 12, 1, 16);
        EntityVehicleF_Physics ski = car(h, 6, 6, 0, "slimvehicle:jetski");
        spawn(h, ski);
        for (int tick = 0; tick < 20; tick++) tick(ski);
        ServerPlayer player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), new GameProfile(UUID.randomUUID(), "pusher"), ClientInformation.createDefault());
        place(player, ski.position().add(0, 0, -2));
        double startZ = ski.z();
        h.assertFalse(ski.attack(new WrapperDamage(player.damageSources().playerAttack(player)), 1), "A plain hit must not shove");
        player.setShiftKeyDown(true);
        h.assertTrue(ski.attack(new WrapperDamage(player.damageSources().playerAttack(player)), 1), "A sneaking hit must shove the jet ski");
        for (int tick = 0; tick < 20; tick++) tick(ski);
        h.assertTrue(ski.z() - startZ > 1, "Must slide away from the player, moved " + (ski.z() - startZ));
        ski.remove();
        h.succeed();
    }
}
