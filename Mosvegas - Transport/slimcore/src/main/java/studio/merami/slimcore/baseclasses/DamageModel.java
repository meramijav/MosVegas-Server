package studio.merami.slimcore.baseclasses;

import studio.merami.slimcore.sliminterface.IWrapperNBT;
import studio.merami.slimcore.sliminterface.InterfaceManager;

import java.util.ArrayList;
import java.util.List;

public final class DamageModel {
    public static final float FULL = 100;
    public static final double IMPACT_THRESHOLD = .12;
    public static final float LAMP = 3;
    public static final float TYRE = 4;
    public static final float WHEEL = 15;
    public static final float ENGINE_FIRE = 10;
    public static final double PULL = .035;
    public static final int MAX_DENTS = 16;
    public static final float MAX_DENT = .32f;

    public enum Side {
        FRONT,
        REAR,
        LEFT,
        RIGHT,
        TOP
    }

    public record Dent(Point3D pos, Point3D dir, float depth, float radius) {}

    private DamageModel() {}

    public static float impact(double deltaV) {
        double excess = deltaV - IMPACT_THRESHOLD;
        return excess <= 0 ? 0 : (float) (80 * excess * excess);
    }

    public static float landing(double speed) {
        double excess = speed - .55;
        return excess <= 0 ? 0 : (float) (60 * excess * excess);
    }

    public static float occupant(double deltaV) {
        return deltaV < .5 ? 0 : (float) ((deltaV - .5) * 18);
    }

    public static float runOver(double closing) {
        return closing < .2 ? 0 : (float) ((closing - .15) * 25);
    }

    public static float scrape(double slide) {
        return slide < .12 ? 0 : (float) (slide * .2);
    }

    public static double power(float engine) {
        return engine >= 60 ? 1 : .35 + .65 * Math.max(0, engine) / 60;
    }

    public static float fire(float engine, float body) {
        if (body <= 0) return 1;
        if (engine <= 0) return .55f;
        return engine < ENGINE_FIRE ? .2f + .15f * (1 - engine / ENGINE_FIRE) : 0;
    }

    public static float dentDepth(float amount) {
        return amount < 1.5f ? 0 : Math.min(MAX_DENT, .03f + .011f * amount);
    }

    public static float dentRadius(float amount) {
        return Math.min(1.15f, .42f + .022f * amount);
    }

    public static boolean dent(List<Dent> dents, Point3D pos, Point3D dir, float amount) {
        float depth = dentDepth(amount);
        float radius = dentRadius(amount);
        if (depth <= 0 || dir.lengthSqr() < 1e-8) return false;
        dir = dir.normalize();
        Dent near = null;
        double best = Double.MAX_VALUE;
        for (Dent dent : dents) {
            double gap = dent.pos().distanceTo(pos);
            if (gap < .5 * (dent.radius() + radius) && gap < best) {
                best = gap;
                near = dent;
            }
        }
        if (near != null) {
            dents.remove(near);
            float total = near.depth() + depth;
            Point3D at = near.pos().scale(near.depth() / total).add(pos.scale(depth / total));
            Point3D push = near.dir().scale(near.depth()).add(dir.scale(depth));
            dents.add(new Dent(at, push.lengthSqr() > 1e-8 ? push.normalize() : dir,
                    Math.min(MAX_DENT, (float) Math.sqrt(near.depth() * near.depth() + depth * depth)),
                    Math.min(1.3f, Math.max(near.radius(), radius) + .05f)));
        } else {
            if (dents.size() >= MAX_DENTS) {
                Dent shallow = dents.get(0);
                for (Dent dent : dents) if (dent.depth() < shallow.depth()) shallow = dent;
                dents.remove(shallow);
            }
            dents.add(new Dent(pos, dir, depth, radius));
        }
        return true;
    }

    public static float falloff(Dent dent, Point3D point) {
        double r = point.distanceTo(dent.pos()) / dent.radius();
        if (r >= 1) return 0;
        double k = 1 - r * r;
        return (float) (k * k);
    }

