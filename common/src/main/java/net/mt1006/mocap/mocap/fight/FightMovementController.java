package net.mt1006.mocap.mocap.fight;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

/**
 * Small deterministic movement layer for Fight-controlled actors.
 *
 * Phase 4A intentionally handles direct horizontal chase only. Collision is
 * delegated to the normal entity movement implementation; this controller
 * never teleports through terrain or loads distant chunks.
 */
public final class FightMovementController
{
	private static final double TICKS_PER_SECOND = 20.0;
	private static final double MAX_BLOCKS_PER_TICK = 0.5;

	private FightMovementController() {}

	public static boolean chase(Entity actor, Entity target, double movementSpeed, double stopDistance)
	{
		if (!actor.isAlive() || !target.isAlive() || actor.level() != target.level()) { return false; }

		double dx = target.getX() - actor.getX();
		double dz = target.getZ() - actor.getZ();
		double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
		if (!Double.isFinite(horizontalDistance) || horizontalDistance <= stopDistance) { return true; }

		faceTarget(actor, dx, dz);

		double speedPerTick = Math.max(0.0, Math.min(movementSpeed / TICKS_PER_SECOND, MAX_BLOCKS_PER_TICK));
		if (speedPerTick == 0.0) { return false; }

		double distanceToMove = Math.min(speedPerTick, horizontalDistance - stopDistance);
		if (distanceToMove <= 0.0) { return true; }

		Vec3 movement = new Vec3(dx / horizontalDistance * distanceToMove, 0.0,
				dz / horizontalDistance * distanceToMove);
		actor.move(MoverType.SELF, movement);
		return true;
	}

	public static void faceTarget(Entity actor, double dx, double dz)
	{
		if (dx == 0.0 && dz == 0.0) { return; }
		float yaw = (float)(Math.atan2(-dx, dz) * 180.0 / Math.PI);
		actor.setYRot(yaw);
		actor.setYHeadRot(yaw);
	}
}
