package net.mt1006.mocap.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.mt1006.mocap.mocap.fight.FightEquipmentController;
import net.mt1006.mocap.mocap.fight.FightMovementController;
import net.mt1006.mocap.mocap.fight.FightNavigationController;
import net.mt1006.mocap.mocap.fight.FightParticipant;
import net.mt1006.mocap.mocap.fight.FightRangedController;

public final class FightMovementAndEquipmentGameTest
{
    @GameTest(maxTicks = 40)
    public void directChaseMovesTowardTargetWithinTickCap(GameTestHelper context)
    {
        fillFloor(context, 0, 0, 12, 12);
        LivingEntity actor = context.spawn(EntityType.ARMOR_STAND, 1, 1, 2);
        LivingEntity target = context.spawn(EntityType.ARMOR_STAND, 8, 1, 2);
        FightParticipant participant = new FightParticipant(
                "movement-direct", actor, FightParticipant.Side.SOURCE, "RED");

        try
        {
            actor.setNoGravity(true);
            target.setNoGravity(true);
            Vec3 startPosition = actor.position();
            double before = actor.distanceToSqr(target);

            context.assertTrue(
                    FightMovementController.chase(participant, target, 20.0, 1.0),
                    "A clear target path must produce a successful chase movement.");

            double after = actor.distanceToSqr(target);
            double moved = actor.position().distanceTo(startPosition);
            context.assertTrue(after < before, "Direct chase must reduce target distance.");
            context.assertTrue(moved <= 0.501, "Movement must respect the 0.5 block/tick safety cap.");
            context.succeed();
        }
        finally
        {
            actor.discard();
            target.discard();
        }
    }

    @GameTest(maxTicks = 40)
    public void blockedChaseUsesBoundedSideRecovery(GameTestHelper context)
    {
        fillFloor(context, 0, 0, 10, 10);
        for (int y = 1; y <= 2; y++)
        {
            context.setBlock(3, y, 1, Blocks.STONE);
        }

        LivingEntity actor = context.spawn(EntityType.ARMOR_STAND, 1, 1, 1);
        LivingEntity target = context.spawn(EntityType.ARMOR_STAND, 6, 1, 1);
        actor.setPos(context.absoluteVec(new Vec3(1.5, 1.0, 1.9)));
        target.setPos(context.absoluteVec(new Vec3(6.5, 1.0, 1.9)));
        FightParticipant participant = new FightParticipant(
                "movement-recovery", actor, FightParticipant.Side.SOURCE, "RED");

        try
        {
            actor.setNoGravity(true);
            target.setNoGravity(true);
            double startZ = actor.getZ();

            for (int tick = 0; tick < 7; tick++)
            {
                FightMovementController.chase(participant, target, 20.0, 1.0);
            }

            context.assertTrue(
                    actor.getZ() != startZ,
                    "Repeated obstruction must trigger bounded lateral recovery.");
            context.assertTrue(
                    Math.abs(actor.getZ() - startZ) <= 0.501,
                    "A single recovery step must remain within the movement cap.");
            context.succeed();
        }
        finally
        {
            actor.discard();
            target.discard();
        }
    }

    @GameTest(maxTicks = 60)
    public void boundedNavigationFindsLoadedRouteAroundObstacle(GameTestHelper context)
    {
        fillFloor(context, 0, 0, 12, 12);
        for (int z = 0; z <= 5; z++)
        {
            for (int y = 1; y <= 2; y++)
            {
                context.setBlock(4, y, z, Blocks.STONE);
            }
        }

        LivingEntity actor = context.spawn(EntityType.ARMOR_STAND, 1, 1, 2);
        LivingEntity target = context.spawn(EntityType.ARMOR_STAND, 9, 1, 2);
        FightParticipant participant = new FightParticipant(
                "navigation-route", actor, FightParticipant.Side.SOURCE, "RED");

        try
        {
            actor.setNoGravity(true);
            target.setNoGravity(true);

            context.assertTrue(
                    FightNavigationController.navigate(participant, target, 10.0, 1.0),
                    "A bounded loaded route around a small wall must be navigable.");

            context.assertTrue(
                    participant.getNavigationWaypoint() != null,
                    "Successful navigation must retain a runtime waypoint.");
            context.assertTrue(
                    participant.getNavigationAgeTicks() > 0,
                    "Successful navigation must advance bounded navigation age.");
            context.succeed();
        }
        finally
        {
            actor.discard();
            target.discard();
        }
    }

