package com.confirmspellbook;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * A tier of Resurrect Thrall spells. Ghost, skeleton and zombie thralls of the same tier share a
 * Magic requirement and a rune cost, so the tier alone determines what the player needs to carry.
 */
public enum ThrallTier
{
	LESSER("Lesser", 38, ThrallRune.AIR, ThrallRune.MIND),
	SUPERIOR("Superior", 57, ThrallRune.EARTH, ThrallRune.DEATH),
	GREATER("Greater", 76, ThrallRune.FIRE, ThrallRune.BLOOD);

	private static final class RunesPerCast
	{
		private static final int ELEMENTAL = 10;
		private static final int CATALYTIC = 5;
		private static final int COSMIC = 1;
	}

	private final String displayName;
	private final int magicLevel;
	private final Map<ThrallRune, Integer> runesPerCast;

	ThrallTier(String displayName, int magicLevel, ThrallRune elemental, ThrallRune catalytic)
	{
		this.displayName = displayName;
		this.magicLevel = magicLevel;

		Map<ThrallRune, Integer> costs = new EnumMap<>(ThrallRune.class);
		costs.put(elemental, RunesPerCast.ELEMENTAL);
		costs.put(catalytic, RunesPerCast.CATALYTIC);
		costs.put(ThrallRune.COSMIC, RunesPerCast.COSMIC);
		this.runesPerCast = Collections.unmodifiableMap(costs);
	}

	public int getMagicLevel()
	{
		return magicLevel;
	}

	public Map<ThrallRune, Integer> getRunesPerCast()
	{
		return runesPerCast;
	}

	/**
	 * How many thralls of this tier the given rune supply covers, limited by whichever rune runs
	 * out first.
	 */
	public int castsAvailable(ToIntFunction<ThrallRune> availableRunes)
	{
		return runesPerCast.entrySet().stream()
			.mapToInt(cost -> availableRunes.applyAsInt(cost.getKey()) / cost.getValue())
			.min()
			.orElse(0);
	}

	/**
	 * The best tier the given Magic level can cast, falling back to the cheapest tier below the
	 * level requirement of every thrall spell.
	 */
	public static ThrallTier highestCastableAt(int magicLevel)
	{
		return Arrays.stream(values())
			.filter(tier -> magicLevel >= tier.magicLevel)
			.max(Comparator.comparingInt(ThrallTier::getMagicLevel))
			.orElse(LESSER);
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
