package net.mt1006.mocap.mocap.fight;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ShieldItem;

/**
 * Runtime-only combat equipment decisions for Fight participants.
 *
 * This controller intentionally works only with the equipment slots already
 * represented by the MoCap recording format. It never creates or searches
 * inventory items.
 */
public final class FightEquipmentController
{
	private FightEquipmentController() {}

	/**
	 * Ensures that a verified melee-capable equipped item is in the main hand
	 * before vanilla-style melee attack execution.
	 *
	 * @return true when the main hand is already valid or an off-hand weapon was moved to it
	 */
	public static boolean prepareMeleeAttack(FightParticipant participant)
	{
		if (participant == null || !participant.isActive()) { return false; }
		if (!(participant.getEntity() instanceof LivingEntity living) || !living.isAlive()) { return false; }

		ItemStack mainHand = living.getMainHandItem();
		if (isMeleeWeapon(mainHand, EquipmentSlot.MAINHAND)) { return true; }

		ItemStack offHand = living.getOffhandItem();
		if (!isMeleeWeapon(offHand, EquipmentSlot.OFFHAND)) { return false; }

		living.setItemInHand(InteractionHand.MAIN_HAND, offHand.copy());
		living.setItemInHand(InteractionHand.OFF_HAND, mainHand.copy());
		return true;
	}

	/**
	 * Puts an equipped Bow into the main hand and verifies that the actor can resolve
	 * compatible ammunition through the normal LivingEntity projectile lookup.
	 */
	public static boolean prepareBowAttack(FightParticipant participant)
	{
		if (participant == null || !participant.isActive()) { return false; }
		if (!(participant.getEntity() instanceof LivingEntity living) || !living.isAlive()) { return false; }

		ItemStack mainHand = living.getMainHandItem();
		if (isUsableBow(living, mainHand)) { return true; }

		ItemStack offHand = living.getOffhandItem();
		if (!isUsableBow(living, offHand)) { return false; }

		living.setItemInHand(InteractionHand.MAIN_HAND, offHand.copy());
		living.setItemInHand(InteractionHand.OFF_HAND, mainHand.copy());
		return true;
	}

	public static boolean prepareCrossbowAttack(FightParticipant participant)
	{
		if (participant == null || !participant.isActive()) { return false; }
		if (!(participant.getEntity() instanceof LivingEntity living) || !living.isAlive()) { return false; }

		ItemStack mainHand = living.getMainHandItem();
		if (isUsableCrossbow(living, mainHand)) { return true; }

		ItemStack offHand = living.getOffhandItem();
		if (!isUsableCrossbow(living, offHand)) { return false; }

		living.setItemInHand(InteractionHand.MAIN_HAND, offHand.copy());
		living.setItemInHand(InteractionHand.OFF_HAND, mainHand.copy());
		return true;
	}

	public static boolean prepareShieldUse(FightParticipant participant)
	{
		if (participant == null || !participant.isActive()) { return false; }
		if (!(participant.getEntity() instanceof LivingEntity living) || !living.isAlive()) { return false; }

		ItemStack offHand = living.getOffhandItem();
		if (offHand.getItem() instanceof ShieldItem) { return true; }

		ItemStack mainHand = living.getMainHandItem();
		return mainHand.getItem() instanceof ShieldItem;
	}

	public static boolean hasShield(LivingEntity living)
	{
		return living.getMainHandItem().getItem() instanceof ShieldItem
				|| living.getOffhandItem().getItem() instanceof ShieldItem;
	}

	public static boolean isUsableCrossbow(LivingEntity living, ItemStack stack)
	{
		if (!(stack.getItem() instanceof CrossbowItem)) { return false; }
		return CrossbowItem.isCharged(stack) || !living.getProjectile(stack).isEmpty();
	}

	public static double getRangedRange(FightParticipant participant)
	{
		if (participant == null || !(participant.getEntity() instanceof LivingEntity living)) { return 0.0; }
		ItemStack mainHand = living.getMainHandItem();
		if (mainHand.getItem() instanceof BowItem bow && !living.getProjectile(mainHand).isEmpty())
		{
			return bow.getDefaultProjectileRange();
		}
		if (mainHand.getItem() instanceof CrossbowItem crossbow && isUsableCrossbow(living, mainHand))
		{
			return crossbow.getDefaultProjectileRange();
		}
		ItemStack offHand = living.getOffhandItem();
		if (offHand.getItem() instanceof BowItem bow && !living.getProjectile(offHand).isEmpty())
		{
			return bow.getDefaultProjectileRange();
		}
		if (offHand.getItem() instanceof CrossbowItem crossbow && isUsableCrossbow(living, offHand))
		{
			return crossbow.getDefaultProjectileRange();
		}
		return 0.0;
	}

	public static boolean isUsableBow(LivingEntity living, ItemStack stack)
	{
		return stack.getItem() instanceof BowItem && !living.getProjectile(stack).isEmpty();
	}

	private static boolean isMeleeWeapon(ItemStack stack, EquipmentSlot slot)
	{
		if (stack == null || stack.isEmpty()) { return false; }

		boolean[] found = {false};
		stack.forEachModifier(slot, (attribute, modifier) ->
		{
			if (attribute.equals(Attributes.ATTACK_DAMAGE) && Double.isFinite(modifier.amount()) && modifier.amount() > 0.0)
			{
				found[0] = true;
			}
		});
		return found[0];
	}
}
