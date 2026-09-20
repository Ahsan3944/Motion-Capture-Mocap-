package net.mt1006.mocap.fabric.events;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.mt1006.mocap.events.BlockInteractionEvent;
import net.mt1006.mocap.mocap.fight.FightFishingRodController;
import org.jetbrains.annotations.Nullable;

public class BlockInteractionFabricEvent
{
	public static boolean onBlockBreak(Level level, Player player, BlockPos pos, BlockState blockState, @Nullable BlockEntity blockEntity)
	{
		BlockInteractionEvent.onBlockBreak(player, pos, blockState);
		return true;
	}

	public static InteractionResult onRightClickBlock(Player player, Level level, InteractionHand hand, BlockHitResult hitResult)
	{
		if (FightFishingRodController.handleBlockUse(player, hand, hitResult.getLocation()))
		{
			return InteractionResult.SUCCESS_SERVER;
		}
		BlockInteractionEvent.onRightClickBlock(player, hand, hitResult, false);
		return InteractionResult.PASS;
	}
}
