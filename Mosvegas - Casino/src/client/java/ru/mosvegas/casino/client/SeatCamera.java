package ru.mosvegas.casino.client;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import ru.mosvegas.casino.seat.SeatEntity;

/**
 * A seated player's eyes are barely above the felt, so the padded rim hides the near betting spots. While sitting
 * at a table the first-person camera is lifted and moved a little back from the table (the body stays on the stool),
 * like the dealer camera of a live game. Only the view changes; clicks on the table use the same camera.
 */
public final class SeatCamera {
    /** Blocks up / back from the normal eye position. */
    public static final double LIFT = 0.42, BACK = 0.22;

    private SeatCamera() {}

    public static Vec3d offset(Entity focused, boolean thirdPerson) {
        if (thirdPerson || !(focused instanceof PlayerEntity player) || !(player.getVehicle() instanceof SeatEntity seat)) return null;
        float yaw = seat.getYaw() * MathHelper.RADIANS_PER_DEGREE;
        // the seat faces the player's bet spot: step back against that direction
        return new Vec3d(MathHelper.sin(yaw) * BACK, LIFT, -MathHelper.cos(yaw) * BACK);
    }
}
