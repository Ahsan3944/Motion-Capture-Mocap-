package net.mt1006.mocap.mocap.fight;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.ItemStack;

/**
 * Server-authoritative defensive behavior for Fight participants.
 *
 * Phase 6F deliberately delegates blocking to Minecraft's normal ShieldItem
 * and LivingEntity active-item state instead of intercepting damage manually.
 */
public final class FightDefenseController
{
	private static final double DEFAULT_DEFENSE_RANGE = 5.0;
	private FightDefenseController() {}

	public static boolean shouldBlock(FightParticipant participant, LivingEntity target)
	{
		if (participant == null || !participant.isActive() || target == null || !target.isAlive()) { return false; }
		if (!(participant.getEntity() instanceof LivingEntity living) || !living.isAlive()) { return false; }
		if (!FightEquipmentController.hasShield(living)) { return false; }

		return living.distanceToSqr(target) <= DEFAULT_DEFENSE_RANGE * DEFAULT_DEFENSE_RANGE;
	}

	public static boolean tick(FightParticipant participant, LivingEntity target)
	{
		if (!(participant.getEntity() instanceof net.minecraft.world.entity.player.Player player)
				|| !(player.level() instanceof ServerLevel level))
		{
			stop(participant);
			return false;
		}
		LivingEntity living = player;

		if (!shouldBlock(participant, target))
		{
			stop(participant);
			return false;
		}

		FightMovementController.faceTarget(living, target.getX() - living.getX(), target.getZ() - living.getZ());

		if (living.isBlocking())
		{
			participant.setState(FightParticipant.State.USE_ITEM);
			return true;
		}

		InteractionHand hand = findShieldHand(living);
		if (hand == null)
		{
			stop(participant);
			return false;
		}

		ItemStack shield = living.getItemInHand(hand);
		if (!(shield.getItem() instanceof ShieldItem shieldItem))
		{
			stop(participant);
			return false;
		}

		shieldItem.use(level, player, hand);
		participant.setShieldBlockTicks(participant.getShieldBlockTicks() + 1);
		participant.setState(FightParticipant.State.USE_ITEM);
		return living.isBlocking();
	}

	public static void stop(FightParticipant participant)
	{
		if (participant == null || !(participant.getEntity() instanceof LivingEntity living)) { return; }
		if (living.isUsingItem()) { living.stopUsingItem(); }
		participant.clearShieldBlock();
		if (participant.getState() == FightParticipant.State.USE_ITEM)
		{
			participant.setState(FightParticipant.State.RECOVER);
		}
	}

	private static InteractionHand findShieldHand(LivingEntity living)
	{
		if (living.getOffhandItem().getItem() instanceof ShieldItem) { return InteractionHand.OFF_HAND; }
		if (living.getMainHandItem().getItem() instanceof ShieldItem) { return InteractionHand.MAIN_HAND; }
		return null;
	}
}
