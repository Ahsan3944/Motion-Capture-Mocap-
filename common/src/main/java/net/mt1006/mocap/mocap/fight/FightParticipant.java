package net.mt1006.mocap.mocap.fight;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class FightParticipant
{
	public enum Side
	{
		SOURCE,
		TARGET
	}

	public enum State
	{
		IDLE,
		SEARCH_TARGET,
		CHASE,
		POSITION,
		ATTACK,
		RECOVER,
		USE_ITEM,
		RETREAT,
		DEAD,
		DISABLED
	}

	private final String id;
	private final Entity entity;
	private final Side side;
	private final String teamId;
	private final Map<EquipmentSlot, ItemStack> equipmentSnapshot = new EnumMap<>(EquipmentSlot.class);
	private @Nullable Entity currentTarget;
	private State state = State.IDLE;
	private int attackCooldownTicks = 0;
	private int movementBlockedTicks = 0;
	private int movementRecoveryDirection = 1;
	private List<Vec3> navigationWaypoints = List.of();
	private int navigationWaypointIndex = 0;
	private int navigationAgeTicks = 0;
	private int navigationStallTicks = 0;
	private @Nullable Vec3 navigationTargetPosition;
	private boolean active = true;

	public FightParticipant(String id, Entity entity, Side side)
	{
		this(id, entity, side, side == Side.SOURCE ? "SOURCE" : "TARGET");
	}

	public FightParticipant(String id, Entity entity, Side side, String teamId)
	{
		this.id = id;
		this.entity = entity;
		this.side = side;
		this.teamId = teamId;
	}

	public String getId() { return id; }
	public Entity getEntity() { return entity; }
	public Side getSide() { return side; }
	public String getTeamId() { return teamId; }

	public void captureEquipmentSnapshot()
	{
		equipmentSnapshot.clear();
		if (!(entity instanceof LivingEntity living)) { return; }
		for (EquipmentSlot slot : combatEquipmentSlots())
		{
			equipmentSnapshot.put(slot, living.getItemBySlot(slot).copy());
		}
	}

	public void restoreEquipmentSnapshot()
	{
		if (!(entity instanceof LivingEntity living)) { return; }
		for (Map.Entry<EquipmentSlot, ItemStack> entry : equipmentSnapshot.entrySet())
		{
			ItemStack current = living.getItemBySlot(entry.getKey());
			ItemStack expected = entry.getValue();
			if (!ItemStack.matches(current, expected))
			{
				living.setItemSlot(entry.getKey(), expected.copy());
			}
		}
	}

	public ItemStack getRecordedMainHandItem()
	{
		return equipmentSnapshot.getOrDefault(EquipmentSlot.MAINHAND, ItemStack.EMPTY).copy();
	}

	public ItemStack getRecordedOffHandItem()
	{
		return equipmentSnapshot.getOrDefault(EquipmentSlot.OFFHAND, ItemStack.EMPTY).copy();
	}

	private static EquipmentSlot[] combatEquipmentSlots()
	{
		return new EquipmentSlot[] {
				EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.FEET, EquipmentSlot.LEGS,
				EquipmentSlot.CHEST, EquipmentSlot.HEAD, EquipmentSlot.BODY, EquipmentSlot.SADDLE
		};
	}

	public @Nullable Entity getCurrentTarget() { return currentTarget; }
	public void setCurrentTarget(@Nullable Entity target)
	{
		if (currentTarget != target)
		{
			movementBlockedTicks = 0;
			movementRecoveryDirection = 1;
			clearNavigationPath();
		}
		currentTarget = target;
	}

	public int getMovementBlockedTicks() { return movementBlockedTicks; }

	public int incrementMovementBlockedTicks() { return ++movementBlockedTicks; }

	public void resetMovementBlockedTicks() { movementBlockedTicks = 0; }

	public int getMovementRecoveryDirection() { return movementRecoveryDirection; }

	public void flipMovementRecoveryDirection() { movementRecoveryDirection = -movementRecoveryDirection; }

	public void setNavigationPath(List<Vec3> waypoints, @Nullable Vec3 targetPosition)
	{
		navigationWaypoints = waypoints.isEmpty() ? List.of() : List.copyOf(waypoints);
		navigationWaypointIndex = 0;
		navigationAgeTicks = 0;
		navigationStallTicks = 0;
		navigationTargetPosition = targetPosition;
	}

	public void clearNavigationPath()
	{
		navigationWaypoints = List.of();
		navigationWaypointIndex = 0;
		navigationAgeTicks = 0;
		navigationStallTicks = 0;
		navigationTargetPosition = null;
	}

	public @Nullable Vec3 getNavigationWaypoint()
	{
		if (navigationWaypointIndex >= navigationWaypoints.size()) { return null; }
		return navigationWaypoints.get(navigationWaypointIndex);
	}

	public void advanceNavigationWaypoint()
	{
		if (navigationWaypointIndex < navigationWaypoints.size()) { navigationWaypointIndex++; }
		if (navigationWaypointIndex >= navigationWaypoints.size()) { clearNavigationPath(); }
	}

	public int getNavigationAgeTicks() { return navigationAgeTicks; }

	public int getNavigationStallTicks() { return navigationStallTicks; }

	public int incrementNavigationStallTicks() { return ++navigationStallTicks; }

	public void resetNavigationStallTicks() { navigationStallTicks = 0; }

	public int tickNavigationAge()
	{
		return ++navigationAgeTicks;
	}

	public @Nullable Vec3 getNavigationTargetPosition() { return navigationTargetPosition; }

	public State getState() { return state; }
	public void setState(State state) { this.state = state; }

	public int getAttackCooldownTicks() { return attackCooldownTicks; }

	public boolean tickAttackCooldown()
	{
		if (attackCooldownTicks <= 0) { return true; }
		attackCooldownTicks--;
		return attackCooldownTicks == 0;
	}

	public void setAttackCooldownTicks(int ticks)
	{
		attackCooldownTicks = Math.max(0, ticks);
	}

	public boolean isActive() { return active; }

	public void deactivate()
	{
		active = false;
		currentTarget = null;
		state = State.DEAD;
		attackCooldownTicks = 0;
		movementBlockedTicks = 0;
		movementRecoveryDirection = 1;
		navigationStallTicks = 0;
		clearNavigationPath();
	}
}
