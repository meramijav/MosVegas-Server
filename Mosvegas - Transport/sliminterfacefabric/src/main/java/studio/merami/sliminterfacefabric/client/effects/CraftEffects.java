package studio.merami.sliminterfacefabric.client.effects;

import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import studio.merami.slimcore.baseclasses.Point3D;
import studio.merami.slimcore.entities.instances.EntityVehicleF_Physics;
import studio.merami.slimcore.jsondefs.JSONVehicle;
import studio.merami.sliminterfacefabric.entity.BuilderVehicle;

public final class CraftEffects {
    private static final double RANGE = 96;
    private static final double WAKE_SPEED = .08;
    private static final double SPRAY_SPEED = .55;
    private static final double DOWNWASH_HEIGHT = 12;
    private static final double DOWNWASH_ROTOR = .5;
    private static final RandomSource RANDOM = RandomSource.create();

    private CraftEffects() {}

    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        ParticleStatus status = mc.options.particles().get();
        if (status == ParticleStatus.MINIMAL) return;
        double density = status == ParticleStatus.DECREASED ? .4 : 1;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof BuilderVehicle builder) || builder.distanceToSqr(mc.player) > RANGE * RANGE) continue;
            EntityVehicleF_Physics vehicle = builder.vehicle();
            if (!vehicle.craft()) continue;
            if (vehicle.definition().craft.helicopter()) downwash(mc, vehicle, density);
            else wake(mc, vehicle, density);
        }
    }

    private static void wake(Minecraft mc, EntityVehicleF_Physics vehicle, double density) {
        double depth = vehicle.waterDepth();
        double speed = Math.abs(vehicle.speed());
        if (Double.isNaN(depth) || speed < WAKE_SPEED) return;
        JSONVehicle definition = vehicle.definition();
        double surface = vehicle.position().y + depth + .05;
        Point3D origin = vehicle.chassisToWorld(Point3D.ZERO);
        Point3D ahead = vehicle.chassisToWorld(new Point3D(0, 0, 1)).subtract(origin);
        Point3D side = vehicle.chassisToWorld(new Point3D(1, 0, 0)).subtract(origin);
        double direction = Math.signum(vehicle.speed());
        double share = Math.min(1, speed / definition.craft.maxSpeed());
        Point3D stern = vehicle.chassisToWorld(new Point3D(0, 0, -definition.size.halfLength * direction));
        int count = count((1 + share * 5) * density);
        for (int i = 0; i < count; i++) {
            double across = (RANDOM.nextDouble() - .5) * definition.size.halfWidth * 1.6;
            double back = -.05 - RANDOM.nextDouble() * .1;
            mc.level.addParticle(ParticleTypes.SPLASH, stern.x + side.x * across, surface, stern.z + side.z * across,
                    ahead.x * back * direction, .1 + share * .25, ahead.z * back * direction);
        }
        if (share > .3 && direction > 0) {
            int tail = count(share * 4 * density);
            for (int i = 0; i < tail; i++) {
                double lift = .25 + share * .35 + RANDOM.nextDouble() * .1;
                double back = -.25 - RANDOM.nextDouble() * .2;
                mc.level.addParticle(ParticleTypes.SPLASH, stern.x, surface + .2, stern.z, ahead.x * back, lift, ahead.z * back);
            }
        }
        if (speed > SPRAY_SPEED) {
            Point3D bow = vehicle.chassisToWorld(new Point3D(0, 0, definition.size.halfLength * .35));
            int spray = count(share * 3 * density);
            for (int i = 0; i < spray; i++) {
                double out = RANDOM.nextBoolean() ? 1 : -1;
                double x = bow.x + side.x * out * definition.size.halfWidth;
                double z = bow.z + side.z * out * definition.size.halfWidth;
                mc.level.addParticle(ParticleTypes.SPLASH, x, surface + .1, z, side.x * out * .25, .2 + share * .2, side.z * out * .25);
            }
            if (RANDOM.nextDouble() < share * .5 * density) {
                mc.level.addParticle(ParticleTypes.CLOUD, stern.x, surface + .3, stern.z, ahead.x * -.05, .02, ahead.z * -.05);
            }
        }
    }

    private static void downwash(Minecraft mc, EntityVehicleF_Physics vehicle, double density) {
        float rotor = vehicle.rotor();
        if (rotor < DOWNWASH_ROTOR) return;
        Point3D at = vehicle.position();
        int bx = Mth.floor(at.x);
        int bz = Mth.floor(at.z);
        int ground = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
        double height = at.y - ground;
        if (height > DOWNWASH_HEIGHT || height < -1) return;
        BlockPos below = new BlockPos(bx, ground - 1, bz);
        boolean water = mc.level.getFluidState(below).is(FluidTags.WATER);
        BlockState block = mc.level.getBlockState(below);
        double strength = rotor * (1 - Math.max(0, height) / DOWNWASH_HEIGHT);
        double radius = 1.5 + Math.max(0, height) * .45;
        int count = count(strength * 8 * density);
        ParticleOptions dust = !water && block.getRenderShape() != RenderShape.INVISIBLE ? new BlockParticleOption(ParticleTypes.BLOCK, block) : ParticleTypes.POOF;
        for (int i = 0; i < count; i++) {
            double angle = RANDOM.nextDouble() * Math.PI * 2;
            double r = radius * (.6 + RANDOM.nextDouble() * .6);
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            double x = at.x + cos * r;
            double z = at.z + sin * r;
            double out = .25 + strength * .45;
            if (water) {
                mc.level.addParticle(ParticleTypes.SPLASH, x, ground + .05, z, cos * out, .15 + strength * .2, sin * out);
                if (RANDOM.nextDouble() < .35) mc.level.addParticle(ParticleTypes.CLOUD, x, ground + .3, z, cos * out * .4, .02, sin * out * .4);
            } else {
                mc.level.addParticle(dust, x, ground + .1, z, cos * out, .05, sin * out);
                if (RANDOM.nextDouble() < .2) mc.level.addParticle(ParticleTypes.POOF, x, ground + .2, z, cos * out * .3, .01, sin * out * .3);
            }
        }
    }

    private static int count(double expected) {
        int whole = (int) expected;
        return whole + (RANDOM.nextDouble() < expected - whole ? 1 : 0);
    }
}
