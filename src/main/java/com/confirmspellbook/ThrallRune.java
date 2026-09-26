package com.confirmspellbook;

import net.runelite.api.gameval.ItemID;

import java.util.Collections;
import java.util.Set;

/**
 * A rune a thrall spell consumes, with every item that can satisfy it: the pure rune, the
 * combination runes that contain it, and the staves and tomes that stand in for it indefinitely.
 */
public enum ThrallRune
{
	AIR(
		Set.of(
			ItemID.AIRRUNE,
			ItemID.MISTRUNE,
			ItemID.DUSTRUNE,
			ItemID.SMOKERUNE),
		Set.of(
			ItemID.STAFF_OF_AIR,
			ItemID.AIR_BATTLESTAFF,
			ItemID.MYSTIC_AIR_STAFF,
			ItemID.BR_AIR_STAFF,
			ItemID.MIST_BATTLESTAFF,
			ItemID.MYSTIC_MIST_BATTLESTAFF,
			ItemID.DUST_BATTLESTAFF,
			ItemID.MYSTIC_DUST_BATTLESTAFF,
			ItemID.SMOKE_BATTLESTAFF,
			ItemID.MYSTIC_SMOKE_BATTLESTAFF)),

	EARTH(
		Set.of(
			ItemID.EARTHRUNE,
			ItemID.MUDRUNE,
			ItemID.DUSTRUNE,
			ItemID.LAVARUNE),
		Set.of(
			ItemID.STAFF_OF_EARTH,
			ItemID.EARTH_BATTLESTAFF,
			ItemID.MYSTIC_EARTH_STAFF,
			ItemID.MUD_BATTLESTAFF,
			ItemID.MYSTIC_MUD_STAFF,
			ItemID.DUST_BATTLESTAFF,
			ItemID.MYSTIC_DUST_BATTLESTAFF,
			ItemID.LAVA_BATTLESTAFF,
			ItemID.MYSTIC_LAVA_STAFF,
			ItemID.LAVA_BATTLESTAFF_PRETTY,
			ItemID.MYSTIC_LAVA_STAFF_PRETTY,
			ItemID.TOME_OF_EARTH)),

	FIRE(
		Set.of(
			ItemID.FIRERUNE,
			ItemID.LAVARUNE,
			ItemID.SMOKERUNE,
			ItemID.STEAMRUNE,
			ItemID.SUNFIRERUNE),
		Set.of(
			ItemID.STAFF_OF_FIRE,
			ItemID.FIRE_BATTLESTAFF,
			ItemID.MYSTIC_FIRE_STAFF,
			ItemID.LAVA_BATTLESTAFF,
			ItemID.MYSTIC_LAVA_STAFF,
			ItemID.LAVA_BATTLESTAFF_PRETTY,
			ItemID.MYSTIC_LAVA_STAFF_PRETTY,
			ItemID.STEAM_BATTLESTAFF,
			ItemID.MYSTIC_STEAM_BATTLESTAFF,
			ItemID.STEAM_BATTLESTAFF_PRETTY,
			ItemID.MYSTIC_STEAM_BATTLESTAFF_PRETTY,
			ItemID.SMOKE_BATTLESTAFF,
			ItemID.MYSTIC_SMOKE_BATTLESTAFF,
			ItemID.TOME_OF_FIRE,
			ItemID.BR_TOME_OF_FIRE,
			ItemID.TWINFLAME_STAFF)),

	MIND(Set.of(ItemID.MINDRUNE)),

	DEATH(Set.of(ItemID.DEATHRUNE)),

	BLOOD(Set.of(ItemID.BLOODRUNE)),

	COSMIC(Set.of(ItemID.COSMICRUNE, ItemID.AETHERRUNE));

	private final Set<Integer> runeItemIds;
	private final Set<Integer> infiniteSourceItemIds;

	ThrallRune(Set<Integer> runeItemIds)
	{
		this(runeItemIds, Collections.emptySet());
	}

	ThrallRune(Set<Integer> runeItemIds, Set<Integer> infiniteSourceItemIds)
	{
		this.runeItemIds = runeItemIds;
		this.infiniteSourceItemIds = infiniteSourceItemIds;
	}

	public boolean isSatisfiedByRune(int itemId)
	{
		return runeItemIds.contains(itemId);
	}

	public boolean isInfiniteSource(int itemId)
	{
		return infiniteSourceItemIds.contains(itemId);
	}

	public boolean hasInfiniteSources()
	{
		return !infiniteSourceItemIds.isEmpty();
	}
}
