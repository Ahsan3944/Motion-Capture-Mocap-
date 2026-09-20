package net.mt1006.mocap.mocap.fight;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class FightFishingRodController
{
	private static final double MAX_RAY_DISTANCE = 32.0;
	private static final Map<UUID, Binding> bindings = new HashMap<>();

	private FightFishingRodController() {}

	public static boolean arm(ServerPlayer player, String fightId, FightParticipant.Side side)
	{
		if (player == null || fightId == null || fightId.isBlank() || side == null) { return false; }
		if (!FightManager.isRunning(fightId)) { return false; }
		bindings.put(player.getUUID(), new Binding(fightId, side));
		return true;
	}

	public static void disarm(ServerPlayer player)
	{
		if (player != null) { bindings.remove(player.getUUID()); }
	}

	public static void clearFight(String fightId)
	{
		if (fightId == null) { return; }
		bindings.entrySet().removeIf(entry -> entry.getValue().fightId().equals(fightId));
	}

	public static void clearAll()
	{
		bindings.clear();
	}

	public static boolean handleBlockUse(Player player, InteractionHand hand, Vec3 destination)
	{
		if (!(player instanceof ServerPlayer serverPlayer) || player.level().isClientSide()) { return false; }
		if (!isFishingRod(serverPlayer, hand)) { return false; }
		return handleDestination(serverPlayer, destination);
	}

	public static boolean handleItemUse(Player player, InteractionHand hand)
	{
		if (!(player instanceof ServerPlayer serverPlayer) || player.level().isClientSide()) { return false; }
		if (!isFishingRod(serverPlayer, hand)) { return false; }

		Binding binding = bindings.get(serverPlayer.getUUID());
		if (binding == null) { return false; }
		if (!FightManager.isRunning(binding.fightId()))
		{
			bindings.remove(serverPlayer.getUUID());
			return false;
		}

		HitResult hit = serverPlayer.pick(MAX_RAY_DISTANCE, 1.0F, false);
		if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK)
		{
			return false;
		}
		return handleDestination(serverPlayer, blockHit.getLocation());
	}

	private static boolean handleDestination(ServerPlayer player, Vec3 destination)
	{
		Binding binding = bindings.get(player.getUUID());
		if (binding == null || destination == null) { return false; }
		if (!FightManager.isRunning(binding.fightId()))
		{
			bindings.remove(player.getUUID());
			return false;
		}
		return FightManager.teleportGroup(binding.fightId(), binding.side(), destination);
	}

	private static boolean isFishingRod(ServerPlayer player, InteractionHand hand)
	{
		return player.getItemInHand(hand).is(Items.FISHING_ROD);
	}

	private record Binding(String fightId, FightParticipant.Side side) {}
}
