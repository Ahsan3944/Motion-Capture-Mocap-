package net.mt1006.mocap.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.mt1006.mocap.mocap.fight.FightEquipmentController;
import net.mt1006.mocap.mocap.fight.FightMovementController;
import net.mt1006.mocap.mocap.fight.FightNavigationController;
import net.mt1006.mocap.mocap.fight.FightParticipant;

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
            double before = actor.distanceToSqr(target);

            context.assertTrue(
                    FightMovementController.chase(participant, target, 20.0, 1.0),
                    "A clear target path must produce a successful chase movement.");

            double after = actor.distanceToSqr(target);
            double moved = actor.position().distanceTo(new Vec3(1.5, 1.0, 2.5));
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
        for (int z = 0; z <= 5; z++)
        {
            for (int y = 1; y <= 2; y++)
            {
                context.setBlock(3, y, z, Blocks.STONE);
            }
        }

        LivingEntity actor = context.spawn(EntityType.ARMOR_STAND, 1, 1, 2);
        LivingEntity target = context.spawn(EntityType.ARMOR_STAND, 6, 1, 2);
        FightParticipant participant = new FightParticipant(
                "movement-recovery", actor, FightParticipant.Side.SOURCE, "RED");

        try
        {
            actor.setNoGravity(true);
            target.setNoGravity(true);
            double startZ = actor.getZ();

            for (int tick = 0; tick < 5; tick++)
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
            first.setItemInHand(InteractionHand.HEAD, Items.IRON_HELMET.getDefaultInstance());
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
            first.setItemInHand(InteractionHand.HEAD, Items.AIR.getDefaultInstance());
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
