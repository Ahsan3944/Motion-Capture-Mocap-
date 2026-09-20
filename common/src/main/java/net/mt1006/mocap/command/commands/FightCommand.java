package net.mt1006.mocap.command.commands;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.world.phys.Vec3;
import net.mt1006.mocap.api.v1.io.CommandInfo;
import net.mt1006.mocap.command.CommandSuggestions;
import net.mt1006.mocap.command.CommandUtils;
import net.mt1006.mocap.command.io.FullCommandInfo;
import net.mt1006.mocap.mocap.fight.FightDefinition;
import net.mt1006.mocap.mocap.fight.FightManager;
import net.mt1006.mocap.mocap.fight.FightParticipant;

public final class FightCommand
{
	private FightCommand() {}

	public static void add(LiteralArgumentBuilder<CommandSourceStack> playback, CommandBuildContext buildContext)
	{
		playback.then(Commands.literal("fight")
				.then(Commands.literal("list")
						.executes(CommandUtils.command(FightCommand::list)))
				.then(Commands.literal("create")
						.then(Commands.argument("name", StringArgumentType.word())
								.executes(CommandUtils.command(FightCommand::create))))
				.then(Commands.literal("info")
						.then(Commands.argument("name", StringArgumentType.word())
								.suggests(FightCommand::suggestions)
								.executes(CommandUtils.command(FightCommand::info))))
				.then(Commands.literal("set")
						.then(Commands.literal("scene")
								.then(Commands.argument("name", StringArgumentType.word())
										.suggests(FightCommand::suggestions)
										.then(Commands.argument("scene", StringArgumentType.string())
												.suggests(CommandSuggestions::scene)
												.executes(CommandUtils.command(FightCommand::setScene)))))
						.then(Commands.literal("target_player")
								.then(Commands.argument("name", StringArgumentType.word())
										.suggests(FightCommand::suggestions)
										.then(Commands.argument("player", StringArgumentType.word())
												.executes(CommandUtils.command(FightCommand::setTargetPlayer)))))
						.then(Commands.literal("target_scene")
								.then(Commands.argument("name", StringArgumentType.word())
										.suggests(FightCommand::suggestions)
										.then(Commands.argument("scene", StringArgumentType.string())
												.suggests(CommandSuggestions::scene)
												.executes(CommandUtils.command(FightCommand::setTargetScene)))))
						.then(Commands.literal("target_mode")
								.then(Commands.argument("name", StringArgumentType.word())
										.suggests(FightCommand::suggestions)
										.then(Commands.argument("mode", StringArgumentType.word())
												.executes(CommandUtils.command(FightCommand::setTargetMode)))))
						.then(Commands.literal("power")
								.then(Commands.argument("name", StringArgumentType.word())
										.suggests(FightCommand::suggestions)
										.then(Commands.argument("value", IntegerArgumentType.integer(1, 10))
												.executes(CommandUtils.command(FightCommand::setPower)))))
						.then(Commands.literal("attack_speed")
								.then(Commands.argument("name", StringArgumentType.word())
										.suggests(FightCommand::suggestions)
										.then(Commands.argument("value", DoubleArgumentType.doubleArg())
												.executes(CommandUtils.command(FightCommand::setAttackSpeed)))))
						.then(Commands.literal("attack_range")
								.then(Commands.argument("name", StringArgumentType.word())
										.suggests(FightCommand::suggestions)
										.then(Commands.argument("value", DoubleArgumentType.doubleArg())
												.executes(CommandUtils.command(FightCommand::setAttackRange)))))
						.then(Commands.literal("detection_range")
								.then(Commands.argument("name", StringArgumentType.word())
										.suggests(FightCommand::suggestions)
										.then(Commands.argument("value", DoubleArgumentType.doubleArg())
												.executes(CommandUtils.command(FightCommand::setDetectionRange)))))
						.then(Commands.literal("movement_speed")
								.then(Commands.argument("name", StringArgumentType.word())
										.suggests(FightCommand::suggestions)
										.then(Commands.argument("value", DoubleArgumentType.doubleArg())
												.executes(CommandUtils.command(FightCommand::setMovementSpeed)))))
						.then(Commands.literal("damage_multiplier")
								.then(Commands.argument("name", StringArgumentType.word())
										.suggests(FightCommand::suggestions)
										.then(Commands.argument("value", DoubleArgumentType.doubleArg())
												.executes(CommandUtils.command(FightCommand::setDamageMultiplier)))))
						.then(Commands.literal("knockback_multiplier")
								.then(Commands.argument("name", StringArgumentType.word())
										.suggests(FightCommand::suggestions)
										.then(Commands.argument("value", DoubleArgumentType.doubleArg())
												.executes(CommandUtils.command(FightCommand::setKnockbackMultiplier)))))
						.then(Commands.literal("clear_targets")
								.then(Commands.argument("name", StringArgumentType.word())
										.suggests(FightCommand::suggestions)
										.executes(CommandUtils.command(FightCommand::clearTargets)))))
				.then(Commands.literal("start")
						.then(Commands.argument("name", StringArgumentType.word())
								.suggests(FightCommand::suggestions)
								.executes(CommandUtils.command(FightCommand::start))))
				.then(Commands.literal("stop")
						.then(Commands.argument("name", StringArgumentType.word())
								.suggests(FightCommand::suggestions)
								.executes(CommandUtils.command(FightCommand::stop))))
				.then(Commands.literal("reset")
						.then(Commands.argument("name", StringArgumentType.word())
								.suggests(FightCommand::suggestions)
								.executes(CommandUtils.command(FightCommand::reset))))
				.then(Commands.literal("remove")
						.then(Commands.argument("name", StringArgumentType.word())
								.suggests(FightCommand::suggestions)
								.executes(CommandUtils.command(FightCommand::remove))))
				.then(Commands.literal("control")
						.then(Commands.literal("rod")
							.then(Commands.literal("arm")
									.then(Commands.argument("name", StringArgumentType.word())
											.suggests(FightCommand::suggestions)
											.then(Commands.argument("side", StringArgumentType.word())
													.suggests(FightCommand::sideSuggestions)
													.executes(CommandUtils.command(FightCommand::armRod)))))
							.then(Commands.literal("disarm")
								.executes(CommandUtils.command(FightCommand::disarmRod))))
						.then(Commands.literal("group")
								.then(Commands.literal("teleport")
										.then(Commands.argument("name", StringArgumentType.word())
												.suggests(FightCommand::suggestions)
												.then(Commands.argument("side", StringArgumentType.word())
														.suggests(FightCommand::sideSuggestions)
														.then(Commands.argument("x", DoubleArgumentType.doubleArg())
															.then(Commands.argument("y", DoubleArgumentType.doubleArg())
																.then(Commands.argument("z", DoubleArgumentType.doubleArg())
																	.executes(CommandUtils.command(FightCommand::teleportGroup)))))))))
				));
	}

