package net.mt1006.mocap.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.mt1006.mocap.command.io.DummyCommandOutput;
import net.mt1006.mocap.mocap.fight.FightDefinition;
import net.mt1006.mocap.mocap.fight.FightManager;

public final class FightServerSmokeGameTest
{
	@GameTest(maxTicks = 100)
	public void fightDefinitionLifecycle(GameTestHelper context)
	{
		String id = "ci_smoke_" + Integer.toUnsignedString(System.identityHashCode(context));
		DummyCommandOutput output = new DummyCommandOutput();
		try
		{
			context.assertTrue(FightManager.create(output, id), "Fight definition should be created on the running server.");
			context.assertTrue(FightManager.get(id) != null, "Created Fight definition must be visible.");
			context.assertTrue(FightManager.setPower(output, id, 10), "Power mutation should persist.");
			context.assertTrue(FightManager.get(id).getPower() == 10, "Power must be updated.");
			context.assertTrue(FightManager.setAttackRange(output, id, 4.5), "Attack range mutation should persist.");
			context.assertTrue(Math.abs(FightManager.get(id).getAttackRange() - 4.5) < 1.0E-9, "Attack range must be updated.");
			context.assertTrue(FightManager.setTargetMode(output, id, "FIXED_TARGET"), "Target mode mutation should persist.");
			context.assertTrue(FightManager.get(id).getTargetMode() == FightDefinition.TargetMode.FIXED_TARGET, "Target mode must be updated.");
			context.assertTrue(FightManager.reset(output, id), "Reset of an inactive Fight should succeed.");
			context.assertTrue(!FightManager.isRunning(id), "Smoke test Fight must not be active.");
			context.succeed();
		}
		finally
		{
			FightManager.stopAll();
			if (FightManager.get(id) != null) { FightManager.remove(output, id); }
		}
	}
}
