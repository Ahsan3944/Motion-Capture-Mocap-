package net.mt1006.mocap.mocap.fight;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class FightTargetSelector
{
	private FightTargetSelector() {}

	public static @Nullable Entity select(FightParticipant participant, FightDefinition.TargetMode mode,
			List<FightParticipant> participants, List<String> configuredPlayers, MinecraftServer server,
			double detectionRange)
	{
		List<Entity> candidates = collectCandidates(participant, participants, configuredPlayers, server, detectionRange);

		if (mode == FightDefinition.TargetMode.CURRENT_TARGET || mode == FightDefinition.TargetMode.FIXED_TARGET)
		{
			Entity current = participant.getCurrentTarget();
			if (isValid(participant, current, detectionRange)) { return current; }
		}

		if (candidates.isEmpty())
		{
			return null;
		}

		if (mode == FightDefinition.TargetMode.FIXED_TARGET)
		{
			return candidates.get(0);
		}

		if (mode == FightDefinition.TargetMode.LOWEST_HEALTH)
		{
			Entity lowest = null;
			float lowestHealth = Float.MAX_VALUE;
			for (Entity candidate : candidates)
			{
				if (candidate instanceof LivingEntity living && living.getHealth() < lowestHealth)
				{
					lowestHealth = living.getHealth();
					lowest = candidate;
				}
			}
			return lowest;
		}

		Entity nearest = null;
		double nearestDistance = Double.MAX_VALUE;
		for (Entity candidate : candidates)
		{
			double distance = participant.getEntity().distanceToSqr(candidate);
			if (distance < nearestDistance)
			{
				nearestDistance = distance;
				nearest = candidate;
			}
		}
		return nearest;
	}

	public static boolean isValid(FightParticipant participant, @Nullable Entity target, double detectionRange)
	{
		if (!(target instanceof LivingEntity living) || !living.isAlive()) { return false; }
		Entity actor = participant.getEntity();
		if (!actor.isAlive() || actor == target) { return false; }
		if (actor.level() != target.level()) { return false; }
		return actor.distanceToSqr(target) <= detectionRange * detectionRange;
	}

	private static List<Entity> collectCandidates(FightParticipant participant, List<FightParticipant> participants,
			List<String> configuredPlayers, MinecraftServer server, double detectionRange)
	{
		List<Entity> candidates = new ArrayList<>();
		for (FightParticipant other : participants)
		{
			if (!other.isActive() || other.getSide() == participant.getSide()) { continue; }
			Entity entity = other.getEntity();
			if (isValid(participant, entity, detectionRange)) { candidates.add(entity); }
		}

		if (participant.getSide() == FightParticipant.Side.SOURCE)
		{
			for (String playerName : configuredPlayers)
			{
				ServerPlayer player = server.getPlayerList().getPlayerByName(playerName);
				if (player != null && isValid(participant, player, detectionRange) && !candidates.contains(player))
				{
					candidates.add(player);
				}
			}
		}

		return candidates;
	}
}
