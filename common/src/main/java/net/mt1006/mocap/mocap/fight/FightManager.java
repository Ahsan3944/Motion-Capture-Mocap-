package net.mt1006.mocap.mocap.fight;

import net.mt1006.mocap.MocapMod;
import net.mt1006.mocap.api.v1.io.CommandInfo;
import net.mt1006.mocap.api.v1.io.CommandOutput;
import net.mt1006.mocap.api.v1.controller.MocapPlaybackRoot;
import net.mt1006.mocap.api.v1.controller.config.MocapPlaybackConfig;
import net.mt1006.mocap.api.v1.controller.playable.MocapPlayable;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.mt1006.mocap.mocap.files.Files;
import net.mt1006.mocap.mocap.actions.Swing;
import net.mt1006.mocap.mocap.playing.playback.PlaybackRoot;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

public final class FightManager
{
	public static final String FILE_EXTENSION = ".mcmocap_fight";
	private static final int FORMAT_VERSION = FightDefinition.CURRENT_VERSION;
	private static final Map<String, FightDefinition> definitions = new LinkedHashMap<>();
	private static final Map<String, FightRuntime> active = new LinkedHashMap<>();
	private static final Map<UUID, String> participantOwners = new HashMap<>();
	private static boolean loaded = false;

	private FightManager() {}

	public static void ensureLoaded()
	{
		if (loaded) { return; }
		loaded = true;
		definitions.clear();

		if (!Files.initialized || Files.fightDirectory == null) { return; }
		File[] files = Files.fightDirectory.listFiles((dir, name) -> name.endsWith(FILE_EXTENSION));
		if (files == null) { return; }

		for (File file : files)
		{
			FightDefinition definition = loadFile(file);
			if (definition != null) { definitions.put(definition.getId(), definition); }
		}
	}

	public static Collection<FightDefinition> definitions()
	{
		ensureLoaded();
		return Collections.unmodifiableCollection(definitions.values());
	}

	public static @Nullable FightDefinition get(String id)
	{
		ensureLoaded();
		return definitions.get(id);
	}

	public static boolean isRunning(String id)
	{
		return id != null && active.containsKey(id);
	}

	public static boolean teleportGroup(String id, FightParticipant.Side side, Vec3 destination)
	{
		FightRuntime runtime = active.get(id);
		if (runtime == null || side == null || destination == null) { return false; }
		if (!Double.isFinite(destination.x) || !Double.isFinite(destination.y) || !Double.isFinite(destination.z)) { return false; }
		return runtime.teleportGroup(side, destination);
	}

	public static boolean armFishingRod(CommandInfo out, String id, FightParticipant.Side side)
	{
		if (out.getSourcePlayer() == null)
		{
			return out.sendFailure("Fishing Rod control requires a player command source.");
		}
		if (id == null || id.isBlank() || !isRunning(id))
		{
			return out.sendFailure("Fight is not running: " + id);
		}
		if (side == null)
		{
			return out.sendFailure("Fight side is required.");
		}
		if (!FightFishingRodController.arm(out.getSourcePlayer(), id, side))
		{
			return out.sendFailure("Failed to arm Fishing Rod control for Fight '" + id + "'.");
		}
		return out.sendSuccessLiteral("Armed Fishing Rod control for Fight '%s' side %s.", id, side);
	}

	public static boolean disarmFishingRod(CommandInfo out)
	{
		if (out.getSourcePlayer() == null)
		{
			return out.sendFailure("Fishing Rod control requires a player command source.");
		}
		FightFishingRodController.disarm(out.getSourcePlayer());
		return out.sendSuccessLiteral("Disarmed Fishing Rod control.");
	}

	public static boolean teleportGroup(CommandInfo out, String id, FightParticipant.Side side, Vec3 destination)
	{
		if (!isRunning(id))
		{
			return out.sendFailure("Fight is not running: " + id);
		}
		if (side == null)
		{
			return out.sendFailure("Fight side is required.");
		}
		if (destination == null || !Double.isFinite(destination.x) || !Double.isFinite(destination.y) || !Double.isFinite(destination.z))
		{
			return out.sendFailure("Teleport destination must contain finite coordinates.");
		}
		if (!teleportGroup(id, side, destination))
		{
			return out.sendFailure("Group teleport failed; no participants were moved.");
		}
		return out.sendSuccessLiteral("Teleported Fight '%s' side %s.", id, side);
	}