    @GameTest(maxTicks = 40)
    public void equipmentPreparationAndResetSnapshotsRemainIsolated(GameTestHelper context)
    {
        LivingEntity first = context.spawn(EntityType.ARMOR_STAND, 2, 1, 2);
        LivingEntity second = context.spawn(EntityType.ARMOR_STAND, 5, 1, 2);
        FightParticipant firstParticipant =
                new FightParticipant("equipment-first", first, FightParticipant.Side.SOURCE, "RED");
        FightParticipant secondParticipant =
                new FightParticipant("equipment-second", second, FightParticipant.Side.SOURCE, "RED");

        try
        {
            first.setItemInHand(InteractionHand.MAIN_HAND, Items.STICK.getDefaultInstance());
            first.setItemInHand(InteractionHand.OFF_HAND, Items.IRON_SWORD.getDefaultInstance());
            first.setItemSlot(EquipmentSlot.HEAD, Items.IRON_HELMET.getDefaultInstance());
            second.setItemInHand(InteractionHand.MAIN_HAND, Items.DIAMOND_SWORD.getDefaultInstance());

            firstParticipant.captureResetSnapshot();
            secondParticipant.captureResetSnapshot();

            context.assertTrue(
                    FightEquipmentController.prepareMeleeAttack(firstParticipant),
                    "A melee-capable off-hand item must be promoted to the main hand.");
            context.assertTrue(
                    first.getMainHandItem().is(Items.IRON_SWORD),
                    "Prepared melee equipment must be in the main hand.");
            context.assertTrue(
                    first.getOffhandItem().is(Items.STICK),
                    "The previous main-hand item must be preserved in the off hand.");

            first.setItemInHand(InteractionHand.MAIN_HAND, Items.GOLDEN_SWORD.getDefaultInstance());
            first.setItemInHand(InteractionHand.OFF_HAND, Items.AIR.getDefaultInstance());
            first.setItemSlot(EquipmentSlot.HEAD, Items.AIR.getDefaultInstance());
            second.setItemInHand(InteractionHand.MAIN_HAND, Items.STICK.getDefaultInstance());

            firstParticipant.restoreEquipmentSnapshot();
            secondParticipant.restoreEquipmentSnapshot();

            context.assertTrue(
                    first.getMainHandItem().is(Items.STICK),
                    "First participant reset must restore only its own main-hand snapshot.");
            context.assertTrue(
                    first.getOffhandItem().is(Items.IRON_SWORD),
                    "First participant reset must restore its own off-hand snapshot.");
            context.assertTrue(
                    first.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(Items.IRON_HELMET),
                    "First participant reset must restore its own armor snapshot.");
            context.assertTrue(
                    second.getMainHandItem().is(Items.DIAMOND_SWORD),
                    "Second participant reset must remain independent from the first participant.");
            context.succeed();
        }
        finally
        {
            first.discard();
            second.discard();
        }
    }

    @GameTest(maxTicks = 20)
    public void resetWithoutCapturedSnapshotLeavesRuntimeEntityUntouched(GameTestHelper context)
    {
        LivingEntity actor = context.spawn(EntityType.ARMOR_STAND, 4, 1, 4);
        FightParticipant participant =
                new FightParticipant("reset-no-snapshot", actor, FightParticipant.Side.SOURCE, "RED");

        try
        {
            actor.setPos(context.absoluteVec(new Vec3(7.0, 1.0, 7.0)));
            actor.setYRot(145.0F);
            actor.setXRot(18.0F);

            context.assertFalse(
                    participant.hasResetSnapshot(),
                    "A participant must not report a reset snapshot before capture.");
            participant.restoreResetSnapshot();

            context.assertTrue(
                    actor.position().distanceTo(context.absoluteVec(new Vec3(7.0, 1.0, 7.0))) < 1.0E-6,
                    "A failed or partial Fight construction must not move an uncaptured participant.");
            context.assertTrue(
                    Math.abs(actor.getYRot() - 145.0F) < 1.0E-6,
                    "An uncaptured participant's yaw must remain untouched.");
            context.assertTrue(
                    Math.abs(actor.getXRot() - 18.0F) < 1.0E-6,
                    "An uncaptured participant's pitch must remain untouched.");
            context.succeed();
        }
        finally
        {
            actor.discard();
        }
    }



