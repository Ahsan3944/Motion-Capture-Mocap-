package net.mt1006.mocap.mocap.fight;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Server-authoritative ranged execution for Fight participants.
 *
 * Bow and Crossbow use the normal Minecraft item/projectile lifecycle. Crossbow
 * charging is tracked explicitly because it spans multiple Fight ticks.
 */
public final class FightRangedController
{
	private FightRangedController() {}


	/**
	 * Advances the vanilla Crossbow lifecycle by one Fight tick.
	 *
	 * @return true only when a charged Crossbow was actually fired.
	 */
	public static boolean tickCrossbow(FightParticipant participant)
	{
		if (!FightEquipmentController.prepareCrossbowAttack(participant)) {
			participant.clearCrossbowCharge();
			return false;
		}
		if (!(participant.getEntity() instanceof Player player) || !(player.level() instanceof ServerLevel level)) {
			participant.clearCrossbowCharge();
			return false;
		}

		ItemStack stack = player.getMainHandItem();
		if (!(stack.getItem() instanceof CrossbowItem crossbow)) {
			participant.clearCrossbowCharge();
			return false;
		}

		if (CrossbowItem.isCharged(stack))
		{
			participant.clearCrossbowCharge();
			InteractionResult result = crossbow.use(level, player, InteractionHand.MAIN_HAND);
			return result.consumesAction() && !CrossbowItem.isCharged(stack);
		}

		if (player.getProjectile(stack).isEmpty())
		{
			participant.clearCrossbowCharge();
			return false;
		}

		int chargeDuration = Math.max(1, CrossbowItem.getChargeDuration(stack, player));
		int chargeTicks = participant.getCrossbowChargeTicks();
		if (chargeTicks <= 0)
		{
			player.startUsingItem(InteractionHand.MAIN_HAND);
			chargeTicks = 1;
			participant.setCrossbowChargeTicks(chargeTicks);
		}

		int ticksRemaining = Math.max(0, crossbow.getUseDuration(stack, player) - chargeTicks);
		crossbow.onUseTick(level, player, stack, ticksRemaining);
		if (chargeTicks >= chargeDuration)
		{
			// CrossbowItem.releaseUsing() must receive the manually tracked remaining
			// use time so vanilla can calculate the full charge and load the projectile.
			// Calling LivingEntity.releaseUsingItem() here would pass the untouched
			// active-item countdown (still near the full 1200-tick use duration), so
			// CrossbowItem would treat this as an early release and never charge.
			crossbow.releaseUsing(stack, level, player, ticksRemaining);
			// We invoked the item callback directly, so clear the entity's active-item
			// state separately without invoking releaseUsing() a second time.
			player.stopUsingItem();
			participant.clearCrossbowCharge();
		}
		else
		{
			participant.setCrossbowChargeTicks(chargeTicks + 1);
		}
		return false;
	}

	public static boolean canFireBow(FightParticipant participant)
	{
		if (participant == null || !participant.isActive()) { return false; }
		if (!(participant.getEntity() instanceof LivingEntity living) || !living.isAlive()) { return false; }
		return FightEquipmentController.isUsableBow(living, living.getMainHandItem());
	}

	public static double getBowRange(FightParticipant participant)
	{
		if (participant == null || !(participant.getEntity() instanceof LivingEntity living)) { return 0.0; }
		ItemStack stack = living.getMainHandItem();
		if (!(stack.getItem() instanceof BowItem bow)) { return 0.0; }
		if (living.getProjectile(stack).isEmpty()) { return 0.0; }
		return bow.getDefaultProjectileRange();
	}

	public static boolean fireBow(FightParticipant participant)
	{
		if (!FightEquipmentController.prepareBowAttack(participant)) { return false; }
		if (!(participant.getEntity() instanceof LivingEntity living)) { return false; }
		if (!(living.level() instanceof ServerLevel level)) { return false; }

		ItemStack bowStack = living.getMainHandItem();
		if (!(bowStack.getItem() instanceof BowItem bow)) { return false; }
		if (living.getProjectile(bowStack).isEmpty()) { return false; }

		int useDuration = Math.max(1, bow.getUseDuration(bowStack, living));
		int fullDrawTicks = Math.min(20, useDuration);
		int remainingTime = Math.max(0, useDuration - fullDrawTicks);
		return bow.releaseUsing(bowStack, level, living, remainingTime);
	}
}
