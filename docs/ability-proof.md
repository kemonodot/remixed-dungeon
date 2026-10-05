# ATK 1 / three-ability technical proof

This is a disposable practice floor, not a campaign. Java 17 and a graphical
desktop with OpenGL are required. Extract `RemixedDungeon-ability-proof.zip` and
run `bash play.sh` on Linux/macOS, or `play.bat` on Windows. The shell launcher adds
the required first-thread JVM option on macOS. Restart the launcher to reset
the floor. Sound is disabled. Saves/preferences are isolated under the bundle
(`rundir/`, `profile/` and `cache/`), with the same isolation in the Gradle task.
The Linux bundle is exercised here; other platforms need their own runtime check.

From the repository, use the desktop Gradle settings:

```sh
./gradlew -c settings.desktop.gradle :RemixedDungeonHeadless:abilityProofTest
./gradlew -c settings.desktop.gradle :RemixedDungeonDesktop:build :RemixedDungeonDesktop:packageAbilityProof
./gradlew -c settings.desktop.gradle :RemixedDungeonDesktop:runAbilityProof
```

In the configured Codex environment, replace `./gradlew -c settings.desktop.gradle`
with `bash /workspace/remixed-onboarding/gradle.sh` (Java 17, proxy and the external
desktop-only Huawei dependency exclusion). A display is needed to play.

## Try the abilities

Click/tap a floor tile to move, and an adjacent rat to attack. The normal game
movement keys also work. Hero starts at (5,6), with 80 HP and all three abilities.
Eight rats occupy a fixed 21x13 floor. There are no stairs or respawns.

* **Footprint Sparks:** move one tile; a visible spark remains behind for three
  game-time units. An enemy entering it takes 4 special damage; the spark is
  consumed. Step away from the pursuing 4 HP rats to kill them without trading hits.
* **Rejection:** each successful normal hit deals at most 1 HP and pushes a living
  enemy one tile away. Walls, pits and occupied tiles block the push. Try the 8 HP
  rat beside the starting position; then arrange a push into your own spark.
* **Death Aftershock:** any hostile enemy death deals 3 special damage to hostile
  enemies within one tile (including diagonals). In the right room, the 4 HP rat
  at (12,6) sits beside three 3 HP rats. Leave a spark next to the group and lure or
  push the first rat onto it. Each resulting death can start another blast.

The existing buff icons show all three abilities; the game log reports sparks,
pushes and blasts. Passive practice rats react when hurt; other rats pursue you.
Health, misses, turns, terrain and enemy AI remain real game systems.

## Implementation and limits

The dedicated launcher enables `rpd.abilityProof`. Without that property the
ordinary launcher/game is unchanged. The cap covers Hero `Char.attack` damage,
including ordinary missile attacks, after modifiers and resistance. Immunity or
absorption may still yield zero. Direct spells, status effects, traps and ability
damage remain uncapped. Existing weapon procs may still cause separate special
damage or effects; a later product policy must decide which of those to allow.

`ProofAbilities` queues MOVE / ENTER / HIT / DEATH events. Lua buff definitions
provide ID, tags, trigger, condition and effect. Marker ownership uses existing
`CustomBuff`. Add a Lua definition and registry entry to add an ability using the
existing events/effects; a new event or primitive needs a small Java addition.

Each event cascade is limited to depth 16, 128 activations and 256 events/queue
entries. Nested deaths enqueue work instead of recursively invoking abilities.
Duplicate deaths in a cascade are suppressed; AoE iterates a snapshot of enemies.
Budget exhaustion discards remaining effects, and script exceptions disable the
proof session. Arbitrary Lua infinite loops are outside these event-budget guards.

Headless checks use fixed terrain and guaranteed hits, since the existing RNG
uses unseedable `Math.random()`. They cover amplified damage, weapons/STR/levels,
special damage, all three abilities, walls/occupancy, combined push/spark, secondary
deaths, both safety limits, 1000 actual game turns, and the playable floor itself.

Temporary sparks are intentionally transient across save/load and level changes;
the practice launcher always starts fresh. This proof does not implement persistent
ability-world state, arbitrary scripting timeouts, or production balance.

## Verified in Codex Cloud

With Temurin 17.0.14+7 and Gradle 8.12.1, Headless integration passes 75 assertions,
including 1000 movement turns advancing actor time from 0 to 1006. A dense 225-rat
test reaches the 128-activation guard; a long line reaches the depth guard.
Headless and Desktop `build` tasks pass. The ordinary, opt-out Headless launcher
also completes its five-turn smoke test.

The extracted Linux ZIP was run with the real LWJGL desktop renderer on a dummy
Xorg display (software OpenGL). Real X keyboard input verifies a critical hit
showing 1, knockback, visible sparks, and a four-enemy death chain in the right
room, leaving the game responsive. This verifies playability; fun still needs
human assessment. There has been no 50–200-ability scale test.
