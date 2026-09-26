package com.confirmspellbook;

import net.runelite.api.gameval.ItemID;
import org.junit.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Set.of throws on duplicate entries, so a slipped item id in a registry would only surface as an
 * ExceptionInInitializerError at runtime. Touching every class that holds one catches it here.
 */
public class ThrallDataTest
{
	@Test
	public void registriesInitialise()
	{
		assertEquals(7, ThrallRune.values().length);
		assertEquals(3, ThrallTier.values().length);
		assertNotNull(new PlayerLoadout());
	}

	@Test
	public void tierCostsMatchTheSpells()
	{
		assertEquals(38, ThrallTier.LESSER.getMagicLevel());
		assertEquals(57, ThrallTier.SUPERIOR.getMagicLevel());
		assertEquals(76, ThrallTier.GREATER.getMagicLevel());

		assertCosts(ThrallTier.LESSER, ThrallRune.AIR, ThrallRune.MIND);
		assertCosts(ThrallTier.SUPERIOR, ThrallRune.EARTH, ThrallRune.DEATH);
		assertCosts(ThrallTier.GREATER, ThrallRune.FIRE, ThrallRune.BLOOD);
	}

	private void assertCosts(ThrallTier tier, ThrallRune elemental, ThrallRune catalytic)
	{
		Map<ThrallRune, Integer> costs = tier.getRunesPerCast();
		assertEquals(3, costs.size());
		assertEquals(Integer.valueOf(10), costs.get(elemental));
		assertEquals(Integer.valueOf(5), costs.get(catalytic));
		assertEquals(Integer.valueOf(1), costs.get(ThrallRune.COSMIC));
	}

	@Test
	public void castsAreLimitedByTheScarcestRune()
	{
		Map<ThrallRune, Integer> supply = new EnumMap<>(ThrallRune.class);
		supply.put(ThrallRune.FIRE, 35);
		supply.put(ThrallRune.BLOOD, 20);
		supply.put(ThrallRune.COSMIC, 4);

		assertEquals(3, castsFrom(ThrallTier.GREATER, supply));

		supply.put(ThrallRune.COSMIC, 2);
		assertEquals(2, castsFrom(ThrallTier.GREATER, supply));

		supply.put(ThrallRune.BLOOD, 0);
		assertEquals(0, castsFrom(ThrallTier.GREATER, supply));
	}

	@Test
	public void unlimitedRunesDoNotOverflow()
	{
		Map<ThrallRune, Integer> supply = new EnumMap<>(ThrallRune.class);
		supply.put(ThrallRune.AIR, Integer.MAX_VALUE);
		supply.put(ThrallRune.MIND, 100);
		supply.put(ThrallRune.COSMIC, 7);

		assertEquals(7, castsFrom(ThrallTier.LESSER, supply));
	}

	private int castsFrom(ThrallTier tier, Map<ThrallRune, Integer> supply)
	{
		return tier.castsAvailable(rune -> supply.getOrDefault(rune, 0));
	}

	@Test
	public void autoTierFollowsMagicLevel()
	{
		assertEquals(ThrallTier.LESSER, ThrallTier.highestCastableAt(37));
		assertEquals(ThrallTier.LESSER, ThrallTier.highestCastableAt(38));
		assertEquals(ThrallTier.LESSER, ThrallTier.highestCastableAt(56));
		assertEquals(ThrallTier.SUPERIOR, ThrallTier.highestCastableAt(57));
		assertEquals(ThrallTier.SUPERIOR, ThrallTier.highestCastableAt(75));
		assertEquals(ThrallTier.GREATER, ThrallTier.highestCastableAt(76));
		assertEquals(ThrallTier.GREATER, ThrallTier.highestCastableAt(99));
	}

	@Test
	public void comboRunesAndInfiniteSourcesCount()
	{
		assertTrue(ThrallRune.AIR.isSatisfiedByRune(ItemID.SMOKERUNE));
		assertTrue(ThrallRune.FIRE.isSatisfiedByRune(ItemID.SMOKERUNE));
		assertTrue(ThrallRune.EARTH.isSatisfiedByRune(ItemID.DUSTRUNE));
		assertTrue(ThrallRune.AIR.isSatisfiedByRune(ItemID.DUSTRUNE));
		assertTrue(ThrallRune.COSMIC.isSatisfiedByRune(ItemID.AETHERRUNE));
		assertFalse(ThrallRune.MIND.isSatisfiedByRune(ItemID.DEATHRUNE));

		assertTrue(ThrallRune.AIR.isInfiniteSource(ItemID.MYSTIC_SMOKE_BATTLESTAFF));
		assertTrue(ThrallRune.EARTH.isInfiniteSource(ItemID.TOME_OF_EARTH));
		assertTrue(ThrallRune.FIRE.isInfiniteSource(ItemID.TOME_OF_FIRE));
		assertFalse(ThrallRune.FIRE.isInfiniteSource(ItemID.STAFF_OF_AIR));

		assertFalse(ThrallRune.COSMIC.hasInfiniteSources());
		assertFalse(ThrallRune.BLOOD.hasInfiniteSources());
		assertTrue(ThrallRune.EARTH.hasInfiniteSources());
	}
}
