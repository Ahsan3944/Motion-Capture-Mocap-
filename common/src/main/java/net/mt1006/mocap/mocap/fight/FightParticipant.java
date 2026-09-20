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

	private final String id;
	private final Entity entity;
	private final Side side;
	private @Nullable Entity currentTarget;
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

	public boolean isActive() { return active; }
	public void deactivate()
	{
		active = false;
		currentTarget = null;
	}
}