	private static boolean list(CommandInfo info)
	{
		FightManager.ensureLoaded();
		if (FightManager.definitions().isEmpty()) { return info.sendSuccessLiteral("No Fight definitions exist."); }
		info.sendSuccessLiteral("Fight definitions:");
		FightManager.definitions().forEach(f -> info.sendSuccessLiteral("- %s", FightManager.describe(f)));
		return true;
	}

	private static boolean create(FullCommandInfo info)
	{
		return FightManager.create(info, info.getString("name"));
	}

	private static boolean info(FullCommandInfo info)
	{
		FightDefinition definition = FightManager.get(info.getString("name"));
		return definition != null
				? info.sendSuccessLiteral(FightManager.describe(definition))
				: info.sendFailure("Fight not found: " + info.getString("name"));
	}

	private static boolean setScene(FullCommandInfo info)
	{
		return FightManager.setSourceScene(info, info.getString("name"), info.getString("scene"));
	}

	private static boolean setTargetPlayer(FullCommandInfo info)
	{
		return FightManager.setTargetPlayer(info, info.getString("name"), info.getString("player"));
	}

	private static boolean setTargetScene(FullCommandInfo info)
	{
		return FightManager.setTargetScene(info, info.getString("name"), info.getString("scene"));
	}

	private static boolean setTargetMode(FullCommandInfo info)
	{
		return FightManager.setTargetMode(info, info.getString("name"), info.getString("mode"));
	}

