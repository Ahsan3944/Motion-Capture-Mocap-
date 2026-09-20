package net.mt1006.mocap.command.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.mt1006.mocap.api.v1.io.CommandInfo;
import net.mt1006.mocap.command.CommandSuggestions;
import net.mt1006.mocap.command.CommandUtils;
import net.mt1006.mocap.mocap.fight.FightDefinition;
import net.mt1006.mocap.mocap.fight.FightManager;

public final class FightCommand
{
	private FightCommand() {}

	public static void add(LiteralArgumentBuilder<CommandSourceStack> playback, CommandBuildContext buildContext)
	{
		playback.then(Commands.literal("fight")
				.then(Commands.literal("list").executes(CommandUtils.command(FightCommand::list)))
				.then(Commands.literal("create")
						.then(Commands.argument("name", StringArgumentType.word())
								.executes(CommandUtils.command(FightCommand::create))))
				.then(Commands.literal("info")
						.then(Commands.argument("name", StringArgumentType.word())
								.suggests(FightCommand::suggestions)
								.executes(CommandUtils.command(FightCommand::info))))
				.then(Commands.literal("set")
					.then(Commands.literal("scene")
							.then(Commands.argument("name", StringArgumentType.word()).suggests(FightCommand::suggestions)
								.then(Commands.argument("scene", StringArgumentType.string()).suggests(CommandSuggestions::scene)
									.executes(CommandUtils.command(FightCommand::setScene))))))
					.then(Commands.literal("target_player")
							.then(Commands.argument("name", StringArgumentType.word()).suggests(FightCommand::suggestions)
									.then(Commands.argument("player", StringArgumentType.word())
										.executes(CommandUtils.command(FightCommand::setTargetPlayer)))))
					.then(Commands.literal("clear_targets")
							.then(Commands.argument("name", StringArgumentType.word()).suggests(FightCommand::suggestions)
								.executes(CommandUtils.command(FightCommand::clearTargets))))
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
								.executes(CommandUtils.command(FightCommand::remove))));
	}

	private static boolean list(CommandInfo info)
	{
		FightManager.ensureLoaded();
		if (FightManager.definitions().isEmpty()) { return info.sendSuccessLiteral("No Fight definitions exist."); }
		info.sendSuccessLiteral("Fight definitions:");
		FightManager.definitions().forEach(f -> info.sendSuccessLiteral("- %s", FightManager.describe(f)));
		return true;
	}

	private static boolean create(CommandInfo info)
	{
		return FightManager.create(info, info.getString("name"));
	}

	private static boolean info(CommandInfo info)
	{
		FightDefinition definition = FightManager.get(info.getString("name"));
		return definition != null
				? info.sendSuccessLiteral(FightManager.describe(definition))
				: info.sendFailure("Fight not found: " + info.getString("name"));
	}

	private static boolean setScene(CommandInfo info)
	{
		return FightManager.setSourceScene(info, info.getString("name"), info.getString("scene"));
	}

	private static boolean setTargetPlayer(CommandInfo info)
	{
		return FightManager.setTargetPlayer(info, info.getString("name"), info.getString("player"));
	}

	private static boolean clearTargets(CommandInfo info)
	{
		return FightManager.clearTargets(info, info.getString("name"));
	}

	private static boolean start(CommandInfo info)
	{
		return FightManager.start(info, info.getString("name"));
	}

	private static boolean stop(CommandInfo info)
	{
		return FightManager.stop(info, info.getString("name"));
	}

	private static boolean reset(CommandInfo info)
	{
		return FightManager.reset(info, info.getString("name"));
	}

	private static boolean remove(CommandInfo info)
	{
		return FightManager.remove(info, info.getString("name"));
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
