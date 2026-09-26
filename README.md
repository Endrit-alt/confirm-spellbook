# Confirm Spellbook

![Confirm Spellbook icon](icon.png)

A RuneLite plugin by **Endrit** that asks you to confirm your spellbook and reminds you about missing thrall supplies.

Source: [Endrit's Confirm Spellbook repository](https://github.com/Endrit-alt/confirm-spellbook).

### How it works

- **Confirm button** only on Ancients, Standard, and Lunar spellbook warnings, to acknowledge an intentional spellbook choice. Missing-book and missing-rune warnings have no button. The existing hide hotkey can still dismiss any warning.
- **Persistent acknowledgment**: unrelated inventory updates, rune-count changes, and visual setting changes do not bring back the same warning or repeat its notification. Banking and withdrawing **either the book or the pouch independently** resets confirmation if the warning still applies, including deposit/withdraw transitions reported before the next game tick. A warning also returns after its condition clears and recurs, when a different requirement becomes the warning, when the spellbook being confirmed changes, after logout/world hopping, or after restarting the plugin.
- **Check Carried Book or Pouch**, enabled by default: carrying the Book of the Dead or a rune pouch on a non-Arceuus spellbook prompts **Confirm spellbook : Ancients**, **Confirm spellbook : Standard**, or **Confirm spellbook : Lunar**, even without thrall runes. Either item is enough; you do not need both. Confirm acknowledges your intentional spellbook choice. It does not change the game's spellbook.
- Spellbook confirmation takes priority. Switching to Arceuus reveals any missing-rune warning, even if the book is also missing. Fixing the runes can then reveal a missing-book warning. Each notification condition can still be disabled separately.
- Pouch types and quantities are checked against the thrall tier automatically selected from your base Magic level. Inventory runes, combination runes, and equipped infinite sources count toward the total. An ancient-rune pouch with sufficient thrall supplies elsewhere does not produce a rune warning.
- Full warning checks are combined once per game tick, including updates reported through backing varps, to avoid warnings from intermediate loadouts. Lightweight book/pouch presence observations preserve banking transitions between ticks.
- **One loadout snapshot per check**: scan each inventory/equipment container once, resolve the pouch rune enum once, and read each pouch slot once. Rune totals use `long` addition and saturate at `Integer.MAX_VALUE` to prevent overflow from combined large stacks.

The Confirm button consumes its click and does not click the game underneath. Alt-drag still moves the reminder. The reminder renders above widgets so the button remains visible while banking. Flashing affects only the warning text area; Confirm keeps its dark tint and turns bright green on hover.

## Features

The original rule warns when you have **exactly 2 out of 3 requirements** for casting thralls. This fork also checks carried items: either the book or pouch prompts confirmation of a non-Arceuus spellbook, while a pouch also checks thrall runes on Arceuus. Turn off **Check Carried Book or Pouch** to use only the original rule:

### The 3 Requirements:
1. **Arceuus Spellbook** - You must be on the Arceuus spellbook
2. **Thrall Runes** - You must have enough runes for the thrall you cast
3. **Book of the Dead** - You must have the Book of the Dead equipped or in your inventory

### All Three Thrall Tiers

Ghost, skeleton and zombie thralls of the same tier cost the same runes, so the tier is all the plugin needs to know:

| Tier | Magic | Runes per cast |
|----------|-------|-----------------------------|
| Lesser | 38 | 10 air, 5 mind, 1 cosmic |
| Superior | 57 | 10 earth, 5 death, 1 cosmic |
| Greater | 76 | 10 fire, 5 blood, 1 cosmic |

The plugin always checks the highest tier your base Magic level allows. Temporary boosts and drains do not change the tier. Below level 38, the rune check uses Lesser thrall requirements.

### Rune Requirements

Having enough runes for one cast is sufficient. The plugin warns when your combined supplies cannot cover one cast of the automatically selected tier. There is no manual tier or minimum-casts setting.

### Reminder Messages

The plugin displays a reminder above your chatbox when you're missing one of these:

- Missing **Book of the Dead**: "Missing Book of the Dead"
- Other **Spellbook**: "Confirm spellbook : Ancients", "Confirm spellbook : Standard", or "Confirm spellbook : Lunar"
- Out of **Runes**: "Missing thrall runes"

### Configuration Options

- **Reminder Text Style**: Choose between long text, short text, or custom text
- **Notification on Reminder**: Send system notification when reminder appears
- **Notify on Missing Thrall Book**: Warn when the Book of the Dead is missing
- **Notify on Missing Thrall Runes**: Warn when you do not have enough runes for one thrall cast
- **Notify on Wrong Spellbook**: Warn when using a non-Arceuus spellbook
- **Display Options**: Customize colors and enable flashing
- **Hide Reminder Hotkey**: Acknowledge any current warning, including missing-book and missing-rune warnings without a Confirm button
- **Check Carried Book or Pouch**: Confirm a non-Arceuus spellbook and check insufficient thrall runes even when other requirements are also missing

Long text names the spellbook with a confirmation prompt. Short text uses **Ancients!**, **Standard!**, or **Lunar!**. Custom text still uses the message you configure.

The two settings sections, **Notification Conditions** and **Display Options**, start closed. There is no Feedback or Thrall Spell section.

### Smart Rune Detection

The plugin intelligently detects:
- Runes in both inventory and rune pouch, including divine rune pouches
- Combo runes (Dust, Mist and Smoke count as air; Dust, Mud and Lava as earth; Lava, Smoke, Steam and Sunfire as fire)
- Aether runes (count as cosmic runes)
- Elemental staves and tomes as an infinite source of their element (air, earth and fire staves and battlestaves, the combo battlestaves, Tome of Fire and Tome of Earth)

## Build and run this fork

Use JDK 11 or newer. On Windows:

```powershell
.\gradlew.bat test jar
.\gradlew.bat runPlugin
```

On macOS/Linux, use `./gradlew` instead. `runPlugin` starts a developer RuneLite client with Confirm Spellbook loaded; disable the original Plugin Hub copy in that client to avoid duplicate reminders. The built JAR is `build/libs/confirm-spellbook-1.2.1.jar`. It is not a standalone application or automatically installed into your regular RuneLite client. Plugin Hub installation will be available after RuneLite approves the submission.

## Review and possible next features

See [CODE_REVIEW.md](CODE_REVIEW.md) for the full source review, remaining limitations, and prioritized suggestions. Tests cover pouch contents, warning transitions, actual overlay rendering and Confirm mouse handling. Rendered test previews are generated in `build/overlay-previews/`.
