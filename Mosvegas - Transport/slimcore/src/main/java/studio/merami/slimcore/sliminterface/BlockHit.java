package studio.merami.slimcore.sliminterface;

import studio.merami.slimcore.baseclasses.Point3D;

public record BlockHit(boolean hit, boolean top, Point3D location) {}
