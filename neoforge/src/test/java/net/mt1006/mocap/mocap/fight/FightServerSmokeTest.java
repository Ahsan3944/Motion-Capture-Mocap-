package net.mt1006.mocap.mocap.fight;

import net.minecraft.server.MinecraftServer;
import net.mt1006.mocap.MocapMod;
import net.mt1006.mocap.command.io.DummyCommandOutput;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class FightServerSmokeTest
{
	@Test
	void fightDefinitionLifecycle(MinecraftServer server)
	{
		assertSame(server, MocapMod.server, "Mocap must be attached to the running test server.");
		String id = "ci_smoke_" + Integer.toUnsignedString(System.identityHashCode(this));
		DummyCommandOutput output = new DummyCommandOutput();
		try
		{
			assertTrue(FightManager.create(output, id));
			assertNotNull(FightManager.get(id));
			assertFalse(FightManager.create(output, id));
			assertTrue(FightManager.setTargetPlayer(output, id, "Steve"));
			assertFalse(FightManager.setTargetPlayer(output, id, ""));
			assertFalse(FightManager.setTargetPlayer(output, id, null));
			assertFalse(FightManager.setTargetMode(output, id, ""));
			assertFalse(FightManager.setTargetMode(output, id, null));
			assertFalse(FightManager.stop(output, id));
			assertTrue(FightManager.setPower(output, id, 10));
			assertEquals(10, FightManager.get(id).getPower());
			assertTrue(FightManager.setAttackRange(output, id, 4.5));
			assertEquals(4.5, FightManager.get(id).getAttackRange());
			assertTrue(FightManager.setTargetMode(output, id, "FIXED_TARGET"));
			assertEquals(FightDefinition.TargetMode.FIXED_TARGET, FightManager.get(id).getTargetMode());
			assertTrue(FightManager.reset(output, id));
			assertFalse(FightManager.isRunning(id));
			FightManager.stopAll();
			assertNotNull(FightManager.get(id));
			assertEquals(FightDefinition.State.STOPPED, FightManager.get(id).getState());
		}
		finally
		{
			FightManager.stopAll();
			if (FightManager.get(id) != null) { FightManager.remove(output, id); }
		}
	}
}
