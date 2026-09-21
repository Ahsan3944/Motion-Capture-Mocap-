package net.mt1006.mocap.mocap.fight;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class FightTargetSelector
{
	private FightTargetSelector() {}

	public static @Nullable Entity select(FightParticipant participant, FightDefinition.TargetMode mode,
			List<FightParticipant> participants, FightDefinition definition, MinecraftServer server,
			double detectionRange)
	{
		if (mode == FightDefinition.TargetMode.CURRENT_TARGET || mode == FightDefinition.TargetMode.FIXED_TARGET)
		{
			Entity current = participant.getCurrentTarget();
			if (isValid(participant, current, detectionRange)) { return current; }
		}

return selectCandidate(participant, mode, participants, definition, server, detectionRange);
	}

	public static boolean isValid(FightParticipant participant, @Nullable Entity target, double detectionRange)
	{
		if (!(target instanceof LivingEntity living) || !living.isAlive()) { return false; }
		Entity actor = participant.getEntity();
		if (!actor.isAlive() || actor == target) { return false; }
		if (actor.level() != target.level()) { return false; }
		return actor.distanceToSqr(target) <= detectionRange * detectionRange;
	}

	private static @Nullable Entity selectCandidate(FightParticipant participant, FightDefinition.TargetMode mode,
			List<FightParticipant> participants, FightDefinition definition, MinecraftServer server, double detectionRange)
	{
		Entity actor = participant.getEntity();
		Entity selected = null;
		double selectedDistance = Double.MAX_VALUE;
		float selectedHealth = Float.MAX_VALUE;
		boolean first = true;

		for (FightParticipant other : participants)
		{
			if (!other.isActive() || other.getTeamId().equals(participant.getTeamId())) { continue; }
			Entity candidate = other.getEntity();
			if (!isValid(participant, candidate, detectionRange)) { continue; }

			if (mode == FightDefinition.TargetMode.FIXED_TARGET) { return candidate; }
			if (mode == FightDefinition.TargetMode.LOWEST_HEALTH && candidate instanceof LivingEntity living)
			{
				if (living.getHealth() < selectedHealth)
				{
					selectedHealth = living.getHealth();
					selected = candidate;
				}
				continue;
			}
			if (mode != FightDefinition.TargetMode.LOWEST_HEALTH)
			{
				double distance = actor.distanceToSqr(candidate);
				if (first || distance < selectedDistance)
				{
					selectedDistance = distance;
					selected = candidate;
					first = false;
				}
			}
		}

		if (participant.getSide() == FightParticipant.Side.SOURCE)
		{
			for (String playerName : definition.getTargetPlayers())
			{
				ServerPlayer player = server.getPlayerList().getPlayerByName(playerName);
				String playerTeam = definition.getTargetPlayerTeam(playerName);
				if (player == null || playerTeam.equals(participant.getTeamId())
						|| isFightParticipantEntity(participants, player)
						|| !isValid(participant, player, detectionRange)) { continue; }

				if (mode == FightDefinition.TargetMode.FIXED_TARGET) { return player; }
				if (mode == FightDefinition.TargetMode.LOWEST_HEALTH)
				{
					if (player.getHealth() < selectedHealth)
					{
						selectedHealth = player.getHealth();
						selected = player;
					}
				}
				else
				{
					double distance = actor.distanceToSqr(player);
					if (first || distance < selectedDistance)
					{
						selectedDistance = distance;
						selected = player;
						first = false;
					}
				}
			}
		}

		return selected;
	}

	private static boolean isFightParticipantEntity(List<FightParticipant> participants, Entity candidate)
	{
		for (FightParticipant participant : participants)
		{
			if (participant.getEntity() == candidate) { return true; }
		}
		return false;
	}
}
