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
		double detectionRangeSqr = detectionRange * detectionRange;
		if (mode == FightDefinition.TargetMode.CURRENT_TARGET || mode == FightDefinition.TargetMode.FIXED_TARGET)
		{
			Entity current = participant.getCurrentTarget();
			if (isValidWithSquaredRange(participant, current, detectionRangeSqr)) { return current; }
		}

		return selectCandidate(participant, mode, participants, definition, server, detectionRangeSqr);
	}

	public static boolean isValid(FightParticipant participant, @Nullable Entity target, double detectionRange)
	{
		return getValidDistanceSqr(participant, target, detectionRange * detectionRange) >= 0.0;
	}

	private static boolean isValidWithSquaredRange(FightParticipant participant, @Nullable Entity target, double detectionRangeSqr)
	{
		return getValidDistanceSqr(participant, target, detectionRangeSqr) >= 0.0;
	}

	private static double getValidDistanceSqr(FightParticipant participant, @Nullable Entity target, double detectionRangeSqr)
	{
		if (!(target instanceof LivingEntity living) || !living.isAlive()) { return -1.0; }
		Entity actor = participant.getEntity();
		if (!actor.isAlive() || actor == target) { return -1.0; }
		if (actor.level() != target.level()) { return -1.0; }
		double distanceSqr = actor.distanceToSqr(target);
		return distanceSqr <= detectionRangeSqr ? distanceSqr : -1.0;
	}

	private static @Nullable Entity selectCandidate(FightParticipant participant, FightDefinition.TargetMode mode,
			List<FightParticipant> participants, FightDefinition definition, MinecraftServer server, double detectionRangeSqr)
	{
		Entity selected = null;
		double selectedDistance = Double.MAX_VALUE;
		float selectedHealth = Float.MAX_VALUE;
		boolean first = true;

		for (FightParticipant other : participants)
		{
			if (!other.isActive() || other.getTeamId().equals(participant.getTeamId())) { continue; }
			Entity candidate = other.getEntity();
			double candidateDistanceSqr = getValidDistanceSqr(participant, candidate, detectionRangeSqr);
			if (candidateDistanceSqr < 0.0) { continue; }

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
				double distance = candidateDistanceSqr;
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
						|| isFightParticipantEntity(participants, player)) { continue; }
				double playerDistanceSqr = getValidDistanceSqr(participant, player, detectionRangeSqr);
				if (playerDistanceSqr < 0.0) { continue; }

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
					double distance = playerDistanceSqr;
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