	public static boolean create(CommandOutput out, String id)
	{
		ensureLoaded();
		if (!isValidId(id) || definitions.containsKey(id))
		{
			return out.sendFailure("Fight already exists or the name is invalid: " + id);
		}

		FightDefinition definition = new FightDefinition(id);
		definitions.put(id, definition);
		if (!save(definition))
		{
			definitions.remove(id);
			return out.sendFailure("Failed to save Fight definition: " + id);
		}
		return out.sendSuccessLiteral("Created Fight '%s'.", id);
	}

	public static boolean remove(CommandOutput out, String id)
	{
		ensureLoaded();
		if (active.containsKey(id)) { return out.sendFailure("Stop the Fight before removing it: " + id); }
		FightDefinition definition = definitions.remove(id);
		if (definition == null) { return out.sendFailure("Fight not found: " + id); }

		FightFishingRodController.clearFight(id);
		if (!deleteFile(id))
		{
			definitions.put(id, definition);
			return out.sendFailure("Failed to remove Fight file: " + id);
		}
		return out.sendSuccessLiteral("Removed Fight '%s'.", id);
	}

	public static boolean setSourceScene(CommandOutput out, String id, String scene)
	{
		ensureLoaded();
		FightDefinition definition = definitions.get(id);
		if (definition == null) { return out.sendFailure("Fight not found: " + id); }
		if (scene == null || scene.isBlank()) { return out.sendFailure("Source scene cannot be empty."); }
		List<String> scenes = new ArrayList<>(definition.getSourceScenes());
		if (!scenes.contains(scene)) { scenes.add(scene); }
		definition.setSourceScenes(scenes);
		return save(definition) && out.sendSuccessLiteral("Added source scene '%s' to Fight '%s'.", scene, id);
	}

	public static boolean setTargetPlayer(CommandOutput out, String id, String player)
	{
		ensureLoaded();
		FightDefinition definition = definitions.get(id);
		if (definition == null) { return out.sendFailure("Fight not found: " + id); }
		List<String> players = new ArrayList<>(definition.getTargetPlayers());
		if (!players.contains(player)) { players.add(player); }
		definition.setTargetPlayers(players);
		return save(definition) && out.sendSuccessLiteral("Added target player '%s' to Fight '%s'.", player, id);
	}

	public static boolean setSourceSceneTeam(CommandOutput out, String id, String scene, String team)
	{
		ensureLoaded();
		FightDefinition definition = definitions.get(id);
		if (definition == null) { return out.sendFailure("Fight not found: " + id); }
		if (active.containsKey(id)) { return out.sendFailure("Stop the Fight before changing team assignments: " + id); }
		if (!definition.setSourceSceneTeam(scene, team))
		{
			return out.sendFailure("Invalid source scene or team ID for Fight '" + id + "'.");
		}
		return save(definition) && out.sendSuccessLiteral("Set source scene '%s' team to '%s'.", scene, team);
	}

	public static boolean setTargetScene(CommandOutput out, String id, String scene)
	{
		ensureLoaded();
		FightDefinition definition = definitions.get(id);
		if (definition == null) { return out.sendFailure("Fight not found: " + id); }
		if (scene == null || scene.isBlank()) { return out.sendFailure("Target scene cannot be empty."); }
		List<String> scenes = new ArrayList<>(definition.getTargetScenes());
		if (!scenes.contains(scene)) { scenes.add(scene); }
		definition.setTargetScenes(scenes);
		return save(definition) && out.sendSuccessLiteral("Added target scene '%s' to Fight '%s'.", scene, id);
	}

	public static boolean setTargetSceneTeam(CommandOutput out, String id, String scene, String team)
	{
		ensureLoaded();
		FightDefinition definition = definitions.get(id);
		if (definition == null) { return out.sendFailure("Fight not found: " + id); }
		if (active.containsKey(id)) { return out.sendFailure("Stop the Fight before changing team assignments: " + id); }
		if (!definition.setTargetSceneTeam(scene, team))
		{
			return out.sendFailure("Invalid target scene or team ID for Fight '" + id + "'.");
		}
		return save(definition) && out.sendSuccessLiteral("Set target scene '%s' team to '%s'.", scene, team);
	}

