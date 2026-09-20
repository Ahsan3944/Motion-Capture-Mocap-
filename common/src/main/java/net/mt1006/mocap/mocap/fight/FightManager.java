package net.mt1006.mocap.mocap.fight;

import net.mt1006.mocap.MocapMod;
import net.mt1006.mocap.api.v1.io.CommandOutput;
import net.mt1006.mocap.mocap.files.Files;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public final class FightManager
{
	public static final String FILE_EXTENSION = ".mcmocap_fight";
	private static final int FORMAT_VERSION = FightDefinition.CURRENT_VERSION;
	private static final Map<String, FightDefinition> definitions = new LinkedHashMap<>();
	private static final Map<String, FightRuntime> active = new LinkedHashMap<>();
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

		if (!deleteFile(id))
		{
			definitions.put(id, definition);
			return out.sendFailure("Failed to remove Fight file: " + id);
		}
		return out.sendSuccessLiteral("Removed Fight '%s'.", id);
	}

	public static boolean start(CommandOutput out, String id)
	{
		ensureLoaded();
		FightDefinition definition = definitions.get(id);
		if (definition == null) { return out.sendFailure("Fight not found: " + id); }
		if (active.containsKey(id)) { return out.sendFailure("Fight is already running: " + id); }
		if (definition.getSourceScenes().isEmpty())
		{
			return out.sendFailure("Fight has no source scene configured: " + id);
		}

		// Runtime participants are deliberately not created in this phase.
		// The next runtime phase will bind scene playback instances here.
		active.put(id, new FightRuntime(id));
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
				entry.getValue().tick();
			}
			catch (Exception e)
			{
				failed.add(entry.getKey());
				MocapMod.LOGGER.error("Fight '{}' failed during runtime tick; isolating it.", entry.getKey(), e);
			}
		}

		for (String id : failed)
		{
			active.remove(id);
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
		for (FightDefinition definition : definitions.values())
		{
			definition.setState(FightDefinition.State.STOPPED);
			save(definition);
		}
	}

	public static String describe(FightDefinition definition)
	{
		return String.format("Fight '%s': state=%s, sourceScenes=%s, targetPlayers=%s, targetScenes=%s, power=%d, attackSpeed=%.3f, attackRange=%.2f, detectionRange=%.2f, movementSpeed=%.2f, damage=%.2f, knockback=%.2f, targetMode=%s",
				definition.getId(), definition.getState(), definition.getSourceScenes(), definition.getTargetPlayers(),
				definition.getTargetScenes(), definition.getPower(), definition.getAttackSpeed(), definition.getAttackRange(),
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

		private FightRuntime(String id)
		{
			this.id = id;
		}

		private void tick()
		{
			// Runtime actor/target controller is implemented in the next phase.
		}

		private void reset()
		{
			// Reset snapshot ownership is implemented with runtime participants.
		}
	}
}
