package net.mt1006.mocap.mocap.fight;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FightDefinitionTest
{
	@Test
	void defaultsAreStable()
	{
		FightDefinition definition = new FightDefinition("arena");
		assertEquals(5, definition.getPower());
		assertEquals(1.0, definition.getAttackSpeed());
		assertEquals(3.0, definition.getAttackRange());
		assertEquals(16.0, definition.getDetectionRange());
		assertEquals(1.0, definition.getMovementSpeed());
		assertEquals(1.0, definition.getDamageMultiplier());
		assertEquals(1.0, definition.getKnockbackMultiplier());
		assertEquals(FightDefinition.TargetMode.NEAREST, definition.getTargetMode());
		assertEquals(FightDefinition.State.STOPPED, definition.getState());
	}

	@Test
	void numericValidationRejectsUnsafeValues()
	{
		FightDefinition definition = new FightDefinition("arena");
		assertFalse(definition.setPower(0));
		assertFalse(definition.setPower(11));
		assertTrue(definition.setPower(10));
		assertFalse(definition.setAttackSpeed(0.0));
		assertFalse(definition.setAttackSpeed(Double.NaN));
		assertFalse(definition.setAttackSpeed(Double.POSITIVE_INFINITY));
		assertTrue(definition.setAttackSpeed(2.5));
		assertFalse(definition.setAttackRange(0.0));
		assertFalse(definition.setDetectionRange(-1.0));
		assertFalse(definition.setMovementSpeed(-0.1));
		assertFalse(definition.setDamageMultiplier(-0.1));
		assertFalse(definition.setKnockbackMultiplier(-0.1));
	}

	@Test
	void teamAssignmentsStayAlignedWithReferences()
	{
		FightDefinition definition = new FightDefinition("arena");
		definition.setSourceScenes(List.of("alpha", "beta"));
		definition.setSourceSceneTeams(List.of("RED", "BLUE"));
		definition.setTargetPlayers(List.of("Steve", "Alex"));
		definition.setTargetPlayerTeams(List.of("BLUE", "GREEN"));
		assertEquals("RED", definition.getSourceSceneTeam("alpha"));
		assertEquals("BLUE", definition.getSourceSceneTeam("beta"));
		assertEquals("BLUE", definition.getTargetPlayerTeam("Steve"));
		assertEquals("GREEN", definition.getTargetPlayerTeam("Alex"));
		assertTrue(definition.setTargetPlayerTeam("Steve", "YELLOW"));
		assertEquals("YELLOW", definition.getTargetPlayerTeam("Steve"));
		assertFalse(definition.setTargetPlayerTeam("Missing", "YELLOW"));
		assertFalse(definition.setTargetPlayerTeam("Steve", "bad team"));
	}

	@Test
	void copyIsDeepForMutableConfiguration()
	{
		FightDefinition original = new FightDefinition("arena");
		original.setSourceScenes(List.of("source"));
		original.setTargetPlayers(List.of("Steve"));
		original.setTargetMode(FightDefinition.TargetMode.FIXED_TARGET);
		original.setPower(8);
		FightDefinition copy = original.copy();
		copy.setSourceScenes(List.of("other"));
		copy.setTargetPlayers(List.of("Alex"));
		copy.setTargetMode(FightDefinition.TargetMode.LOWEST_HEALTH);
		copy.setPower(2);
		assertEquals(List.of("source"), original.getSourceScenes());
		assertEquals(List.of("Steve"), original.getTargetPlayers());
		assertEquals(FightDefinition.TargetMode.FIXED_TARGET, original.getTargetMode());
		assertEquals(8, original.getPower());
		assertEquals(List.of("other"), copy.getSourceScenes());
		assertEquals(List.of("Alex"), copy.getTargetPlayers());
		assertEquals(FightDefinition.TargetMode.LOWEST_HEALTH, copy.getTargetMode());
		assertEquals(2, copy.getPower());
	}

	@Test
	void copyPreservesTeamAssignmentsAndState()
	{
		FightDefinition original = new FightDefinition("arena");
		original.setSourceScenes(List.of("source"));
		original.setSourceSceneTeam("source", "RED");
		original.setTargetScenes(List.of("target"));
		original.setTargetSceneTeam("target", "BLUE");
		original.setState(FightDefinition.State.RUNNING);
		FightDefinition copy = original.copy();
		assertEquals("RED", copy.getSourceSceneTeam("source"));
		assertEquals("BLUE", copy.getTargetSceneTeam("target"));
		assertEquals(FightDefinition.State.RUNNING, copy.getState());
	}
	@Test
	void fixedTargetLockIsClearedOnResetLifecycle()
	{
		FightParticipant participant = new FightParticipant("fighter", null, FightParticipant.Side.SOURCE);
		assertFalse(participant.isFixedTargetLocked());
		participant.lockFixedTarget();
		assertTrue(participant.isFixedTargetLocked());
		participant.resetFixedTargetLock();
		assertFalse(participant.isFixedTargetLocked());
		participant.lockFixedTarget();
		participant.deactivate();
		assertFalse(participant.isFixedTargetLocked());
	}

}