	public static boolean setTargetPlayerTeam(CommandOutput out, String id, String player, String team)
	{
		ensureLoaded();
		FightDefinition definition = definitions.get(id);
		if (definition == null) { return out.sendFailure("Fight not found: " + id); }
		if (active.containsKey(id)) { return out.sendFailure("Stop the Fight before changing team assignments: " + id); }
		if (!definition.setTargetPlayerTeam(player, team))
		{
			return out.sendFailure("Invalid target player or team ID for Fight '" + id + "'.");
		}
		return save(definition) && out.sendSuccessLiteral("Set target player '%s' team to '%s'.", player, team);
	}

	public static boolean setTargetMode(CommandOutput out, String id, String mode)
	{
		ensureLoaded();
		FightDefinition definition = definitions.get(id);
		if (definition == null) { return out.sendFailure("Fight not found: " + id); }
		try
		{
			definition.setTargetMode(FightDefinition.TargetMode.valueOf(mode.toUpperCase(java.util.Locale.ROOT)));
		}
		catch (IllegalArgumentException e)
		{
			return out.sendFailure("Unknown target mode: " + mode);
		}
		return save(definition) && out.sendSuccessLiteral("Set target mode for Fight '%s' to %s.", id, definition.getTargetMode());
	}

	public static boolean setPower(CommandOutput out, String id, int value)
	{
		ensureLoaded();
		FightDefinition definition = definitions.get(id);
		if (definition == null) { return out.sendFailure("Fight not found: " + id); }
		if (!definition.setPower(value)) { return out.sendFailure("Power must be between 1 and 10."); }
		return save(definition) && out.sendSuccessLiteral("Set power for Fight '%s' to %d.", id, value);
	}

	public static boolean setAttackSpeed(CommandOutput out, String id, double value)
	{
		ensureLoaded();
		FightDefinition definition = definitions.get(id);
		if (definition == null) { return out.sendFailure("Fight not found: " + id); }
		if (!definition.setAttackSpeed(value)) { return out.sendFailure("Attack speed must be finite and greater than 0."); }
		return save(definition) && out.sendSuccessLiteral("Set attack speed for Fight '%s' to %.3f.", id, value);
	}

	public static boolean setAttackRange(CommandOutput out, String id, double value)
	{
		ensureLoaded();
		FightDefinition definition = definitions.get(id);
		if (definition == null) { return out.sendFailure("Fight not found: " + id); }
		if (!definition.setAttackRange(value)) { return out.sendFailure("Attack range must be finite and greater than 0."); }
		return save(definition) && out.sendSuccessLiteral("Set attack range for Fight '%s' to %.2f.", id, value);
	}

	public static boolean setDetectionRange(CommandOutput out, String id, double value)
	{
		ensureLoaded();
		FightDefinition definition = definitions.get(id);
		if (definition == null) { return out.sendFailure("Fight not found: " + id); }
		if (!definition.setDetectionRange(value)) { return out.sendFailure("Detection range must be finite and greater than 0."); }
		return save(definition) && out.sendSuccessLiteral("Set detection range for Fight '%s' to %.2f.", id, value);
	}

	public static boolean setMovementSpeed(CommandOutput out, String id, double value)
	{
		ensureLoaded();
		FightDefinition definition = definitions.get(id);
		if (definition == null) { return out.sendFailure("Fight not found: " + id); }
		if (!definition.setMovementSpeed(value)) { return out.sendFailure("Movement speed must be finite and non-negative."); }
		return save(definition) && out.sendSuccessLiteral("Set movement speed for Fight '%s' to %.2f.", id, value);
	}

	public static boolean setDamageMultiplier(CommandOutput out, String id, double value)
	{
		ensureLoaded();
		FightDefinition definition = definitions.get(id);
		if (definition == null) { return out.sendFailure("Fight not found: " + id); }
		if (!definition.setDamageMultiplier(value)) { return out.sendFailure("Damage multiplier must be finite and non-negative."); }
		return save(definition) && out.sendSuccessLiteral("Set damage multiplier for Fight '%s' to %.2f.", id, value);
	}

