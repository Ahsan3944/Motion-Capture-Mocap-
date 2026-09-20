package net.mt1006.mocap.mocap.fight;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class FightDefinition
{
	public static final int CURRENT_VERSION = 1;

	public enum TargetMode
	{
		NEAREST,
		CURRENT_TARGET,
		LOWEST_HEALTH,
		FIXED_TARGET
	}

	public enum State
	{
		STOPPED,
		RUNNING
	}

	private final String id;
	private final List<String> sourceScenes = new ArrayList<>();
	private final List<String> targetPlayers = new ArrayList<>();
	private final List<String> targetScenes = new ArrayList<>();
	private final List<String> sourceSceneTeams = new ArrayList<>();
	private final List<String> targetSceneTeams = new ArrayList<>();
	private final List<String> targetPlayerTeams = new ArrayList<>();
	private int power = 5;
	private double attackSpeed = 1.0;
	private double attackRange = 3.0;
	private double detectionRange = 16.0;
	private double movementSpeed = 1.0;
	private double damageMultiplier = 1.0;
	private double knockbackMultiplier = 1.0;
	private TargetMode targetMode = TargetMode.NEAREST;
	private State state = State.STOPPED;

	public FightDefinition(String id)
	{
		this.id = id;
	}

	public String getId() { return id; }
	public List<String> getSourceScenes() { return Collections.unmodifiableList(sourceScenes); }
	public List<String> getTargetPlayers() { return Collections.unmodifiableList(targetPlayers); }
	public List<String> getTargetScenes() { return Collections.unmodifiableList(targetScenes); }
	public List<String> getSourceSceneTeams() { return Collections.unmodifiableList(sourceSceneTeams); }
	public List<String> getTargetSceneTeams() { return Collections.unmodifiableList(targetSceneTeams); }
	public List<String> getTargetPlayerTeams() { return Collections.unmodifiableList(targetPlayerTeams); }

	public String getSourceSceneTeam(String scene)
	{
		int index = sourceScenes.indexOf(scene);
		return index >= 0 && index < sourceSceneTeams.size() ? sourceSceneTeams.get(index) : "SOURCE";
	}

	public String getTargetSceneTeam(String scene)
	{
		int index = targetScenes.indexOf(scene);
		return index >= 0 && index < targetSceneTeams.size() ? targetSceneTeams.get(index) : "TARGET";
	}

	public String getTargetPlayerTeam(String player)
	{
		int index = targetPlayers.indexOf(player);
		return index >= 0 && index < targetPlayerTeams.size() ? targetPlayerTeams.get(index) : "TARGET";
	}
	public int getPower() { return power; }
	public double getAttackSpeed() { return attackSpeed; }
	public double getAttackRange() { return attackRange; }
	public double getDetectionRange() { return detectionRange; }
	public double getMovementSpeed() { return movementSpeed; }
	public double getDamageMultiplier() { return damageMultiplier; }
	public double getKnockbackMultiplier() { return knockbackMultiplier; }
	public TargetMode getTargetMode() { return targetMode; }
	public State getState() { return state; }

	public void setSourceScenes(List<String> values)
	{
		List<String> oldTeams = new ArrayList<>(sourceSceneTeams);
		sourceScenes.clear();
		sourceScenes.addAll(values);
		sourceSceneTeams.clear();
		for (int i = 0; i < sourceScenes.size(); i++)
		{
			sourceSceneTeams.add(i < oldTeams.size() && isValidTeamId(oldTeams.get(i)) ? oldTeams.get(i) : "SOURCE");
		}
	}

	public void setTargetPlayers(List<String> values)
	{
		List<String> oldTeams = new ArrayList<>(targetPlayerTeams);
		targetPlayers.clear();
		targetPlayers.addAll(values);
		targetPlayerTeams.clear();
		for (int i = 0; i < targetPlayers.size(); i++)
		{
			targetPlayerTeams.add(i < oldTeams.size() && isValidTeamId(oldTeams.get(i)) ? oldTeams.get(i) : "TARGET");
		}
	}

	public void setTargetScenes(List<String> values)
	{
		List<String> oldTeams = new ArrayList<>(targetSceneTeams);
		targetScenes.clear();
		targetScenes.addAll(values);
		targetSceneTeams.clear();
		for (int i = 0; i < targetScenes.size(); i++)
		{
			targetSceneTeams.add(i < oldTeams.size() && isValidTeamId(oldTeams.get(i)) ? oldTeams.get(i) : "TARGET");
		}
	}

	public boolean setSourceSceneTeam(String scene, String team)
	{
		return setTeam(sourceScenes, sourceSceneTeams, scene, team, "SOURCE");
	}

	public boolean setTargetSceneTeam(String scene, String team)
	{
		return setTeam(targetScenes, targetSceneTeams, scene, team, "TARGET");
	}

	public boolean setTargetPlayerTeam(String player, String team)
	{
		return setTeam(targetPlayers, targetPlayerTeams, player, team, "TARGET");
	}

	public void setSourceSceneTeams(List<String> values)
	{
		setTeams(sourceScenes, sourceSceneTeams, values, "SOURCE");
	}

	public void setTargetSceneTeams(List<String> values)
	{
		setTeams(targetScenes, targetSceneTeams, values, "TARGET");
	}

	public void setTargetPlayerTeams(List<String> values)
	{
		setTeams(targetPlayers, targetPlayerTeams, values, "TARGET");
	}

	private static boolean setTeam(List<String> references, List<String> teams, String reference, String team, String fallback)
	{
		if (reference == null || team == null || !isValidTeamId(team)) { return false; }
		int index = references.indexOf(reference);
		if (index < 0) { return false; }
		while (teams.size() < references.size()) { teams.add(fallback); }
		teams.set(index, team);
		return true;
	}

	private static void setTeams(List<String> references, List<String> teams, List<String> values, String fallback)
	{
		teams.clear();
		for (int i = 0; i < references.size(); i++)
		{
			String value = values != null && i < values.size() ? values.get(i) : null;
			teams.add(isValidTeamId(value) ? value : fallback);
		}
	}

	private static boolean isValidTeamId(String value)
	{
		return value != null && value.matches("[A-Za-z0-9_.-]{1,32}");
	}

	public boolean setPower(int value)
	{
		if (value < 1 || value > 10) { return false; }
		power = value;
		return true;
	}

	public boolean setAttackSpeed(double value)
	{
		if (!Double.isFinite(value) || value <= 0.0) { return false; }
		attackSpeed = value;
		return true;
	}

	public boolean setAttackRange(double value)
	{
		if (!Double.isFinite(value) || value <= 0.0) { return false; }
		attackRange = value;
		return true;
	}

	public boolean setDetectionRange(double value)
	{
		if (!Double.isFinite(value) || value <= 0.0) { return false; }
		detectionRange = value;
		return true;
	}

	public boolean setMovementSpeed(double value)
	{
		if (!Double.isFinite(value) || value < 0.0) { return false; }
		movementSpeed = value;
		return true;
	}

	public boolean setDamageMultiplier(double value)
	{
		if (!Double.isFinite(value) || value < 0.0) { return false; }
		damageMultiplier = value;
		return true;
	}

	public boolean setKnockbackMultiplier(double value)
	{
		if (!Double.isFinite(value) || value < 0.0) { return false; }
		knockbackMultiplier = value;
		return true;
	}

	public void setTargetMode(TargetMode value)
	{
		targetMode = value;
	}

	public void setState(State value)
	{
		state = value;
	}
}
