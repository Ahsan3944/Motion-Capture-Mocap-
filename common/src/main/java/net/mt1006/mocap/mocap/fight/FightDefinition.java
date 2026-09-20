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
		sourceScenes.clear();
		sourceScenes.addAll(values);
	}

	public void setTargetPlayers(List<String> values)
	{
		targetPlayers.clear();
		targetPlayers.addAll(values);
	}

	public void setTargetScenes(List<String> values)
	{
		targetScenes.clear();
		targetScenes.addAll(values);
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