	public static boolean setKnockbackMultiplier(CommandOutput out, String id, double value)
	{
		ensureLoaded();
		FightDefinition definition = definitions.get(id);
		if (definition == null) { return out.sendFailure("Fight not found: " + id); }
		if (!definition.setKnockbackMultiplier(value)) { return out.sendFailure("Knockback multiplier must be finite and non-negative."); }
		return save(definition) && out.sendSuccessLiteral("Set knockback multiplier for Fight '%s' to %.2f.", id, value);
	}

	public static boolean clearTargets(CommandOutput out, String id)
	{
		ensureLoaded();
		FightDefinition definition = definitions.get(id);
		if (definition == null) { return out.sendFailure("Fight not found: " + id); }
		definition.setTargetPlayers(List.of());
		definition.setTargetScenes(List.of());
		return save(definition) && out.sendSuccessLiteral("Cleared targets for Fight '%s'.", id);
	}

	public static boolean start(CommandInfo out, String id)
	{
		ensureLoaded();
		FightDefinition definition = definitions.get(id);
		if (definition == null) { return out.sendFailure("Fight not found: " + id); }
		if (active.containsKey(id)) { return out.sendFailure("Fight is already running: " + id); }
		if (definition.getSourceScenes().isEmpty())
		{
			return out.sendFailure("Fight has no source scene configured: " + id);
		}
		if (definition.getTargetScenes().isEmpty() && definition.getTargetPlayers().isEmpty())
		{
			return out.sendFailure("Fight has no target configured: " + id);
		}
		FightRuntime runtime;
		try { runtime = new FightRuntime(definition, out); }
		catch (Exception e) { MocapMod.LOGGER.error("Failed to initialize Fight '{}' .", id, e); return out.sendFailure("Failed to initialize Fight '" + id + "'."); }
		if (runtime.isEmpty())
		{
			runtime.reset();
			return out.sendFailure("Fight has no runtime actors: " + id);
		}
		active.put(id, runtime);
		definition.setState(FightDefinition.State.RUNNING);
		save(definition);
		return out.sendSuccessLiteral("Started Fight '%s'.", id);
	}

	public static boolean stop(CommandOutput out, String id)
	{
		ensureLoaded();
		FightRuntime runtime = active.remove(id);
		FightDefinition definition = definitions.get(id);
		if (definition == null) { return out.sendFailure("Fight not found: " + id); }
		if (runtime == null) { return out.sendFailure("Fight is not running: " + id); }
		FightFishingRodController.clearFight(id);
		runtime.reset();

		definition.setState(FightDefinition.State.STOPPED);
		save(definition);
		return out.sendSuccessLiteral("Stopped Fight '%s'.", id);
	}

	public static boolean reset(CommandOutput out, String id)
	{
		ensureLoaded();
		FightRuntime runtime = active.remove(id);
		FightDefinition definition = definitions.get(id);
		if (definition == null) { return out.sendFailure("Fight not found: " + id); }

		if (runtime != null) { runtime.reset(); }
		FightFishingRodController.clearFight(id);
		definition.setState(FightDefinition.State.STOPPED);
		save(definition);
		return out.sendSuccessLiteral("Reset Fight '%s'.", id);
	}

	public static void tick()
	{
		ensureLoaded();
		if (active.isEmpty()) { return; }

		List<String> failed = new ArrayList<>();
		for (Map.Entry<String, FightRuntime> entry : active.entrySet())
		{
			try
			{
				entry.getValue().tick(definitions.get(entry.getKey()));
			}
			catch (Exception e)
			{
				failed.add(entry.getKey());
				MocapMod.LOGGER.error("Fight '{}' failed during runtime tick; isolating it.", entry.getKey(), e);
			}
		}

		for (String id : failed)
		{
			FightRuntime runtime = active.remove(id);
			if (runtime != null)
			{
				try { runtime.reset(); }
				catch (Exception e) { MocapMod.LOGGER.error("Failed to reset isolated Fight runtime '{}'.", id, e); }
			}
			FightDefinition definition = definitions.get(id);
			if (definition != null) { definition.setState(FightDefinition.State.STOPPED); save(definition); }
		}
	}

