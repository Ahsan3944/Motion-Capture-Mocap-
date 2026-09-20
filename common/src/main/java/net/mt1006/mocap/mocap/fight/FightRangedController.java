package net.mt1006.mocap.mocap.fight;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;

/**
 * Server-authoritative ranged execution for Fight participants.
 *
 * Phase 6D intentionally supports Bow only. Crossbow loading/charged state is
 * a separate lifecycle and is not approximated here.
 */
public final class FightRangedController
{
	private FightRangedController() {}

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