    public static Point3D displacement(List<Dent> dents, Point3D point) {
        double x = 0;
        double y = 0;
        double z = 0;
        for (Dent dent : dents) {
            float weight = falloff(dent, point);
            if (weight <= 0) continue;
            double depth = dent.depth() * weight * (1 + .35 * noise(point.x, point.y, point.z, 0));
            Point3D fold = new Point3D(noise(point.x, point.y, point.z, 1), noise(point.x, point.y, point.z, 2), noise(point.x, point.y, point.z, 3));
            fold = fold.subtract(dent.dir().scale(fold.dot(dent.dir()))).scale(.28 * dent.depth() * weight);
            x += dent.dir().x * depth + fold.x;
            y += dent.dir().y * depth + fold.y;
            z += dent.dir().z * depth + fold.z;
        }
        double length = Math.sqrt(x * x + y * y + z * z);
        double cap = MAX_DENT * 1.35;
        return length > cap ? new Point3D(x, y, z).scale(cap / length) : new Point3D(x, y, z);
    }

    private static float noise(double x, double y, double z, int seed) {
        x = x / .23 + seed * 17.3;
        y = y / .23 - seed * 9.1;
        z = z / .23 + seed * 5.7;
        int ix = SlimMath.floor(x);
        int iy = SlimMath.floor(y);
        int iz = SlimMath.floor(z);
        float fx = smooth((float) (x - ix));
        float fy = smooth((float) (y - iy));
        float fz = smooth((float) (z - iz));
        float x00 = SlimMath.lerp(fx, hash(ix, iy, iz), hash(ix + 1, iy, iz));
        float x10 = SlimMath.lerp(fx, hash(ix, iy + 1, iz), hash(ix + 1, iy + 1, iz));
        float x01 = SlimMath.lerp(fx, hash(ix, iy, iz + 1), hash(ix + 1, iy, iz + 1));
        float x11 = SlimMath.lerp(fx, hash(ix, iy + 1, iz + 1), hash(ix + 1, iy + 1, iz + 1));
        return SlimMath.lerp(fz, SlimMath.lerp(fy, x00, x10), SlimMath.lerp(fy, x01, x11));
    }

    private static float smooth(float t) {
        return t * t * (3 - 2 * t);
    }

    private static float hash(int x, int y, int z) {
        int h = x * 374761393 + y * 668265263 + z * 1274126177;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xffff) / 32767.5f - 1;
    }

    public static void save(IWrapperNBT tag, List<Dent> dents) {
        List<IWrapperNBT> list = new ArrayList<>();
        for (Dent dent : dents) {
            IWrapperNBT entry = InterfaceManager.coreInterface.createNBT();
            entry.setFloat("X", (float) dent.pos().x);
            entry.setFloat("Y", (float) dent.pos().y);
            entry.setFloat("Z", (float) dent.pos().z);
            entry.setFloat("DX", (float) dent.dir().x);
            entry.setFloat("DY", (float) dent.dir().y);
            entry.setFloat("DZ", (float) dent.dir().z);
            entry.setFloat("Depth", dent.depth());
            entry.setFloat("Radius", dent.radius());
            list.add(entry);
        }
        tag.setCompounds("Dents", list);
    }

    public static List<Dent> load(List<IWrapperNBT> list) {
        List<Dent> dents = new ArrayList<>();
        for (int i = 0; i < list.size() && dents.size() < MAX_DENTS; i++) {
            IWrapperNBT tag = list.get(i);
            Point3D dir = new Point3D(tag.getFloat("DX"), tag.getFloat("DY"), tag.getFloat("DZ"));
            float depth = SlimMath.clamp(tag.getFloat("Depth"), 0, MAX_DENT);
            float radius = SlimMath.clamp(tag.getFloat("Radius"), .1f, 1.3f);
            if (depth > 0 && dir.lengthSqr() > 1e-8) {
                dents.add(new Dent(new Point3D(tag.getFloat("X"), tag.getFloat("Y"), tag.getFloat("Z")), dir.normalize(), depth, radius));
            }
        }
        return dents;
    }

    public static List<Dent> load(IWrapperNBT tag) {
        return tag.hasList("Dents") ? load(tag.getCompounds("Dents")) : new ArrayList<>();
    }
}