	public static void stopAll()
	{
		for (FightRuntime runtime : active.values())
		{
			try { runtime.reset(); }
			catch (Exception e) { MocapMod.LOGGER.error("Failed to reset Fight runtime.", e); }
		}
		active.clear();
		participantOwners.clear();
		for (FightDefinition definition : definitions.values())
		{
			definition.setState(FightDefinition.State.STOPPED);
			save(definition);
		}
	}

	public static String describe(FightDefinition definition)
	{
		return String.format("Fight '%s': state=%s, sourceScenes=%s, sourceTeams=%s, targetPlayers=%s, targetPlayerTeams=%s, targetScenes=%s, targetTeams=%s, power=%d, attackSpeed=%.3f, attackRange=%.2f, detectionRange=%.2f, movementSpeed=%.2f, damage=%.2f, knockback=%.2f, targetMode=%s",
				definition.getId(), definition.getState(), definition.getSourceScenes(), definition.getSourceSceneTeams(),
				definition.getTargetPlayers(), definition.getTargetPlayerTeams(), definition.getTargetScenes(), definition.getTargetSceneTeams(), definition.getPower(), definition.getAttackSpeed(), definition.getAttackRange(),
				definition.getDetectionRange(), definition.getMovementSpeed(), definition.getDamageMultiplier(),
				definition.getKnockbackMultiplier(), definition.getTargetMode());
	}

	private static boolean save(FightDefinition definition)
	{
		if (!Files.initialized || Files.fightDirectory == null) { return false; }
		Properties p = new Properties();
		p.setProperty("format_version", Integer.toString(FORMAT_VERSION));
		p.setProperty("id", definition.getId());
		p.setProperty("source_scenes", String.join(",", definition.getSourceScenes()));
		p.setProperty("target_players", String.join(",", definition.getTargetPlayers()));
		p.setProperty("target_scenes", String.join(",", definition.getTargetScenes()));
		p.setProperty("source_scene_teams", String.join(",", definition.getSourceSceneTeams()));
		p.setProperty("target_scene_teams", String.join(",", definition.getTargetSceneTeams()));
		p.setProperty("target_player_teams", String.join(",", definition.getTargetPlayerTeams()));
		p.setProperty("power", Integer.toString(definition.getPower()));
		p.setProperty("attack_speed", Double.toString(definition.getAttackSpeed()));
		p.setProperty("attack_range", Double.toString(definition.getAttackRange()));
		p.setProperty("detection_range", Double.toString(definition.getDetectionRange()));
		p.setProperty("movement_speed", Double.toString(definition.getMovementSpeed()));
		p.setProperty("damage_multiplier", Double.toString(definition.getDamageMultiplier()));
		p.setProperty("knockback_multiplier", Double.toString(definition.getKnockbackMultiplier()));
		p.setProperty("target_mode", definition.getTargetMode().name());

		File file = new File(Files.fightDirectory, definition.getId() + FILE_EXTENSION);
		File tmp = new File(Files.fightDirectory, definition.getId() + FILE_EXTENSION + ".tmp");
		try (FileOutputStream stream = new FileOutputStream(tmp))
		{
			p.store(stream, "MoCap Fight definition");
			if (!tmp.renameTo(file))
			{
				if (file.exists() && !file.delete()) { return false; }
				return tmp.renameTo(file);
			}
			return true;
		}
		catch (Exception e)
		{
			MocapMod.LOGGER.error("Failed to save Fight '{}'.", definition.getId(), e);
			return false;
		}
		finally
		{
			if (tmp.exists()) { tmp.delete(); }
		}
	}

