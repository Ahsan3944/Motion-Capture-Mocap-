package net.mt1006.mocap.fabric.events;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.mt1006.mocap.mocap.fight.FightFishingRodController;

public final class FightFishingRodFabricEvent
{
	private FightFishingRodFabricEvent() {}

	public static InteractionResult onUseItem(Player player, Level level, InteractionHand hand)
	{
		return FightFishingRodController.handleItemUse(player, hand)
				? InteractionResult.SUCCESS_SERVER
				: InteractionResult.PASS;
	}
}
