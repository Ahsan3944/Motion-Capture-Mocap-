package net.mt1006.mocap.mocap.fight;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Server-authoritative Food/Consumable behavior for Fight participants.
 *
 * The controller delegates actual consumption, effects, remainder handling and
 * timing to Minecraft's DataComponents.CONSUMABLE lifecycle.
 */
public final class FightFoodController
{
	private static final double HEALTH_THRESHOLD = 0.50;
	private static final double IMMEDIATE_MELEE_DISTANCE_FACTOR = 1.5;
	private static final int POST_CONSUME_COOLDOWN_TICKS = 20;

	private FightFoodController() {}

	public static boolean shouldEat(FightParticipant participant, LivingEntity target, double attackRange)
	{
		if (participant == null || !participant.isActive() || target == null || !target.isAlive()) { return false; }
		if (!(participant.getEntity() instanceof LivingEntity living) || !living.isAlive()) { return false; }
		if (!FightEquipmentController.isUsableFood(living.getMainHandItem())
				&& !FightEquipmentController.isUsableFood(living.getOffhandItem())) { return false; }
		if (participant.getFoodCooldownTicks() > 0) { return false; }
		if (living.getMaxHealth() <= 0.0F || !Float.isFinite(living.getHealth())) { return false; }
		if (living.getHealth() > living.getMaxHealth() * HEALTH_THRESHOLD) { return false; }

		double safeRange = Math.max(0.0, attackRange) * IMMEDIATE_MELEE_DISTANCE_FACTOR;
		return living.distanceToSqr(target) > safeRange * safeRange;
	}

	public static boolean tick(FightParticipant participant, LivingEntity target, double attackRange)
	{
		if (!(participant.getEntity() instanceof Player player) || !(player.level() instanceof ServerLevel level))
		{
			stop(participant);
			return false;
		}

		if (!shouldEat(participant, target, attackRange))
		{
			if (player.isUsingItem() && FightEquipmentController.isUsableFood(player.getUseItem()))
			{
				stop(participant);
			}
			return false;
		}

		FightMovementController.faceTarget(player, target.getX() - player.getX(), target.getZ() - player.getZ());

		if (player.isUsingItem())
		{
			if (FightEquipmentController.isUsableFood(player.getUseItem()))
			{
				participant.setState(FightParticipant.State.USE_ITEM);
				return true;
			}
			stop(participant);
			return false;
		}

		InteractionHand hand = findFoodHand(player);
		if (hand == null) { return false; }

		ItemStack food = player.getItemInHand(hand);
		InteractionResult result = food.use(level, player, hand);
		if (!result.consumesAction())
		{
			return false;
		}

		participant.setState(FightParticipant.State.USE_ITEM);
		int useDuration = Math.max(1, food.getUseDuration(food, player));
		participant.setFoodCooldownTicks(useDuration + POST_CONSUME_COOLDOWN_TICKS);
		return true;
	}

	public static void stop(FightParticipant participant)
	{
		if (participant == null || !(participant.getEntity() instanceof LivingEntity living)) { return; }
		if (living.isUsingItem() && FightEquipmentController.isUsableFood(living.getUseItem()))
		{
			living.stopUsingItem();
		}
		if (participant.getState() == FightParticipant.State.USE_ITEM)
		{
			participant.setState(FightParticipant.State.RECOVER);
		}
	}

	private static InteractionHand findFoodHand(Player player)
	{
		if (FightEquipmentController.isUsableFood(player.getMainHandItem())) { return InteractionHand.MAIN_HAND; }
		if (FightEquipmentController.isUsableFood(player.getOffhandItem())) { return InteractionHand.OFF_HAND; }
		return null;
	}
}