    @GameTest(maxTicks = 80)
    public void crossbowChargeReleaseClearsItemUseState(GameTestHelper context)
    {
        LivingEntity target = context.spawn(EntityType.ARMOR_STAND, 8, 1, 4);
        net.minecraft.world.entity.player.Player player = context.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(context.absoluteVec(new Vec3(4.0, 1.0, 4.0)));
        player.getInventory().add(Items.ARROW.getDefaultInstance());
        player.setItemInHand(InteractionHand.MAIN_HAND, Items.CROSSBOW.getDefaultInstance());
        FightParticipant participant =
                new FightParticipant("crossbow-lifecycle", player, FightParticipant.Side.SOURCE, "RED");

        try
        {
            participant.captureResetSnapshot();

            for (int tick = 0; tick < 30
                    && !net.minecraft.world.item.CrossbowItem.isCharged(player.getMainHandItem()); tick++)
            {
                boolean fired = FightRangedController.tickCrossbow(participant);
                context.assertFalse(
                        fired,
                        "Charging alone must not report a fired projectile.");
            }

            context.assertTrue(
                    !player.isUsingItem(),
                    "Completing Crossbow charge must release the entity's active item-use state.");
            context.assertTrue(
                    net.minecraft.world.item.CrossbowItem.isCharged(player.getMainHandItem()),
                    "Completing Crossbow charge must leave the Crossbow in its normal charged state.");

            boolean fired = FightRangedController.tickCrossbow(participant);
            context.assertTrue(fired, "A charged Crossbow must fire through its normal use lifecycle.");
            context.assertFalse(
                    net.minecraft.world.item.CrossbowItem.isCharged(player.getMainHandItem()),
                    "A successfully fired Crossbow must leave its charged state.");

            context.succeed();
        }
        finally
        {
            participant.deactivate();
            target.discard();
        }
    }


    @GameTest(maxTicks = 40)
    public void resetSnapshotRestoresRuntimeStateAfterCombatMutations(GameTestHelper context)
    {
        LivingEntity actor = context.spawn(EntityType.ARMOR_STAND, 3, 1, 3);
        FightParticipant participant =
                new FightParticipant("reset-snapshot", actor, FightParticipant.Side.SOURCE, "RED");

        try
        {
            actor.setItemInHand(InteractionHand.MAIN_HAND, Items.IRON_SWORD.getDefaultInstance());
            actor.setItemInHand(InteractionHand.OFF_HAND, Items.SHIELD.getDefaultInstance());
            actor.setYRot(37.0F);
            actor.setXRot(-12.0F);
            participant.captureResetSnapshot();

            Vec3 initialPosition = participant.getInitialPosition();
            float initialYaw = participant.getInitialYaw();
            float initialPitch = participant.getInitialPitch();
            float initialHealth = participant.getInitialHealth();

            actor.setPos(context.absoluteVec(new Vec3(8.0, 1.0, 8.0)));
            actor.setYRot(180.0F);
            actor.setXRot(25.0F);
            actor.setHealth(1.0F);
            actor.setItemInHand(InteractionHand.MAIN_HAND, Items.STICK.getDefaultInstance());
            actor.setItemInHand(InteractionHand.OFF_HAND, Items.AIR.getDefaultInstance());

            participant.restoreResetSnapshot();

            context.assertTrue(
                    actor.position().distanceTo(initialPosition) < 1.0E-6,
                    "Reset must restore the participant's captured position.");
            context.assertTrue(
                    Math.abs(actor.getYRot() - initialYaw) < 1.0E-6,
                    "Reset must restore the participant's captured yaw.");
            context.assertTrue(
                    Math.abs(actor.getXRot() - initialPitch) < 1.0E-6,
                    "Reset must restore the participant's captured pitch.");
            context.assertTrue(
                    Math.abs(actor.getHealth() - initialHealth) < 1.0E-6,
                    "Reset must restore the participant's captured health.");
            context.assertTrue(
                    actor.getMainHandItem().is(Items.IRON_SWORD),
                    "Reset must restore the captured main-hand item.");
            context.assertTrue(
                    actor.getOffhandItem().is(Items.SHIELD),
                    "Reset must restore the captured off-hand item.");
            context.succeed();
        }
        finally
        {
            actor.discard();
        }
    }


    private static void fillFloor(GameTestHelper context, int minX, int minZ, int maxX, int maxZ)
    {
        for (int x = minX; x <= maxX; x++)
        {
            for (int z = minZ; z <= maxZ; z++)
            {
                context.setBlock(x, 0, z, Blocks.STONE);
            }
        }
    }
}
