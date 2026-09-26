# Source review and suggested features

Reviewed the complete upstream source at `90eee3bdd863159b9f30473eaca35c68304898ac`, the revision listed in RuneLite's Plugin Hub marker on 2026-09-25, and the changes in this fork. This includes every production class, existing tests, build/launch configuration, both plugin metadata files, README, license, funding file, and Gradle wrapper configuration. Game behavior was assessed from source and automated tests; no live account session was used.

## Findings addressed in this fork

| Finding | Cause in upstream | Change |
| --- | --- | --- |
| Pouch with ancient runes can produce no warning | `ConfirmSpellbookPlugin.evaluateWarningState()` requires exactly two of the three prerequisites. The pouch reader does inspect rune types; multiple missing prerequisites suppress the warning. | A carried pouch prompts confirmation on any spellbook, even without thrall runes. Carrying the book already confirms Arceuus; on other spellbooks, the book also triggers confirmation. Arceuus supply checks still apply after confirmation. |
| No visible acknowledgment control | `ConfirmSpellbookOverlay` only renders text. | A visible Confirm button sits beside spellbook warnings in every text style, and beside the missing-book warning after accepting Arceuus. The existing hide hotkey dismisses any warning. Its full click is consumed; Alt-drag and clicks outside it retain their normal behavior. |
| Hide hotkey does not reliably stay hidden | `hideWarning()` resets the missing condition to `NONE`, so the next inventory event treats the same issue as new. | Acknowledgment retains the warning identity. It resets on resolution, a different warning/spellbook, withdrawing either the book or pouch after banking, logout/hopping, or restart. A queued click cannot dismiss a newer warning or loadout. |
| Pouch changes can be missed | `onVarbitChanged()` checks only individual varbit IDs. RuneLite also reports changes to their backing varps. | Both update paths mark state dirty; evaluate once at the next game tick, after related inventory and rune-slot updates. |
| Different warning can silently replace the old one | `showWarning()` sends a notification only if no warning was already visible. | A new warning identity sends one notification; repeated updates of the same warning do not. |
| State survives stopping and restarting the plugin | Shutdown unregisters the overlay/hotkey but does not reset warning state. | Clear state and button targets on shutdown, startup, logout and hopping; unregister the mouse listener too. |
| Reminders interrupt login with an existing loadout | Evaluating carried items at startup immediately shows warnings. | Login, world hopping, reconnecting and enabling the plugin establish a quiet baseline. Checks start when either the book or pouch newly appears while the bank is open, then continue for that session. Opening the bank alone and pickups outside the bank do not start checks. Rune totals are only read after checks start. |
| Chat reminders always use RuneLite's highlight color | The default notifier formats chat messages with the shared highlight color. | A Chat Notification Color picker defaults to red and colors new reminder messages. Queue one colored chat copy while preserving inherited or advanced notification settings, focus preferences, sound and plain desktop text. |
| Inconsistent sizing across text styles | The old overlay uses negative padding for long/custom text and different render paths depending on style. | Measure the rendered text and button with explicit positive spacing and one render path. |
| Packaged metadata differs from root metadata | `src/main/resources/runelite-plugin.properties` has older tags and lacks the build field. | Synchronize metadata as Confirm Spellbook by Endrit and retain the required BSD license notice. |
| Large rune stacks can overflow | Inventory, combination-rune and pouch quantities were added as `int`. | Sum using `long`, then saturate at `Integer.MAX_VALUE` before calculating casts. Equipped infinite sources remain saturated when pouch runes are added. |
| Repeated loadout reads | Each required rune caused another inventory scan and another read of the pouch's slots. | Build one immutable snapshot per warning check, with one scan per container, one pouch enum lookup and one read per slot. |

## Remaining limitations and code improvements

1. **Readiness is about supplies, not every casting restriction.** `ThrallTier.highestCastableAt()` falls back to Lesser even below level 38. Automatic selection uses the real Magic level, so boosts and drains do not change the selected tier. Prayer, quest completion, cooldowns and location restrictions are not checked. Consider a separate optional casting-readiness check. Avoid noisy warnings on every temporary stat change.
2. **Only one issue is displayed at a time.** `evaluateWarningState()` deliberately gives spellbook confirmation priority, followed by runes. Confirming Standard, Ancients or Lunar keeps the intentional non-thrall setup quiet. Confirming Arceuus reveals any separate supply warning; carrying the book skips Arceuus confirmation entirely. A checklist would be better for users who want to see all missing requirements together.
3. **The equipment/rune registry needs upkeep.** `ThrallRune` is a clear, small mapping of pure runes, combination runes and infinite sources, but future rune/staff/tome variants must be added. Compare it to RuneLite's current game data when new items release. Keep charged and empty tome variants distinct.
4. **Build resolution is moving.** `build.gradle` uses RuneLite `latest.release`, which is appropriate for keeping up with the client but means later builds can resolve a different API. Add CI against the current release, and optionally allow an explicit RuneLite-version override for reproducing bugs. The bundled Gradle wrapper is 8.12.1; consider setting its distribution checksum when maintaining the wrapper.
5. **Very long custom text remains a single row.** The overlay autosizes to its text. A maximum width with word wrapping would prevent unusually long custom messages from extending beyond a small game window. Standard messages fit comfortably in the rendered previews.

## Performance assessment

