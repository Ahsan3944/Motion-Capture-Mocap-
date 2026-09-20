package net.mt1006.mocap.mocap.fight;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class FightGroupController
{
	private static final double MIN_SPACING = 1.0;
	private static final double MAX_SPACING = 3.0;
	private static final double DEFAULT_SPACING = 1.5;
	private static final int MAX_SUPPORT_Y_OFFSET = 2;

	private FightGroupController() {}

	public static List<FightParticipant> selectBySide(List<FightParticipant> participants, FightParticipant.Side side)
	{
		List<FightParticipant> selected = new ArrayList<>();
		for (FightParticipant participant : participants)
		{
			if (participant.isActive() && participant.getEntity().isAlive() && participant.getSide() == side)
			{
				selected.add(participant);
			}
		}
		return selected;
	}

	public static boolean teleportFormation(List<FightParticipant> participants, Vec3 destination)
	{
		return teleportFormation(participants, destination, DEFAULT_SPACING);
	}

	public static boolean teleportFormation(List<FightParticipant> participants, Vec3 destination, double requestedSpacing)
	{
		if (participants == null || participants.isEmpty() || destination == null) { return false; }

		List<FightParticipant> group = new ArrayList<>();
		Set<Entity> groupEntities = new HashSet<>();
		ServerLevel level = null;
		for (FightParticipant participant : participants)
		{
			if (participant == null || !participant.isActive()) { return false; }
			Entity entity = participant.getEntity();
			if (entity == null || !entity.isAlive() || !(entity.level() instanceof ServerLevel participantLevel)) { return false; }
			if (level == null) { level = participantLevel; }
			if (level != participantLevel) { return false; }
			if (!groupEntities.add(entity)) { return false; }
			group.add(participant);
		}
		if (level == null) { return false; }

		double spacing = Math.max(MIN_SPACING, Math.min(requestedSpacing, MAX_SPACING));
		List<FormationSlot> slots = buildSlots(group, destination, spacing);
		if (slots.size() != group.size() || !validateSlots(level, group, slots, groupEntities))
		{
			return false;
		}

		for (int i = 0; i < group.size(); i++)
		{
			Entity entity = group.get(i).getEntity();
			FormationSlot slot = slots.get(i);
			entity.snapTo(slot.position().x, slot.position().y, slot.position().z);
			entity.setYRot(slot.yaw());
			entity.setYHeadRot(slot.yaw());
			entity.setDeltaMovement(Vec3.ZERO);
			entity.setOnGround(true);
			group.get(i).resetMovementBlockedTicks();
			group.get(i).resetNavigationStallTicks();
			group.get(i).clearNavigationPath();
		}
		return true;
	}

	private static List<FormationSlot> buildSlots(List<FightParticipant> group, Vec3 destination, double spacing)
	{
		int count = group.size();
		int columns = (int)Math.ceil(Math.sqrt(count));
		int rows = (int)Math.ceil((double)count / columns);
		List<FormationSlot> slots = new ArrayList<>(count);
		for (int index = 0; index < count; index++)
		{
			int row = index / columns;
			int column = index % columns;
			double offsetX = (column - (columns - 1) / 2.0) * spacing;
			double offsetZ = (row - (rows - 1) / 2.0) * spacing;
			double x = destination.x + offsetX;
			double z = destination.z + offsetZ;
			Vec3 safe = findSafePosition(group.get(index).getEntity(), x, destination.y, z);
			if (safe == null) { return List.of(); }

			float yaw = (float)(Math.toDegrees(Math.atan2(destination.x - x, destination.z - z)));
			slots.add(new FormationSlot(safe, yaw));
		}
		return slots;
	}

	private static Vec3 findSafePosition(Entity entity, double x, double requestedY, double z)
	{
		if (!(entity.level() instanceof ServerLevel level)) { return null; }
		for (int offset = 0; offset <= MAX_SUPPORT_Y_OFFSET; offset++)
		{
			double y = requestedY + offset;
			AABB movedBox = entity.getBoundingBox().move(x - entity.getX(), y - entity.getY(), z - entity.getZ());
			if (!isLoaded(level, movedBox)) { continue; }
			if (!level.noBlockCollision(movedBox) || !level.noBorderCollision(entity, movedBox)) { continue; }
			AABB supportBox = new AABB(
					x - 0.20, y - 0.15, z - 0.20,
				x + 0.20, y, z + 0.20);
			if (!level.getBlockCollisions(null, supportBox).iterator().hasNext()) { continue; }
			return new Vec3(x, y, z);
		}
		return null;
	}

	private static boolean validateSlots(ServerLevel level, List<FightParticipant> group,
			List<FormationSlot> slots, Set<Entity> groupEntities)
	{
		for (int i = 0; i < slots.size(); i++)
		{
			Entity entity = group.get(i).getEntity();
			AABB box = entity.getBoundingBox().move(
					slots.get(i).position().x - entity.getX(),
					slots.get(i).position().y - entity.getY(),
					slots.get(i).position().z - entity.getZ());

			if (!isLoaded(level, box) || !level.noBlockCollision(box) || !level.noBorderCollision(entity, box))
			{
				return false;
			}

			for (Entity nearby : level.getEntities(null, box.inflate(0.001), candidate ->
					candidate.isAlive() && !groupEntities.contains(candidate)))
			{
				return false;
			}

			for (int j = 0; j < i; j++)
			{
				Entity other = group.get(j).getEntity();
				AABB otherBox = other.getBoundingBox().move(
						slots.get(j).position().x - other.getX(),
						slots.get(j).position().y - other.getY(),
						slots.get(j).position().z - other.getZ());
				if (box.intersects(otherBox)) { return false; }
			}
		}
		return true;
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

	private record FormationSlot(Vec3 position, float yaw) {}
}
