package net.mt1006.mocap.mocap.fight;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class FightTargetSelectorServerTest
{
	@Test
	void targetModesRespectLiveValidityAndTeamRules(MinecraftServer server)
	{
		LivingEntity source = spawn(server, 1.5, 1.0, 1.5);
		LivingEntity nearest = spawn(server, 3.5, 1.0, 1.5);
		LivingEntity farther = spawn(server, 7.5, 1.0, 1.5);
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
			assertSame(nearest, FightTargetSelector.select(sourceParticipant, definition.getTargetMode(), participants, definition,
					server, definition.getDetectionRange()));

			farther.setHealth(1.0F);
			definition.setTargetMode(FightDefinition.TargetMode.LOWEST_HEALTH);
			assertSame(farther, FightTargetSelector.select(sourceParticipant, definition.getTargetMode(), participants, definition,
					server, definition.getDetectionRange()));

			definition.setTargetMode(FightDefinition.TargetMode.FIXED_TARGET);
			Entity fixed = FightTargetSelector.select(sourceParticipant, definition.getTargetMode(), participants, definition,
					server, definition.getDetectionRange());
			assertSame(nearest, fixed);
			sourceParticipant.setCurrentTarget(fixed);
			sourceParticipant.lockFixedTarget();
			nearest.kill();
			assertNull(FightTargetSelector.select(sourceParticipant, definition.getTargetMode(), participants, definition,
					server, definition.getDetectionRange()));

			farther.setHealth(farther.getMaxHealth());
			definition.setTargetMode(FightDefinition.TargetMode.NEAREST);
			participants = List.of(sourceParticipant, new FightParticipant("same-team", farther, FightParticipant.Side.TARGET, "RED"));
			assertNull(FightTargetSelector.select(sourceParticipant, definition.getTargetMode(), participants, definition,
					server, definition.getDetectionRange()));
		}
		finally
		{
			source.discard();
			nearest.discard();
			farther.discard();
		}
	}

	private static LivingEntity spawn(MinecraftServer server, double x, double y, double z)
	{
		LivingEntity entity = (LivingEntity) EntityType.ZOMBIE.create(server.overworld());
		assertNotNull(entity);
		entity.setNoAi(true);
		entity.setPos(x, y, z);
		server.overworld().addFreshEntity(entity);
		return entity;
	}
}