	private static @Nullable FightDefinition loadFile(File file)
	{
		Properties p = new Properties();
		try (FileInputStream stream = new FileInputStream(file))
		{
			p.load(stream);
			int version = Integer.parseInt(p.getProperty("format_version", "0"));
			if (version < 1 || version > FORMAT_VERSION) { return null; }

			String id = p.getProperty("id", "");
			if (!isValidId(id)) { return null; }

			FightDefinition definition = new FightDefinition(id);
			definition.setSourceScenes(splitList(p.getProperty("source_scenes", "")));
			definition.setTargetPlayers(splitList(p.getProperty("target_players", "")));
			definition.setTargetScenes(splitList(p.getProperty("target_scenes", "")));
			definition.setSourceSceneTeams(splitList(p.getProperty("source_scene_teams", "")));
			definition.setTargetSceneTeams(splitList(p.getProperty("target_scene_teams", "")));
			definition.setTargetPlayerTeams(splitList(p.getProperty("target_player_teams", "")));
			if (!definition.setPower(Integer.parseInt(p.getProperty("power", "5")))) { return null; }
			if (!definition.setAttackSpeed(Double.parseDouble(p.getProperty("attack_speed", "1.0")))) { return null; }
			if (!definition.setAttackRange(Double.parseDouble(p.getProperty("attack_range", "3.0")))) { return null; }
			if (!definition.setDetectionRange(Double.parseDouble(p.getProperty("detection_range", "16.0")))) { return null; }
			if (!definition.setMovementSpeed(Double.parseDouble(p.getProperty("movement_speed", "1.0")))) { return null; }
			if (!definition.setDamageMultiplier(Double.parseDouble(p.getProperty("damage_multiplier", "1.0")))) { return null; }
			if (!definition.setKnockbackMultiplier(Double.parseDouble(p.getProperty("knockback_multiplier", "1.0")))) { return null; }
			definition.setTargetMode(FightDefinition.TargetMode.valueOf(p.getProperty("target_mode", "NEAREST")));
			return definition;
		}
		catch (Exception e)
		{
			MocapMod.LOGGER.warn("Ignoring invalid Fight file '{}'.", file.getName(), e);
			return null;
		}
	}

	private static List<String> splitList(String value)
	{
		if (value == null || value.isBlank()) { return List.of(); }
		List<String> result = new ArrayList<>();
		for (String entry : value.split(","))
		{
			if (!entry.isBlank()) { result.add(entry.trim()); }
		}
		return result;
	}

	private static boolean deleteFile(String id)
	{
		if (!Files.initialized || Files.fightDirectory == null) { return false; }
		File file = new File(Files.fightDirectory, id + FILE_EXTENSION);
		return !file.exists() || file.delete();
	}

	private static boolean isValidId(String id)
	{
		if (id == null || id.isEmpty() || id.startsWith(".") || id.startsWith("-")) { return false; }
		for (char c : id.toCharArray())
		{
			if (!Files.isAllowedInInputName(c)) { return false; }
		}
		return true;
	}

	private static final class FightRuntime
	{
		private final String id;
		private final MinecraftServer server;
		private final List<MocapPlaybackRoot> playbackRoots = new ArrayList<>();
		private final List<FightParticipant> participants = new ArrayList<>();

		private FightRuntime(FightDefinition definition, CommandInfo info)
		{
			this.id = definition.getId();
			this.server = info.getServer();
			MocapPlaybackConfig baseConfig = MocapPlaybackConfig.createFromSettings();
			baseConfig.setInvulnerablePlayback(false);
			try
			{
				startSources(definition, info, baseConfig);
				startTargets(definition, info, baseConfig);
				if (participants.isEmpty()) { throw new IllegalStateException("Fight created no runtime participants."); }
				takeEquipmentOwnership();
			}
			catch (RuntimeException e)
			{
				reset();
				throw e;
			}
		}

		private void startSources(FightDefinition definition, CommandInfo info, MocapPlaybackConfig baseConfig)
		{
			int index = 0;
			for (String source : definition.getSourceScenes())
			{
				startPlayable(info, source, baseConfig, FightParticipant.Side.SOURCE, "source-" + index++, definition.getSourceSceneTeam(source));
			}
		}

		private void startTargets(FightDefinition definition, CommandInfo info, MocapPlaybackConfig baseConfig)
		{
			int index = 0;
			for (String target : definition.getTargetScenes())
			{
				startPlayable(info, target, baseConfig, FightParticipant.Side.TARGET, "target-" + index++, definition.getTargetSceneTeam(target));
			}
		}

