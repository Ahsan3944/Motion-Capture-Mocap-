package net.mt1006.mocap.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.mt1006.mocap.mocap.fight.FightFoodController;
import net.mt1006.mocap.mocap.fight.FightParticipant;

public final class FightFoodGameTest
{
	@GameTest(maxTicks = 100)
	public void foodUseContinuesThroughRuntimeCooldown(GameTestHelper context)
	{
		Player player = context.makeMockPlayer(GameType.SURVIVAL);
		LivingEntity target = context.spawn(EntityType.ZOMBIE, 5, 0, 0);
		FightParticipant participant = new FightParticipant("food-test", player, FightParticipant.Side.SOURCE);

		player.setHealth(4.0F);
		player.getFoodData().setFoodLevel(10);
		player.setItemInHand(InteractionHand.MAIN_HAND, Items.BREAD.getDefaultInstance());

		try
		{
			context.assertTrue(FightFoodController.tick(participant, target, 3.0),
					"Food use should start when health is low and the target is outside immediate melee pressure.");
			context.assertTrue(player.isUsingItem(), "The first food-use tick must enter the normal item-use lifecycle.");
			context.assertTrue(participant.getFoodCooldownTicks() > 0, "Food cooldown should be armed after starting consumption.");

			context.assertTrue(FightFoodController.tick(participant, target, 3.0),
					"Food use must continue even while the runtime cooldown is counting down.");
			context.assertTrue(player.isUsingItem(),
					"Food consumption must not be cancelled by the runtime cooldown before the consumable finishes.");
			context.succeed();
		}
		finally
		{
			if (player.isUsingItem()) { player.stopUsingItem(); }
			context.kill(target);
		}
	}
}
