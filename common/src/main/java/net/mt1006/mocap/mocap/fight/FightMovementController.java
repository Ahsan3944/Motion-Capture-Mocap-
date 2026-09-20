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
	private static final int RECOVERY_AFTER_BLOCKED_TICKS = 4;
	private static final double MIN_SUCCESSFUL_MOVE = 0.01;
	private static final double SUCCESS_RATIO = 0.25;

	private FightMovementController() {}

	public static boolean chase(FightParticipant participant, Entity target, double movementSpeed, double stopDistance)
	{
		Entity actor = participant.getEntity();
		if (!actor.isAlive() || !target.isAlive() || actor.level() != target.level()) { return false; }

		double dx = target.getX() - actor.getX();
		double dy = target.getY() - actor.getY();
		double dz = target.getZ() - actor.getZ();
		double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
		double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (!Double.isFinite(horizontalDistance) || !Double.isFinite(distance)) { return false; }
		if (distance <= stopDistance) { participant.resetMovementBlockedTicks(); return true; }
		if (horizontalDistance <= MIN_SUCCESSFUL_MOVE)
		{
			return false;
		}

		faceTarget(actor, dx, dz);

		double speedPerTick = Math.max(0.0, Math.min(movementSpeed / TICKS_PER_SECOND, MAX_BLOCKS_PER_TICK));
		if (speedPerTick == 0.0) { participant.resetMovementBlockedTicks(); return false; }

		double distanceToMove = Math.min(speedPerTick, horizontalDistance - Math.min(stopDistance, horizontalDistance));
		if (distanceToMove <= 0.0) { return false; }

		if (tryMove(actor, new Vec3(dx / horizontalDistance * distanceToMove, 0.0,
				dz / horizontalDistance * distanceToMove), distanceToMove))
		{
			participant.resetMovementBlockedTicks();
			return true;
		}

		int blockedTicks = participant.incrementMovementBlockedTicks();
		if (blockedTicks < RECOVERY_AFTER_BLOCKED_TICKS) { return false; }

		double sideX = -dz / horizontalDistance * participant.getMovementRecoveryDirection();
		double sideZ = dx / horizontalDistance * participant.getMovementRecoveryDirection();
		boolean recovered = tryMove(actor, new Vec3(sideX * distanceToMove, 0.0, sideZ * distanceToMove), distanceToMove);
		participant.flipMovementRecoveryDirection();
		if (recovered) { participant.resetMovementBlockedTicks(); }
		return recovered;
	}

	private static boolean tryMove(Entity actor, Vec3 movement, double requestedDistance)
	{
		Vec3 before = actor.position();
		actor.move(MoverType.SELF, movement);
		Vec3 after = actor.position();
		double movedX = after.x - before.x;
		double movedZ = after.z - before.z;
		double movedDistance = Math.sqrt(movedX * movedX + movedZ * movedZ);
		return Double.isFinite(movedDistance)
				&& movedDistance >= Math.max(MIN_SUCCESSFUL_MOVE, requestedDistance * SUCCESS_RATIO);
	}

	public static void faceTarget(Entity actor, double dx, double dz)
	{
		if (dx == 0.0 && dz == 0.0) { return; }
		float yaw = (float)(Math.atan2(-dx, dz) * 180.0 / Math.PI);
		actor.setYRot(yaw);
		actor.setYHeadRot(yaw);
	}
}