		private void startPlayable(CommandInfo info, String source, MocapPlaybackConfig baseConfig,
				FightParticipant.Side side, String idPrefix, String teamId)
		{
			MocapPlayable playable = MocapPlayable.get(info, source);
			if (playable == null) { throw new IllegalArgumentException("Unknown Fight source/target: " + source); }

			MocapPlaybackConfig config = baseConfig.copy();
			config.setInvulnerablePlayback(false);
			MocapPlaybackRoot root = playable.startPlayback(info,
					net.mt1006.mocap.api.v1.modifiers.MocapModifiers.empty(), config, true);
			if (root == null) { throw new IllegalStateException("Playback failed: " + source); }

			playbackRoots.add(root);
			if (!(root instanceof PlaybackRoot playbackRoot))
			{
				throw new IllegalStateException("Unsupported playback root implementation.");
			}

			playbackRoot.setMovementControlled(true);
			List<Entity> entities = playbackRoot.getControlledEntities();
			for (int i = 0; i < entities.size(); i++)
			{
				Entity entity = entities.get(i);
				if (entity == null || !entity.isAlive()) { continue; }
				if (!(entity instanceof LivingEntity))
				{
					throw new IllegalStateException("Fight participant is not a LivingEntity.");
				}
				if (!claimParticipant(entity.getUUID(), id))
				{
					throw new IllegalStateException("Entity is already controlled by another Fight: " + entity.getUUID());
				}
				participants.add(new FightParticipant(idPrefix + "-" + i, entity, side, teamId));
			}
		}

		private void takeEquipmentOwnership()
		{
			// Execute the first playback tick before suppressing ChangeItem so each participant
			// captures the recording's initialized equipment rather than an empty spawn state.
			for (MocapPlaybackRoot root : playbackRoots)
			{
				if (root instanceof PlaybackRoot playbackRoot) { playbackRoot.tick(); }
			}
			for (FightParticipant participant : participants) { participant.captureEquipmentSnapshot(); }
			for (MocapPlaybackRoot root : playbackRoots)
			{
				if (root instanceof PlaybackRoot playbackRoot) { playbackRoot.setEquipmentControlled(true); }
			}
		}

		private boolean isEmpty() { return participants.isEmpty(); }

		private boolean teleportGroup(FightParticipant.Side side, Vec3 destination)
		{
			List<FightParticipant> group = FightGroupController.selectBySide(participants, side);
			return FightGroupController.teleportFormation(group, destination);
		}

