package net.mt1006.mocap.mocap.fight;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

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
