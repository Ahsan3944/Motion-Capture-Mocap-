package net.mt1006.mocap.mocap.fight;

import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

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
	private @Nullable Entity currentTarget;
	private State state = State.IDLE;
	private int attackCooldownTicks = 0;
	private boolean active = true;

	public FightParticipant(String id, Entity entity, Side side)
	{
		this.id = id;
		this.entity = entity;
		this.side = side;
	}

	public String getId() { return id; }
	public Entity getEntity() { return entity; }
	public Side getSide() { return side; }

	public @Nullable Entity getCurrentTarget() { return currentTarget; }
	public void setCurrentTarget(@Nullable Entity target) { currentTarget = target; }

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
	}
}