		private void tick(FightDefinition definition)
		{
			for (FightParticipant participant : participants)
			{
				if (!participant.isActive()) { continue; }

				Entity entity = participant.getEntity();
				if (!entity.isAlive())
				{
					participant.deactivate();
					continue;
				}

				participant.tickAttackCooldown();
				participant.tickFoodCooldown();
				Entity target = FightTargetSelector.select(participant, definition.getTargetMode(), participants,
						definition, server, definition.getDetectionRange());
				participant.setCurrentTarget(target);

				if (target == null)
				{
					FightDefenseController.stop(participant);
					participant.setState(FightParticipant.State.SEARCH_TARGET);
					continue;
				}
				if (!(target instanceof LivingEntity))
				{
					FightDefenseController.stop(participant);
				}

				double attackRange = definition.getAttackRange();
				if (target instanceof LivingEntity livingTarget
						&& FightDefenseController.tick(participant, livingTarget))
				{
					continue;
				}
				if (target instanceof LivingEntity livingTarget
						&& FightFoodController.tick(participant, livingTarget, attackRange))
				{
					continue;
				}
				double targetDistanceSqr = entity.distanceToSqr(target);
				double rangedRange = FightEquipmentController.getRangedRange(participant);
				if (targetDistanceSqr > attackRange * attackRange && rangedRange > attackRange && targetDistanceSqr <= rangedRange * rangedRange)
				{
					FightMovementController.faceTarget(entity, target.getX() - entity.getX(), target.getZ() - entity.getZ());
					if (participant.getAttackCooldownTicks() > 0)
					{
						participant.setState(FightParticipant.State.RECOVER);
						continue;
					}

					if (entity instanceof LivingEntity living
							&& FightEquipmentController.prepareCrossbowAttack(participant)
							&& living.getMainHandItem().getItem() instanceof net.minecraft.world.item.CrossbowItem)
					{
						participant.setState(FightParticipant.State.ATTACK);
						boolean fired = FightRangedController.tickCrossbow(participant);
						if (fired)
						{
							participant.setAttackCooldownTicks(calculateAttackCooldown(definition.getAttackSpeed()));
						}
						else if (participant.getCrossbowChargeTicks() > 0
								|| (living.getMainHandItem().getItem() instanceof net.minecraft.world.item.CrossbowItem
								&& net.minecraft.world.item.CrossbowItem.isCharged(living.getMainHandItem())))
						{
							// The Crossbow is still in its authoritative load/charged lifecycle.
						}
						else
						{
							participant.setState(FightParticipant.State.CHASE);
						}
						continue;
					}

					participant.setState(FightParticipant.State.ATTACK);
					if (FightRangedController.fireBow(participant))
					{
						participant.setAttackCooldownTicks(calculateAttackCooldown(definition.getAttackSpeed()));
					}
					else
					{
						participant.setState(FightParticipant.State.CHASE);
					}
					continue;
				}

				if (targetDistanceSqr > attackRange * attackRange)
				{
					participant.setState(FightParticipant.State.CHASE);
					if (participant.getNavigationWaypoint() != null)
					{
						FightNavigationController.navigate(participant, target, definition.getMovementSpeed(), attackRange);
						if (participant.getNavigationWaypoint() != null) { continue; }
					}

					double beforeTargetDistance = entity.distanceToSqr(target);
					FightMovementController.chase(participant, target, definition.getMovementSpeed(), attackRange);
					double afterTargetDistance = entity.distanceToSqr(target);
					if (afterTargetDistance + 1.0E-4 < beforeTargetDistance)
					{
						participant.resetNavigationStallTicks();
					}
					else
					{
						participant.incrementNavigationStallTicks();
					}
					if (participant.getNavigationStallTicks() >= 8)
					{
						FightNavigationController.navigate(participant, target, definition.getMovementSpeed(), attackRange);
					}
					continue;
				}

				participant.setState(FightParticipant.State.POSITION);
				participant.clearNavigationPath();
				FightMovementController.faceTarget(entity, target.getX() - entity.getX(), target.getZ() - entity.getZ());
				if (participant.getAttackCooldownTicks() > 0)
				{
					participant.setState(FightParticipant.State.RECOVER);
					continue;
				}

				if (!(entity instanceof LivingEntity attacker) || !target.isAlive())
				{
					participant.setState(FightParticipant.State.DEAD);
					participant.deactivate();
					continue;
				}

				participant.setState(FightParticipant.State.ATTACK);
				FightEquipmentController.prepareMeleeAttack(participant);
				Swing.attackTarget(attacker, target, (net.minecraft.server.level.ServerLevel)entity.level());
				participant.setAttackCooldownTicks(calculateAttackCooldown(definition.getAttackSpeed()));
			}
		}

		private static int calculateAttackCooldown(double attacksPerSecond)
		{
			double safeSpeed = Math.max(0.1, Math.min(attacksPerSecond, 20.0));
			return Math.max(1, (int)Math.round(20.0 / safeSpeed));
		}

		private void reset()
		{
			for (FightParticipant participant : participants)
			{
				FightDefenseController.stop(participant);
				FightFoodController.stop(participant);
				participant.deactivate();
				releaseParticipant(participant.getEntity().getUUID(), id);
			}
			participants.clear();

			for (MocapPlaybackRoot root : playbackRoots)
			{
				if (root instanceof PlaybackRoot playbackRoot)
				{
					playbackRoot.setEquipmentControlled(false);
					playbackRoot.setMovementControlled(false);
				}
				try { root.stop(); }
				catch (Exception e) { MocapMod.LOGGER.error("Failed to stop Fight playback root '{}'.", id, e); }
			}
			playbackRoots.clear();
		}

		private static boolean claimParticipant(UUID uuid, String fightId)
		{
			String owner = participantOwners.putIfAbsent(uuid, fightId);
			return owner == null || owner.equals(fightId);
		}

		private static void releaseParticipant(UUID uuid, String fightId)
		{
			participantOwners.remove(uuid, fightId);
		}
	}
}
