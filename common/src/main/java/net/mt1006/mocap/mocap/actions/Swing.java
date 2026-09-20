package net.mt1006.mocap.mocap.actions;

import net.minecraft.core.Holder;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.mt1006.mocap.api.v1.extension.MocapRecordingData;
import net.mt1006.mocap.api.v1.extension.actions.MocapActionContext;
import net.mt1006.mocap.api.v1.extension.actions.MocapStateAction;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class Swing implements MocapStateAction
{
	private final boolean swinging;
	private final int swingingTime;
	private final InteractionHand hand;

	public static @Nullable Swing fromEntity(Entity entity)
	{
		return (entity instanceof LivingEntity livingEntity) ? new Swing(livingEntity) : null;
	}

	private Swing(LivingEntity entity)
	{
		swinging = entity.swinging;
		swingingTime = entity.swingTime;
		hand = entity.swingingArm;
	}

	public Swing(Reader reader)
	{
		swinging = true;
		swingingTime = 0;
		hand = reader.readBoolean() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
	}

	@Override public boolean differs(MocapStateAction previousAction)
	{
		Swing previousSwing = (Swing)previousAction;
		return swinging && (previousSwing == null || !previousSwing.swinging || previousSwing.swingingTime > swingingTime);
	}

	@Override public boolean shouldBeInitialized()
	{
		return false;
	}

	@Override public void write(Writer writer, MocapRecordingData data)
	{
		writer.addBoolean(hand == InteractionHand.OFF_HAND);
	}

	@Override public Result execute(MocapActionContext ctx)
	{
		if (!(ctx.getEntity() instanceof LivingEntity entity)) { return Result.IGNORED; }

		double hitRange = Math.min(ctx.getConfig().getHitRange(), 4.0);
		if (hitRange > 0.0)
		{
			Entity entityToHit = getEntityToHit(entity, hitRange);
			if (entityToHit != null) { attackTarget(entity, entityToHit, ctx.getLevel()); }
		}

		entity.swing(hand); // resets attackStrengthTicker for players
		return Result.OK;
	}

	/** Executes the existing MoCap/vanilla-style attack mechanics against one explicit target. */
	public static boolean attackTarget(LivingEntity attacker, Entity target, ServerLevel level)
	{
		if (!attacker.isAlive() || !target.isAlive() || attacker == target) { return false; }
		if (attacker instanceof Player player)
		{
			player.attack(target);
		}
		else
		{
			boolean hasAttackDamage = attacker.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE);
			if (attacker instanceof Mob mob && hasAttackDamage)
			{
				mob.doHurtTarget(level, target);
			}
			else
			{
				float damage = (float)getAttribValueOrDef(attacker, Attributes.ATTACK_DAMAGE, 1.0f);
				target.hurtServer(level, level.damageSources().mobAttack(attacker), damage);
			}
		}
		return true;
	}

	private static @Nullable Entity getEntityToHit(LivingEntity entity, double rangeMultiplier)
	{
		double range = getAttribValueOrDef(entity, Attributes.ENTITY_INTERACTION_RANGE, 4.5) * rangeMultiplier;

		Vec3 eyePos = entity.getEyePosition();
		Vec3 viewVector = entity.calculateViewVector(entity.getXRot(), entity.getYRot()).scale(range);
		Vec3 hitVector = eyePos.add(viewVector);

		AABB searchBoundingBox = entity.getBoundingBox().expandTowards(viewVector).inflate(rangeMultiplier);
		return findEntityToHit(entity, eyePos, hitVector, searchBoundingBox, range * range, rangeMultiplier);
	}

	private static @Nullable Entity findEntityToHit(Entity hittingEntity, Vec3 startPos, Vec3 endPos,
													AABB searchBoundingBox, double rangeSqr, double rangeMultiplier)
	{
		Entity closestEntity = null;
		double closestDistSqr = rangeSqr;

		for (Entity entity : hittingEntity.level().getEntities(null, searchBoundingBox))
		{
			if (entity == hittingEntity) { continue; }
			AABB hitBox = entity.getBoundingBox().inflate(entity.getPickRadius() + Math.max(rangeMultiplier - 1.0, 0.0));
			Optional<Vec3> clippingPoint = hitBox.clip(startPos, endPos);

			if (hitBox.contains(startPos))
			{
				closestEntity = entity;
				closestDistSqr = 0.0;
			}
			else if (clippingPoint.isPresent())
			{
				double distToClippingPointSqr = startPos.distanceToSqr(clippingPoint.get());
				if (distToClippingPointSqr < closestDistSqr || closestDistSqr == 0.0)
				{
					closestEntity = entity;
					closestDistSqr = distToClippingPointSqr;
				}
			}
		}
		return closestEntity;
	}

	private static double getAttribValueOrDef(LivingEntity entity, Holder<Attribute> attribute, double defVal)
	{
		AttributeMap attributeMap = entity.getAttributes();
		return attributeMap.hasAttribute(attribute) ? attributeMap.getValue(attribute) : defVal;
	}
}
