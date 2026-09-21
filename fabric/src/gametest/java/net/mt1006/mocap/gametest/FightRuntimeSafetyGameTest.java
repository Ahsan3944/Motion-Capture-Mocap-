package net.mt1006.mocap.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.mt1006.mocap.mocap.fight.FightDefenseController;
import net.mt1006.mocap.mocap.fight.FightGroupController;
import net.mt1006.mocap.mocap.fight.FightParticipant;

import java.util.List;

public final class FightRuntimeSafetyGameTest
{
    @GameTest(maxTicks = 80)
    public void shieldLifecycleUsesNormalBlockingState(GameTestHelper context)
    {
        Player player = context.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(context.absoluteVec(new Vec3(0.5, 1.0, 1.5)));
        LivingEntity target = context.spawn(EntityType.ZOMBIE, 4, 1, 1);
        FightParticipant participant = new FightParticipant("shield-test", player, FightParticipant.Side.SOURCE);

        player.setItemInHand(InteractionHand.OFF_HAND, Items.SHIELD.getDefaultInstance());

        try
        {
            for (int tick = 0; tick < 6; tick++)
            {
                context.assertTrue(
                        FightDefenseController.tick(participant, target),
                        "A nearby target with a shield available should enter normal shield item use.");
                player.tick();
            }

            context.assertTrue(player.isBlocking(), "Shield defense must reach Minecraft's active blocking state.");
            context.assertTrue(player.isUsingItem(), "Shield defense must use the normal item-use lifecycle.");

            FightDefenseController.stop(participant);
            context.assertFalse(player.isBlocking(), "Stopping defense must release the shield.");
            context.assertFalse(player.isUsingItem(), "Stopping defense must end shield item use.");
            context.succeed();
        }
        finally
        {
            FightDefenseController.stop(participant);
            context.kill(target);
        }
    }

    @GameTest(maxTicks = 80)
    public void groupFormationRejectsUnsafeDestinationAtomically(GameTestHelper context)
    {
        for (int x = 0; x <= 8; x++)
        {
            for (int z = 0; z <= 8; z++)
            {
                context.setBlock(x, 0, z, net.minecraft.world.level.block.Blocks.STONE);
            }
        }

        LivingEntity first = context.spawn(EntityType.ARMOR_STAND, 1, 1, 1);
        LivingEntity second = context.spawn(EntityType.ARMOR_STAND, 2, 1, 1);
        FightParticipant firstParticipant =
                new FightParticipant("atomic-first", first, FightParticipant.Side.SOURCE, "RED");
        FightParticipant secondParticipant =
                new FightParticipant("atomic-second", second, FightParticipant.Side.SOURCE, "RED");

        Vec3 firstStart = first.position();
        Vec3 secondStart = second.position();
        Vec3 unsafeDestination = context.absoluteVec(new Vec3(5.5, 2.0, 5.5));

        try
        {
            context.assertFalse(
                    FightGroupController.teleportFormation(
                            List.of(firstParticipant, secondParticipant), unsafeDestination),
                    "A destination without supporting terrain must be rejected.");

            context.assertTrue(
                    first.position().distanceToSqr(firstStart) < 0.000001,
                    "Atomic group teleport must not move the first participant after validation failure.");
            context.assertTrue(
                    second.position().distanceToSqr(secondStart) < 0.000001,
                    "Atomic group teleport must not move the second participant after validation failure.");
            context.succeed();
        }
        finally
        {
            first.discard();
            second.discard();
        }
    }

    @GameTest(maxTicks = 80)
    public void groupFormationTeleportsOnlyTheSelectedParticipants(GameTestHelper context)
    {
        for (int x = 0; x <= 12; x++)
        {
            for (int z = 0; z <= 12; z++)
            {
                context.setBlock(x, 0, z, net.minecraft.world.level.block.Blocks.STONE);
            }
        }

        LivingEntity first = context.spawn(EntityType.ZOMBIE, 1, 1, 1);
        LivingEntity second = context.spawn(EntityType.ZOMBIE, 2, 1, 1);
        LivingEntity third = context.spawn(EntityType.ZOMBIE, 3, 1, 1);
        Vec3 firstStart = first.position();
        Vec3 secondStart = second.position();
        Vec3 thirdStart = third.position();
        LivingEntity unselected = context.spawn(EntityType.ZOMBIE, 10, 1, 10);
        Vec3 unselectedStart = unselected.position();

        FightParticipant firstParticipant =
                new FightParticipant("group-first", first, FightParticipant.Side.SOURCE, "RED");
        FightParticipant secondParticipant =
                new FightParticipant("group-second", second, FightParticipant.Side.SOURCE, "RED");
        FightParticipant thirdParticipant =
                new FightParticipant("group-third", third, FightParticipant.Side.SOURCE, "RED");

        List<FightParticipant> group = List.of(firstParticipant, secondParticipant, thirdParticipant);
        Vec3 destination = context.absoluteVec(new Vec3(6.5, 1.0, 6.5));

        try
        {
            context.assertTrue(
                    FightGroupController.teleportFormation(group, destination),
                    "A valid loaded floor must accept a safe group formation.");

            context.assertTrue(
                    first.position().distanceToSqr(firstStart) > 4.0,
                    "The first participant must move to the requested formation.");
            context.assertTrue(
                    second.position().distanceToSqr(secondStart) > 4.0,
                    "The second participant must move to the requested formation.");
            context.assertTrue(
                    third.position().distanceToSqr(thirdStart) > 4.0,
                    "The third participant must move to the requested formation.");

            context.assertTrue(
                    first.position().distanceToSqr(second.position()) >= 1.0,
                    "Formation slots must keep participants separated.");
            context.assertTrue(
                    first.position().distanceToSqr(third.position()) >= 1.0,
                    "Formation slots must keep participants separated.");
            context.assertTrue(
                    second.position().distanceToSqr(third.position()) >= 1.0,
                    "Formation slots must keep participants separated.");
            context.assertTrue(
                    unselected.position().distanceToSqr(unselectedStart) < 0.000001,
                    "Entities outside the selected group must not be teleported.");

            context.succeed();
        }
        finally
        {
            first.discard();
            second.discard();
            third.discard();
            unselected.discard();
        }
    }
}