	private static boolean setPower(FullCommandInfo info)
	{
		return FightManager.setPower(info, info.getString("name"), info.getInteger("value"));
	}

	private static boolean setAttackSpeed(FullCommandInfo info)
	{
		return FightManager.setAttackSpeed(info, info.getString("name"), info.getDouble("value"));
	}

	private static boolean setAttackRange(FullCommandInfo info)
	{
		return FightManager.setAttackRange(info, info.getString("name"), info.getDouble("value"));
	}

	private static boolean setDetectionRange(FullCommandInfo info)
	{
		return FightManager.setDetectionRange(info, info.getString("name"), info.getDouble("value"));
	}

	private static boolean setMovementSpeed(FullCommandInfo info)
	{
		return FightManager.setMovementSpeed(info, info.getString("name"), info.getDouble("value"));
	}

	private static boolean setDamageMultiplier(FullCommandInfo info)
	{
		return FightManager.setDamageMultiplier(info, info.getString("name"), info.getDouble("value"));
	}

	private static boolean setKnockbackMultiplier(FullCommandInfo info)
	{
		return FightManager.setKnockbackMultiplier(info, info.getString("name"), info.getDouble("value"));
	}

	private static boolean clearTargets(FullCommandInfo info)
	{
		return FightManager.clearTargets(info, info.getString("name"));
	}

	private static boolean start(FullCommandInfo info)
	{
		return FightManager.start(info, info.getString("name"));
	}

	private static boolean stop(FullCommandInfo info)
	{
		return FightManager.stop(info, info.getString("name"));
	}

	private static boolean reset(FullCommandInfo info)
	{
		return FightManager.reset(info, info.getString("name"));
	}

	private static boolean remove(FullCommandInfo info)
	{
		return FightManager.remove(info, info.getString("name"));
	}

	private static boolean armRod(FullCommandInfo info)
	{
		return FightManager.armFishingRod(info, info.getString("name"), parseSide(info));
	}

	private static boolean disarmRod(FullCommandInfo info)
	{
		return FightManager.disarmFishingRod(info);
	}

	private static boolean teleportGroup(FullCommandInfo info)
	{
		Vec3 destination = new Vec3(info.getDouble("x"), info.getDouble("y"), info.getDouble("z"));
		return FightManager.teleportGroup(info, info.getString("name"), parseSide(info), destination);
	}

	private static FightParticipant.Side parseSide(FullCommandInfo info)
	{
		String value = info.getString("side");
		try
		{
			return FightParticipant.Side.valueOf(value.toUpperCase(java.util.Locale.ROOT));
		}
		catch (IllegalArgumentException e)
		{
			info.sendFailure("Unknown Fight side: " + value + ". Use SOURCE or TARGET.");
			return null;
		}
	}

	private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> sideSuggestions(
			com.mojang.brigadier.context.CommandContext<?> ctx,
			com.mojang.brigadier.suggestion.SuggestionsBuilder builder)
	{
		builder.suggest("SOURCE");
		builder.suggest("TARGET");
		return builder.buildFuture();
	}

	private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestions(
			com.mojang.brigadier.context.CommandContext<?> ctx,
			com.mojang.brigadier.suggestion.SuggestionsBuilder builder)
	{
		String remaining = builder.getRemaining();
		FightManager.definitions().forEach(f -> {
			if (f.getId().startsWith(remaining)) { builder.suggest(f.getId()); }
		});
		return builder.buildFuture();
	}
}
