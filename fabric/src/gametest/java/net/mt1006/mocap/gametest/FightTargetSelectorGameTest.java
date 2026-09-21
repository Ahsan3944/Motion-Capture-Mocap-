package net.mt1006.mocap.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.mt1006.mocap.mocap.fight.FightDefinition;
import net.mt1006.mocap.mocap.fight.FightParticipant;
import net.mt1006.mocap.mocap.fight.FightTargetSelector;

import java.util.List;

public final class FightTargetSelectorGameTest
{
	@GameTest(maxTicks = 40)
	public void targetModesRespectLiveValidityAndTeamRules(GameTestHelper context)
	{
		LivingEntity source = (LivingEntity) context.spawn(EntityType.ZOMBIE, new Vec3(1.5, 1.0, 1.5));
		LivingEntity nearest = (LivingEntity) context.spawn(EntityType.ZOMBIE, new Vec3(3.5, 1.0, 1.5));
		LivingEntity farther = (LivingEntity) context.spawn(EntityType.ZOMBIE, new Vec3(7.5, 1.0, 1.5));
		try
		{
			FightParticipant sourceParticipant = new FightParticipant("source", source, FightParticipant.Side.SOURCE, "RED");
			FightParticipant nearestParticipant = new FightParticipant("nearest", nearest, FightParticipant.Side.TARGET, "BLUE");
			FightParticipant fartherParticipant = new FightParticipant("farther", farther, FightParticipant.Side.TARGET, "BLUE");
			FightDefinition definition = new FightDefinition("selector_test");
			definition.setSourceScenes(List.of("source"));
			definition.setDetectionRange(16.0);
			List<FightParticipant> participants = List.of(sourceParticipant, nearestParticipant, fartherParticipant);

			definition.setTargetMode(FightDefinition.TargetMode.NEAREST);
			context.assertTrue(
					FightTargetSelector.select(sourceParticipant, definition.getTargetMode(), participants, definition,
							context.getLevel().getServer(), definition.getDetectionRange()) == nearest,
						"NEAREST must select the closest hostile participant.");

			farther.setHealth(1.0F);
			definition.setTargetMode(FightDefinition.TargetMode.LOWEST_HEALTH);
			context.assertTrue(
					FightTargetSelector.select(sourceParticipant, definition.getTargetMode(), participants, definition,
							context.getLevel().getServer(), definition.getDetectionRange()) == farther,
						"LOWEST_HEALTH must select the lowest-health hostile participant.");

			definition.setTargetMode(FightDefinition.TargetMode.FIXED_TARGET);
			var fixed = FightTargetSelector.select(sourceParticipant, definition.getTargetMode(), participants, definition,
					context.getLevel().getServer(), definition.getDetectionRange());
			context.assertTrue(fixed == nearest, "FIXED_TARGET must lock the first valid hostile participant.");
			sourceParticipant.setCurrentTarget(fixed);
			sourceParticipant.lockFixedTarget();
			nearest.kill();
			context.assertTrue(
					FightTargetSelector.select(sourceParticipant, definition.getTargetMode(), participants, definition,
							context.getLevel().getServer(), definition.getDetectionRange()) == null,
						"FIXED_TARGET must not retarget after its locked target becomes invalid.");

			farther.setHealth(farther.getMaxHealth());
			definition.setTargetMode(FightDefinition.TargetMode.NEAREST);
			FightParticipant sameTeam = new FightParticipant("same-team", farther, FightParticipant.Side.TARGET, "RED");
			participants = List.of(sourceParticipant, sameTeam);
			context.assertTrue(
					FightTargetSelector.select(sourceParticipant, definition.getTargetMode(), participants, definition,
							context.getLevel().getServer(), definition.getDetectionRange()) == null,
						"Same-team participants must not be selected as hostile targets.");

			context.succeed();
		}
		finally
		{
			source.discard();
			nearest.discard();
			farther.discard();
		}
	}
}