The plugin is small and event-driven, so no noticeable FPS improvement is claimed without profiling. The useful improvements are reducing redundant work and making state transitions predictable.

| Area | Current behavior | Recommendation/status |
| --- | --- | --- |
| Event bursts | Upstream immediately recalculates after each relevant inventory, equipment or varbit event. One loadout change can produce several events. | Implemented: mark state dirty and read the settled loadout once at the next game tick. Lightweight container observations track book/pouch removal and return without reading pouch runes, so banking transitions are retained. Config/startup refreshes remain explicit. |
| Inventory and equipment scans | Upstream searches for the pouch and book separately and scans inventory for each required rune. | Implemented: build one immutable loadout snapshot per refresh and reuse its book/pouch flags and rune totals. Snapshots are not cached across checks. |
| Pouch reads | Upstream rereads all six type/quantity pairs for each of the three required rune types and obtains the enum for each populated slot. | Implemented: one enum lookup, 12 varbit reads total (six type/quantity pairs), and rune totals accumulated in an `EnumMap`. The tests verify those read counts. |
| Frame rendering | The overlay measures text and creates its drawing components each visible frame. | Low priority: cache layout until text/font changes and reuse drawing components if a profiler shows allocation pressure. The overlay is tiny and only renders while a warning is visible. |
| Broad backing-varp fallback | Any backing-varp change can mark the loadout dirty, even when unrelated to thralls. This protects against missed pouch updates. | If profiling justifies it, map the spellbook/pouch varbits to their backing varps and filter those IDs. Preserve game-version compatibility and the new regression test. |

Reliable acknowledgment, backing-varp handling, clearing stale state, the single-snapshot loadout reader and overflow-safe totals are implemented. Banking either tracked item independently now rearms an acknowledged warning on withdrawal; unrelated inventory updates do not. No FPS improvement has been measured.

## Suggested features, in priority order

| Priority | Feature | Player benefit |
| --- | --- | --- |
| 1 | Activity presets: Thralls / Ancients / Standard / Lunar | Choose the spellbook and rune loadout expected for a raid, boss or skilling activity. Confirm is then checking the intended setup, rather than treating every pouch as a possible thrall setup. |
| 2 | Missing-rune details | Show exactly what is short, such as "Missing cosmic runes" or "Need 20 more fire runes for 5 casts", combining inventory and pouch supplies. |
| 3 | Bank departure check | Recheck the complete setup on bank close, with optional rearming for the next trip. Reduce interruptions while assembling a loadout. |
| 4 | All-requirements checklist | Show spellbook, book and runes together, with a single acknowledgment for the current setup. Optional prayer/level/quest checks can be added separately. |
| 5 | Configurable acknowledgment duration | Offer until setup changes, next bank visit, or a short snooze. |

Combination runes, Aether runes, equipped infinite sources, flashing, colors and a hide hotkey already existed upstream. Confirm Spellbook removes manual tier selection, minimum-cast thresholds, low-cast text and Feedback links. It always selects the tier from the real Magic level and checks supplies for one cast.

## Coverage and validation

The implementation was validated on 2026-09-26 with Java 11 and RuneLite 1.12.39 (resolved from `latest.release`), including rendered previews for all four spellbooks, custom text, hover and flashing. Version 1.2.1 passed **74 locally maintained tests** and builds successfully, with regressions for automatic Arceuus confirmation when carrying the book, separate supply warnings, independent book/pouch banking, multiple updates before one tick, stale confirmations, snapshot read counts and maximum rune stacks. The regression suite is kept locally. No live game session was used for automated validation.

- `PlayerLoadout`, `ThrallRune`, `ThrallTier`: reviewed rune counting, all pouch slots and variants, combinations, infinite sources and automatic tier selection. Removed the unused manual-tier enum. Added loadout tests for ancient runes, split inventory/pouch supplies, banked pouches, later slots, missing enum/container data, and equipped versus carried staves.
- `ConfirmSpellbookPlugin`, `MissingCondition`: reviewed each event handler, warning transition, notification and lifecycle hook. Added behavioral tests for named spellbooks, priority, rearming, notifications, backing-varp updates, event coalescing, automatic tier boundaries and the one-cast threshold.
- `ConfirmSpellbookOverlay`, `ConfirmSpellbookStyle`, `ConfirmMouseListener`: reviewed layout, text styles, rendering layers, mouse coordinates, click consumption and thread handoff. Tests use actual Guice construction and render the real overlay into images, then exercise the button at its canvas coordinates.
- `ConfirmSpellbookConfig`: reviewed defaults, condition toggles, text, colors, notification and hotkey settings. Uses its own `confirmspellbook` configuration group, Short Text by default, and a default-on carried-book/pouch check; both remaining settings sections start closed. Chat Notification Color appears between Custom Text and Flash the Reminder Box.
- `PluginLauncher`, launch logging, Gradle files and metadata: reviewed and retained. Event subscriber registration, tier data and the added regression tests are covered by the local suite.

Build with `gradlew.bat jar testClasses` on Windows or `./gradlew jar testClasses` elsewhere; `runPlugin` starts the development launcher. Local validation generates visual previews under `build/overlay-previews/`. A live RuneLite check is still useful for fixed/resizable/stretched layouts, real banking and world hopping; the fork is not automatically installed by building the JAR.
