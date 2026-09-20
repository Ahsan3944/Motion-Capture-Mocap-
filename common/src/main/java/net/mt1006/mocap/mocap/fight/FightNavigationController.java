package net.mt1006.mocap.mocap.fight;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public final class FightNavigationController
{
	private static final int NAVIGATION_TRIGGER_BLOCKED_TICKS = 8;
	private static final int SEARCH_RADIUS = 8;
	private static final int MAX_SEARCH_NODES = 96;
	private static final int MAX_PATH_NODES = 12;
	private static final int NAVIGATION_TIMEOUT_TICKS = 40;
	private static final int REPATH_INTERVAL_TICKS = 8;
	private static final double TARGET_REPATH_DISTANCE_SQR = 4.0;
	private static final double WAYPOINT_REACHED_DISTANCE_SQR = 0.16;
	private static final double SUPPORT_DEPTH = 0.15;
	private static final double SUPPORT_HALF_WIDTH = 0.20;

	private FightNavigationController() {}

	public static boolean navigate(FightParticipant participant, Entity target, double movementSpeed, double attackRange)
	{
		Entity actor = participant.getEntity();
		if (!actor.isAlive() || !target.isAlive() || actor.level() != target.level())
		{
			participant.clearNavigationPath();
			return false;
		}
		if (!(actor.level() instanceof ServerLevel level)) { return false; }

		Vec3 targetPosition = new Vec3(target.getX(), actor.getY(), target.getZ());
		Vec3 waypoint = participant.getNavigationWaypoint();
		Vec3 plannedTarget = participant.getNavigationTargetPosition();
		boolean targetMoved = plannedTarget == null || plannedTarget.distanceToSqr(targetPosition) > TARGET_REPATH_DISTANCE_SQR;
		boolean expired = participant.getNavigationAgeTicks() >= NAVIGATION_TIMEOUT_TICKS;
		boolean needsRepath = waypoint == null || targetMoved || expired || participant.getNavigationAgeTicks() % REPATH_INTERVAL_TICKS == 0;

		if (waypoint != null && waypoint.distanceToSqr(actor.position()) <= WAYPOINT_REACHED_DISTANCE_SQR)
		{
			participant.advanceNavigationWaypoint();
			waypoint = participant.getNavigationWaypoint();
			needsRepath = waypoint == null;
		}

		if (!needsRepath && waypoint != null)
		{
			participant.tickNavigationAge();
			return FightMovementController.moveToward(participant, waypoint, movementSpeed, 0.05);
		}

		if (participant.getMovementBlockedTicks() < NAVIGATION_TRIGGER_BLOCKED_TICKS)
		{
			return false;
		}

		List<Vec3> path = findPath(level, actor, targetPosition, attackRange);
		if (path.isEmpty())
		{
			participant.clearNavigationPath();
			return false;
		}

		participant.setNavigationPath(path, targetPosition);
		waypoint = participant.getNavigationWaypoint();
		if (waypoint == null) { return false; }

		participant.tickNavigationAge();
		return FightMovementController.moveToward(participant, waypoint, movementSpeed, 0.05);
	}

	private static List<Vec3> findPath(ServerLevel level, Entity actor, Vec3 targetPosition, double attackRange)
	{
		GridPos start = new GridPos(BlockPos.containing(actor.getX(), actor.getY(), actor.getZ()).getX(),
				BlockPos.containing(actor.getX(), actor.getY(), actor.getZ()).getZ());
		if (!isWalkable(level, actor, start)) { return Collections.emptyList(); }

		PriorityQueue<SearchNode> open = new PriorityQueue<>();
		Map<GridPos, Double> bestCost = new HashMap<>();
		Map<GridPos, GridPos> cameFrom = new HashMap<>();

		double startDistance = distanceToTarget(start, targetPosition);
		GridPos best = start;
		double bestDistance = startDistance;
		open.add(new SearchNode(start, 0.0, heuristic(start, targetPosition)));
		bestCost.put(start, 0.0);

		int expanded = 0;
		while (!open.isEmpty() && expanded < MAX_SEARCH_NODES)
		{
			SearchNode current = open.poll();
			double knownCost = bestCost.getOrDefault(current.position(), Double.POSITIVE_INFINITY);
			if (current.cost() > knownCost + 1.0E-9) { continue; }
			expanded++;

			double currentDistance = distanceToTarget(current.position(), targetPosition);
			if (currentDistance < bestDistance)
			{
				best = current.position();
				bestDistance = currentDistance;
			}
			if (currentDistance <= Math.max(attackRange, 1.0))
			{
				best = current.position();
				break;
			}

			for (int[] direction : DIRECTIONS)
			{
				GridPos next = new GridPos(current.position().x() + direction[0], current.position().z() + direction[1]);
				if (Math.abs(next.x() - start.x()) > SEARCH_RADIUS || Math.abs(next.z() - start.z()) > SEARCH_RADIUS) { continue; }
				if (!isWalkable(level, actor, next)) { continue; }

				double nextCost = current.cost() + 1.0;
				if (nextCost >= bestCost.getOrDefault(next, Double.POSITIVE_INFINITY)) { continue; }

				bestCost.put(next, nextCost);
				cameFrom.put(next, current.position());
				open.add(new SearchNode(next, nextCost, nextCost + heuristic(next, targetPosition)));
			}
		}

		if (best.equals(start)) { return Collections.emptyList(); }
		return toWaypoints(actor.getY(), start, best, cameFrom);
	}

	private static List<Vec3> toWaypoints(double y, GridPos start, GridPos goal, Map<GridPos, GridPos> cameFrom)
	{
		List<GridPos> cells = new ArrayList<>();
		GridPos current = goal;
		while (!current.equals(start) && cells.size() < MAX_PATH_NODES)
		{
			cells.add(current);
			current = cameFrom.get(current);
			if (current == null) { return Collections.emptyList(); }
		}
		Collections.reverse(cells);

		List<Vec3> waypoints = new ArrayList<>(cells.size());
		for (GridPos cell : cells)
		{
			waypoints.add(new Vec3(cell.x() + 0.5, y, cell.z() + 0.5));
		}
		return waypoints;
	}

	private static boolean isWalkable(ServerLevel level, Entity actor, GridPos pos)
	{
		if (!isLoaded(level, pos.x(), pos.z())) { return false; }

		double x = pos.x() + 0.5;
		double z = pos.z() + 0.5;
		AABB actorBox = actor.getBoundingBox();
		AABB movedBox = actorBox.move(x - actor.getX(), 0.0, z - actor.getZ());
		if (!isLoaded(level, movedBox)) { return false; }
		if (!level.noCollision(actor, movedBox)) { return false; }

		AABB supportBox = new AABB(
				x - SUPPORT_HALF_WIDTH, actor.getY() - SUPPORT_DEPTH, z - SUPPORT_HALF_WIDTH,
				x + SUPPORT_HALF_WIDTH, actor.getY(), z + SUPPORT_HALF_WIDTH);
		if (!isLoaded(level, supportBox)) { return false; }
		return level.getBlockCollisions(null, supportBox).iterator().hasNext();
	}

	private static boolean isLoaded(ServerLevel level, int x, int z)
	{
		return level.getChunkSource().getChunkNow(x >> 4, z >> 4) != null;
	}

	private static boolean isLoaded(ServerLevel level, AABB box)
	{
		int minChunkX = ((int)Math.floor(box.minX)) >> 4;
		int maxChunkX = ((int)Math.floor(box.maxX)) >> 4;
		int minChunkZ = ((int)Math.floor(box.minZ)) >> 4;
		int maxChunkZ = ((int)Math.floor(box.maxZ)) >> 4;
		for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++)
		{
			for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++)
			{
				if (level.getChunkSource().getChunkNow(chunkX, chunkZ) == null) { return false; }
			}
		}
		return true;
	}

	private static double heuristic(GridPos position, Vec3 target)
	{
		return distanceToTarget(position, target);
	}

	private static double distanceToTarget(GridPos position, Vec3 target)
	{
		double dx = position.x() + 0.5 - target.x;
		double dz = position.z() + 0.5 - target.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	private record GridPos(int x, int z) {}

	private record SearchNode(GridPos position, double cost, double priority) implements Comparable<SearchNode>
	{
		@Override public int compareTo(SearchNode other)
		{
			return Double.compare(priority, other.priority);
		}
	}

	private static final int[][] DIRECTIONS = {
			{1, 0},
			{-1, 0},
			{0, 1},
			{0, -1}
	};
}
