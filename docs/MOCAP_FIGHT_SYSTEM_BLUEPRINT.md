# MoCap Fight System — Master Architecture & Implementation Blueprint

**Status:** DESIGN LOCKED / IMPLEMENTATION PLANNING  
**Repository:** `Ahsan3944/Motion-Capture-Mocap-`  
**Purpose:** Master source of truth for the runtime Fight feature built on top of the existing MoCap recording, scene and playback systems.

> This document is intentionally more detailed than the README. Implementation must follow this document unless a new architectural decision is explicitly recorded here first.

---

# 0. Absolute Design Contract

The Fight system is a **runtime combat controller**.

It is NOT:

- a prerecorded combat animation;
- an AI that generates a fight and saves it;
- an offline simulator;
- a replacement for MoCap recording;
- a replacement for MoCap scenes;
- a replacement for MoCap playback;
- a general-purpose Minecraft AI framework.

The fundamental pipeline is:

```
Existing Recording
        ↓
Existing Scene
        ↓
Existing Playback
        ↓
Runtime Mocap Actor(s)
        ↓
Fight Runtime Controller
        ↓
Live Target / Live World
        ↓
Live Movement + Combat Decisions
```

The recording provides the character's available identity/state/inventory/animation material. The Fight runtime decides what happens **after the Fight is started**.

No complete future fight is calculated in advance.

---

# 1. Existing-System Preservation Contract

The first priority is not breaking existing MoCap.

The following existing capabilities must remain functional:

- Recording
- Saving/loading recordings
- Recording playback
- Scene creation
- Nested scenes
- Scene playback
- Playback offsets
- Playback delays
- Player naming/skin handling
- Existing action data
- Existing settings
- Existing commands
- Existing API/event behavior
- Fabric build
- NeoForge build, unless a clearly documented platform-specific limitation exists

The Fight feature must be additive.

### Forbidden architectural shortcut

Do not rewrite the entire playback engine merely because Fight needs additional runtime control.

First inspect and reuse the current playback/entity lifecycle.

---

# 2. Runtime vs Recording Separation

This is the most important boundary in the entire project.

## Recording layer

Responsible for:

- What was recorded
- Recorded movement
- Recorded actions
- Recorded equipment
- Recorded states
- Playback source data

## Fight layer

Responsible for:

- Current target
- Current target position
- Current combat state
- Current combat distance
- Current attack cooldown
- Current health/combat status
- Runtime movement
- Runtime item choice
- Runtime attack decisions
- Runtime death/reset handling

### Rule

Fight runtime state must never silently mutate the original recording.

A Fight can use the same recording multiple times.

Example:

```
WarriorRecording
   ├── Fighter A
   ├── Fighter B
   ├── Fighter C
   └── Fighter D
```

Every fighter gets independent runtime state.

---

# 3. Fight Definition

A saved Fight is a configuration object, not a recording.

Minimum conceptual fields:

- `id/name`
- `version`
- `source scene reference(s)`
- `target reference(s)`
- `team/faction information`
- `power` (1–10)
- `attack speed`
- `attack range`
- `detection range`
- `movement speed`
- `damage modifier`
- `knockback modifier`
- `targeting mode`
- `combat mode`
- `inventory policy`
- `death policy`
- `reset policy`
- `spacing policy`
- `terrain/recovery policy`
- `start configuration`
- `runtime flags`

Only fields actually needed by the final implementation should be serialized.

Do not serialize live entity references that become invalid after restart.

---

# 4. Fight Lifecycle

A Fight must have an explicit lifecycle.

Recommended states:

```
UNLOADED
   ↓
LOADED
   ↓
READY
   ↓
STARTING
   ↓
RUNNING
   ↓
STOPPING
   ↓
STOPPED
   ↓
RESETTING
   ↓
READY
```

Possible terminal/error states:

```
FAILED
INVALID_CONFIGURATION
MISSING_SOURCE
MISSING_TARGET
```

A stopped Fight must not continue ticking.

A completed Fight must not continue attacking.

A reset must clear all runtime combat state.

---

# 5. Start Semantics

Starting a Fight must be deterministic.

Before entering RUNNING:

1. Validate Fight exists.
2. Validate source scene(s).
3. Validate target definition.
4. Validate required playback/source data.
5. Resolve runtime participants.
6. Capture reset state.
7. Apply initial inventory/armor/state.
8. Place actors safely.
9. Initialize target selection.
10. Initialize combat state.
11. Register runtime tick processing.
12. Enter RUNNING.

If any required step fails, do not start a partially initialized fight.

---

# 6. Stop Semantics

Stopping a Fight must:

- stop combat decisions;
- stop target chasing;
- stop attack scheduling;
- unregister/disable runtime processing;
- preserve enough state for reset;
- not corrupt the source recording;
- not silently delete saved Fight configuration.

Stopping is different from resetting.

---

# 7. Reset Semantics

Reset must restore the Fight to its configured initial state.

Reset should:

- stop the Fight;
- remove/disable active combat behavior;
- clear targets;
- clear attack cooldowns;
- clear combat states;
- restore actor positions;
- restore orientation;
- restore inventory;
- restore armor;
- restore held items;
- restore health/state;
- restore playback baseline;
- clear dead flags;
- clear temporary runtime data;
- prepare the Fight to start again.

Repeated:

```
START → STOP → RESET → START
```

must remain safe.

Also test:

```
START → DEATH → RESET → START
```

---

# 8. Participant Model

A runtime fighter should conceptually contain:

- Stable runtime ID
- Source recording/scene reference
- Runtime entity reference
- Team/faction
- Current target
- Current state
- Health snapshot
- Inventory snapshot
- Armor snapshot
- Main-hand snapshot
- Off-hand snapshot
- Initial position
- Initial rotation
- Movement parameters
- Combat parameters
- Attack cooldown
- Recovery timer
- Last known target position
- Death flag
- Active/inactive flag

Do not store mutable runtime state inside immutable source recording data.

---

# 9. Participant Independence

Every scene instance must be independent.

If one recording is used ten times:

- one fighter dying must not kill the others;
- one fighter changing item must not change the others;
- one fighter changing target must not change the others;
- one fighter moving must not alter another fighter's reset position;
- one fighter's runtime state must not mutate the original recording.

This is a mandatory isolation requirement.

---

# 10. Target System

Target selection happens at runtime.

A valid target must satisfy all applicable rules:

- Exists
- Is alive
- Is available
- Is an enemy according to the configured relationship
- Is inside the allowed search/detection rules
- Is not the fighter itself
- Is not a dead/stale runtime reference

Target selection modes should be extensible.

Initial modes can include:

- NEAREST
- CURRENT_TARGET
- LOWEST_HEALTH
- ATTACKER
- RANDOM_VALID
- FIXED_TARGET
- TEAM_PRIORITY

Do not implement every possible mode immediately if the existing command/config architecture would become unnecessarily complex. Start with the modes required by the feature and keep the selector extensible.

---

# 11. Target Validation

A target must be revalidated whenever:

- it dies;
- it disappears;
- it becomes invalid;
- it changes dimension/world;
- the Fight is reset/stopped;
- the target becomes unavailable;
- configured range/relationship rules invalidate it.

Do not keep attacking a stale UUID/entity reference.

---

# 12. Target Acquisition Strategy

Do not scan the entire world every tick.

Preferred order:

1. Existing Fight participant list
2. Known configured targets
3. Nearby candidate search
4. Target selector evaluation

Use squared-distance checks where possible.

Cache a valid target until it becomes invalid or a configured retarget condition occurs.

---

# 13. Combat State Machine

Initial states:

```
IDLE
SEARCH_TARGET
CHASE
POSITION
ATTACK
RECOVER
USE_ITEM
RETREAT
DEAD
DISABLED
```

### Example transitions

```
IDLE → SEARCH_TARGET

SEARCH_TARGET → CHASE
SEARCH_TARGET → IDLE

CHASE → POSITION
CHASE → SEARCH_TARGET
CHASE → DEAD

POSITION → ATTACK
POSITION → CHASE
POSITION → SEARCH_TARGET

ATTACK → RECOVER
ATTACK → DEAD

RECOVER → CHASE
RECOVER → POSITION
RECOVER → USE_ITEM
RECOVER → DEAD

USE_ITEM → CHASE
USE_ITEM → POSITION
USE_ITEM → DEAD

Any active state → DEAD
```

State transitions must have timeout/failsafe behavior.

No state may become permanently stuck.

---

# 14. Combat Decision Model

Do not build a general-purpose AI planner.

Use deterministic lightweight rules.

A combat decision may consider:

- Target distance
- Target health
- Fighter health
- Available weapons
- Available food
- Shield availability
- Attack cooldown
- Current state
- Power level
- Terrain
- Target visibility/availability

Decision priority should be explicit so behavior is reproducible and debuggable.

---

# 15. Power System

Power is a normalized configuration from 1 to 10.

Power must not secretly mean "unlimited intelligence".

It should influence selected combat parameters such as:

- reaction delay;
- attack timing;
- target retention;
- movement aggressiveness;
- attack precision;
- recovery behavior;
- decision frequency;
- optional damage modifier if configured.

Do not multiply every statistic blindly by Power.

Power must have documented formulas or bounded mappings.

No value should become zero, negative, NaN, infinite, or unreasonably large.

---

# 16. Attack Model

Separate:

1. Decision to attack
2. Attack execution
3. Hit validation
4. Damage application
5. Knockback
6. Recovery/cooldown

Never treat "animation started" as proof that damage happened.

A hit must be validated against the live target.

---

# 17. Attack Range

Attack range must be configurable but bounded by safe minimum/maximum values.

The system should account for:

- distance;
- target hitbox;
- fighter reach;
- weapon type;
- line/visibility rules where applicable.

A fighter should not repeatedly attack a target that is clearly outside the valid hit range.

---

# 18. Attack Speed

Attack speed is a runtime cooldown.

It must not bypass Minecraft/server combat safety indefinitely.

The implementation must define:

- minimum legal cooldown;
- recovery duration;
- behavior when target leaves range;
- behavior when target dies during cooldown.

Avoid scheduling thousands of independent timers. Prefer centralized runtime state/tick evaluation.

---

# 19. Damage

Damage must be calculated from actual runtime combat state.

Potential inputs:

- Base weapon damage
- Power modifier
- Configured damage modifier
- Armor
- Target state
- Attack type

Do not manually duplicate Minecraft's entire damage system unless required.

Prefer invoking the appropriate existing game/entity damage mechanics so armor, effects and other normal rules remain coherent.

---

# 20. Knockback

Knockback should be applied only after a valid hit.

It must be configurable and bounded.

Repeated attacks must not create runaway velocity.

Special handling may be needed for:

- air targets;
- edges;
- walls;
- multiple simultaneous hits.

---

# 21. Weapon Selection

The fighter can only choose from its allowed combat inventory.

Initial runtime priorities:

### Melee
Use when target is inside melee engagement range.

### Ranged
Use when target is outside melee range and a valid ranged weapon/ammunition setup exists.

### Shield
Use according to defensive rules and available off-hand configuration.

### Food
Use when health is below the configured threshold and a valid consumable exists.

The final implementation must verify actual item availability rather than assuming an item exists.

---

# 22. Inventory Isolation

Fight must use a private runtime inventory snapshot.

Rules:

- Battlefield pickups are ignored.
- Dead fighter drops are ignored as loot.
- Another fighter's inventory cannot be consumed.
- Runtime switching cannot mutate the original recording.
- Reset restores the original combat inventory.

If the underlying playback implementation has no true inventory container for a Mocap actor, create the smallest safe runtime abstraction required rather than weakening these rules.

---

# 23. Death Handling

Death is an explicit runtime transition.

On death:

1. Mark fighter DEAD.
2. Stop target selection.
3. Stop attack scheduling.
4. Stop movement decisions.
5. Remove from active combat candidate list.
6. Prevent further combat actions.
7. Apply configured death presentation.
8. Preserve reset snapshot.

Other fighters must immediately invalidate the dead fighter as a target.

---

# 24. Death Drops / Loot Policy

The default Fight policy should prevent combat participants from gaining equipment through battlefield looting.

The implementation must explicitly decide how drops are handled.

Preferred cinematic-safe behavior:

- No automatic pickup.
- No automatic loot transfer.
- Optional suppression/cleanup of Mocap death drops if required by the existing actor implementation.
- Real player drops must remain governed by normal Minecraft behavior unless the user explicitly configures a separate future rule.

Do not globally change vanilla player loot behavior.

---

# 25. Real Player Combat

Real players are live entities.

For Mocap vs Real Player:

- Mocap reads the player's live position/state.
- Fight does not control the player's movement.
- Fight does not record the player's future movement.
- Targeting follows the player dynamically.
- Damage must respect the configured combat rules.

The system must avoid unexpectedly changing unrelated players outside the active Fight.

---

# 26. Mocap vs Mocap Combat

For Mocap vs Mocap:

- Both participants are runtime-controlled.
- Neither participant has a prerecorded combat path.
- Each participant evaluates the other live.
- Target changes are possible.
- Death removes a participant from active combat.

This is the primary fully autonomous cinematic use case.

---

# 27. Multiple Teams

The architecture should support more than two teams.

Conceptually:

```
Team A
Team B
Team C
Team D
...
```

Target validity should be based on team relationship rather than hardcoded "red vs blue".

Possible relationships:

- FRIENDLY
- HOSTILE
- NEUTRAL

Initial implementation may only expose the relationships actually needed by the first release.

---

# 28. Group Teleport

A selected Mocap group can be moved to a runtime destination.

Requirements:

- Never stack all actors at one exact position.
- Find safe nearby positions.
- Check collision/space where practical.
- Respect terrain.
- Keep approximately 1–2 block spacing where possible.
- Preserve actor orientation unless configured otherwise.
- Do not overwrite reset positions unless explicitly requested.

---

# 29. Fishing Rod Destination Control

Fishing Rod control is a proposed playback convenience feature.

Flow:

```
Cast Rod
   ↓
Detect Valid Landing
   ↓
Resolve Destination
   ↓
Resolve Selected Mocap Group
   ↓
Generate Formation
   ↓
Validate Positions
   ↓
Teleport Group
```

Important:

- A rod cast must not accidentally teleport unrelated players.
- Invalid destinations must be rejected safely.
- The system must handle unloaded/invalid chunks appropriately.
- The destination must not overwrite each actor's saved reset location.

If the existing event system cannot reliably identify the intended rod interaction, use the project's available event hooks rather than adding fragile polling.

---

# 30. Formation / Spacing Algorithm

Formation placement should be deterministic.

Preferred strategy:

1. Start from destination.
2. Generate candidate offsets in rings/spiral/grid order.
3. Validate each candidate.
4. Reject occupied/unsafe positions.
5. Assign the first valid position.
6. Continue until all actors are placed.
7. If insufficient valid positions exist, place as many safely as possible and report the failure.

Do not silently teleport actors into blocks or into each other.

---

# 31. Movement

Movement is runtime-controlled.

Minimum behavior:

- Follow live target position.
- Stop within combat range.
- Recalculate when target moves.
- Recover after small displacement/obstruction.
- Avoid permanent oscillation.

Movement must not fight against the normal playback system indefinitely.

Therefore the implementation must define when Fight owns movement and when normal playback movement is temporarily suspended/overridden.

This ownership boundary must be explicit in code.

---

# 32. Navigation

Navigation should start simple.

Initial priority:

1. Direct movement if path is clear.
2. Small obstacle/step handling.
3. Local recovery.
4. Optional bounded pathfinding if required.

Do not immediately implement a full Minecraft pathfinding engine if a simpler controller is sufficient.

Pathfinding must have:

- timeout;
- maximum search cost;
- failure fallback;
- target revalidation.

---

# 33. Hole / Trap Recovery

If an actor falls into a small hole:

- detect abnormal vertical displacement;
- determine whether the target remains valid;
- attempt bounded recovery;
- prevent infinite recovery loops;
- return to CHASE when recovered.

Do not place arbitrary blocks everywhere.

If block placement is implemented later, it must be explicitly permission/configuration controlled.

---

# 34. Terrain Safety

Fight must never unintentionally:

- destroy large areas;
- place unlimited blocks;
- modify protected areas;
- bypass world protections;
- create permanent terrain damage.

World modification must be isolated behind a clearly defined policy.

Default should be **no destructive world modification**.

---

# 35. Playback Ownership

During normal playback:

```
Playback Controller → Actor movement/state
```

During Fight-controlled movement:

```
Fight Controller → Actor combat movement/state
```

During Fight-controlled combat animation:

```
Fight Controller → Combat action
Playback/Actor Layer → Visual representation
```

The implementation must avoid two controllers writing contradictory state at the same time.

This is a critical integration point.

---

# 36. Action Integration

Where the existing Action system can represent a combat action, reuse it.

Candidate action concepts:

- ATTACK
- HIT
- BLOCK
- USE_ITEM
- SWITCH_ITEM
- KNOCKBACK
- TARGET_CHANGE
- CHASE
- RETREAT
- DEATH

Do not create duplicate action types if an existing generic action model can represent the same event safely.

Combat runtime state and visual/action events are separate concepts.

---

# 37. Animation vs Combat Logic

Never couple damage directly to a visual animation frame unless the existing engine guarantees deterministic synchronization.

Preferred model:

```
Combat Decision
      ↓
Attack Start
      ↓
Attack Window
      ↓
Live Hit Validation
      ↓
Damage
      ↓
Recovery
```

This keeps cinematic visuals and actual game mechanics synchronized without making animation data responsible for authoritative damage.

---

# 38. Scene vs Scene

A Fight can reference scenes on both sides.

Important:

- Nested scenes must resolve correctly.
- Each scene instance must remain independent.
- Player names must not collide in a way that breaks runtime identification.
- Offsets must be applied before combat initialization.
- Runtime entity IDs must be used for active combat references.

Do not use display names as the sole identity of a fighter.

---

# 39. Duplicate Names

Two fighters may visually have the same player name/skin.

Therefore:

- display name ≠ runtime identity;
- runtime UUID/entity identity must be used internally;
- command names must resolve to the configured Fight/scene/recording identifier;
- name collisions must not cause one fighter to control another.

---

# 40. Persistence Format

Fight files must be versioned.

Conceptually:

```
{
  "formatVersion": 1,
  "fightId": "...",
  "sources": [...],
  "targets": [...],
  "rules": {...},
  "reset": {...}
}
```

The exact serialization format must follow the repository's existing file conventions.

Never invent a second persistence mechanism without checking the existing file system first.

Future format migration must be possible.

---

# 41. Persistence Validation

When loading a Fight:

- validate format version;
- validate required fields;
- reject malformed values;
- clamp safe numeric ranges;
- report missing recording/scene references;
- do not crash the entire server because one Fight file is malformed.

A malformed Fight should fail independently.

---

# 42. Configuration Validation

Before Fight start:

### Required
- Fight exists
- Source exists
- Target exists when required
- At least one valid participant exists

### Numeric validation
- Power: 1–10
- Ranges: positive
- Speeds: bounded positive values
- Damage modifier: bounded
- Knockback modifier: bounded
- Timers: non-negative

### Runtime validation
- No duplicate runtime participant IDs
- No self-target
- No invalid dimension reference
- No stale runtime entity reference

---

# 43. Commands

Fight must extend the existing command architecture.

Conceptual structure:

```
/mocap playing fight list
/mocap playing fight create <name>
/mocap playing fight info <name>
/mocap playing fight start <name>
/mocap playing fight stop <name>
/mocap playing fight reset <name>
/mocap playing fight remove <name>
```

Additional edit commands may be required:

```
... setScene
... addScene
... setTarget
... setPower
... setAttackSpeed
... setAttackRange
... setDetectionRange
... setMovementSpeed
... setTargetMode
... setTeam
... setDamage
... setKnockback
... setSpacing
... setInventoryPolicy
... setDeathPolicy
```

These are conceptual commands. Exact syntax must follow the existing command tree and parser conventions.

---

# 44. Command Safety

Commands must:

- provide suggestions;
- validate names;
- provide clear errors;
- avoid silently overwriting existing Fight definitions;
- refuse invalid configurations;
- distinguish saved configuration from active runtime state.

Removing a Fight must stop its active runtime instance first or explicitly refuse removal while active.

---

# 45. Permissions / Authority

Fight control is an administrative/cinematic operation.

The implementation must use the existing command permission model.

Do not create a new incompatible permission framework unless required by the current project.

---

# 46. Tick Architecture

Use existing server tick events.

Do not create one independent scheduled task per fighter.

Prefer:

```
FightManager
   ↓
Active Fight Registry
   ↓
Active Participants
   ↓
Per-tick lightweight update
```

Within a tick:

1. Remove invalid/dead participants.
2. Process due combat timers.
3. Update target validity.
4. Update movement where required.
5. Process attack/use-item decisions.
6. Apply bounded state transitions.

Expensive operations should be throttled.

---

# 47. Tick Scheduling

Different work can use different frequencies.

Example strategy:

- Position/combat timing: frequent
- Target validation: periodic
- Target discovery: less frequent
- Expensive navigation: only when needed
- Formation calculation: only on teleport/start
- Persistence: only on configuration changes or explicit save

Exact tick intervals must be benchmarked.

Do not treat example intervals as permanent constants without testing.

---

# 48. Performance Rules

No arbitrary fighter-count cap is part of the design.

However, the system must be performance-conscious.

Required optimizations:

- Active participant registries
- No repeated full-world scans
- Squared distance calculations
- Cached targets
- Reusable collections where appropriate
- Minimal temporary allocation in tick loops
- Dead/inactive participant removal
- Lazy navigation
- Event-driven invalidation where possible

---

# 49. Performance Testing

Test progressively:

- 1 fighter
- 2 fighters
- 10 fighters
- 25 fighters
- 50 fighters
- 100 fighters
- 250 fighters
- 500 fighters where hardware permits

Measure:

- server tick time;
- memory;
- entity count;
- target-search cost;
- navigation cost;
- network traffic where relevant;
- playback impact;
- combat update cost.

Do not publish a hard maximum based on assumption.

---

# 50. Multiplayer Synchronization

Server authority owns:

- Target state
- Combat state
- Movement decisions
- Attack timing
- Damage
- Death
- Inventory rules
- Reset

Clients only render the resulting state.

Do not require clients to run the Fight AI for correctness.

---

# 51. Dimension / World Handling

The Fight system must account for world identity.

A fighter and target in different dimensions cannot be treated as ordinary nearby combat targets.

Required handling:

- dimension mismatch;
- unloaded world;
- invalid entity;
- teleport between dimensions;
- reset to original dimension.

Default behavior should be safe termination/reselection rather than invalid cross-dimension movement.

---

# 52. Chunk / Loading Safety

Do not force uncontrolled chunk loading.

When navigation or target tracking requires a location:

- check whether the relevant area is available;
- avoid repeatedly loading distant chunks;
- fail/retry with bounded behavior;
- do not create an infinite chunk-loading loop.

---

# 53. Entity Lifetime

Every runtime fighter reference must be invalidated when its entity disappears.

Never assume:

```
UUID exists → entity is still valid
```

Resolve/validate runtime entities safely.

---

# 54. Concurrency / Re-entrancy

The Fight manager must tolerate:

- Fight start called twice;
- Fight stop called twice;
- Reset during combat;
- Target death during attack processing;
- Fighter death during target selection;
- Scene removal while Fight is active;
- Server shutdown/reload.

No duplicate runtime registration may occur.

No Fight may tick twice in the same manager cycle.

---

# 55. Error Isolation

A failure in one Fight must not crash:

- the server;
- another Fight;
- normal MoCap playback;
- recording;
- unrelated scenes.

Catch and report recoverable Fight-specific failures at the correct boundary.

Do not hide programming errors with broad silent exception handling.

---

# 56. Logging / Debugging

Provide useful debug information.

Potential categories:

- Fight lifecycle
- Participant lifecycle
- Target selection
- State transition
- Attack decision
- Item switch
- Death/reset
- Navigation failure
- Persistence failure

Debug logging must be disabled or appropriately throttled by default.

Avoid logging every fighter every tick in normal mode.

---

# 57. Debug / Inspection Command

An information/debug view should be able to report:

- Fight state
- Number of participants
- Active/dead count
- Current targets
- Current combat state
- Target distance
- Current held item
- Attack cooldown
- Last error

This is especially important for diagnosing cinematic scenes.

---

# 58. API Boundary

The existing public API must not be broken.

If Fight functionality is exposed publicly, use a new additive API surface.

Do not silently change existing API contracts.

Internal Fight classes should remain internal unless there is a clear API use case.

---

# 59. Fabric / NeoForge Separation

Common logic should stay in the common module where possible.

Loader-specific code should be isolated.

Do not put Fabric-only classes into common.

Do not force NeoForge-specific APIs into common abstractions.

Fight core should depend on shared abstractions where the project architecture allows it.

---

# 60. Version Discipline

The repository currently declares:

- Minecraft 26.1
- Java 25
- Fabric
- NeoForge

Implementation must use the repository's actual Gradle configuration as the authority.

Do not assume compatibility with 1.20.1, 1.21.x, or another Minecraft version without explicitly porting and validating it.

Do not change the target Minecraft version merely to simplify Fight implementation.

---

# 61. Testing Matrix

Every major feature requires tests for:

## Basic
- Create Fight
- Save Fight
- Load Fight
- Start
- Stop
- Reset
- Remove

## Combat
- Melee
- Ranged
- Shield
- Food
- Target movement
- Target death
- Fighter death
- Retargeting

## Relationships
- Mocap vs Player
- Mocap vs Mocap
- Scene vs Scene
- Multiple teams
- Multiple simultaneous fights

## Runtime
- Moving target
- Hole
- Obstacle
- Unloaded area
- Dimension mismatch
- Missing entity
- Server restart

## Regression
- Existing recording
- Existing playback
- Existing scenes
- Nested scenes
- Existing commands
- Existing settings
- Existing API behavior

---

# 62. Manual Cinematic Test Scenario

Before declaring the feature complete, run a complete real-world scenario:

1. Record a fighter.
2. Save recording.
3. Build a scene.
4. Duplicate the scene several times.
5. Assign teams.
6. Create Fight.
7. Set power.
8. Set target.
9. Start playback/Fight.
10. Move target.
11. Verify fighters follow target.
12. Verify item switching.
13. Kill one fighter.
14. Verify dead fighter stops acting.
15. Verify remaining fighters retarget correctly.
16. Stop.
17. Reset.
18. Verify all actors return to initial state.
19. Start again.
20. Verify the second run works independently.

---

# 63. Failure Cases That Must Be Explicitly Handled

The implementation must have a defined result for:

- Fight does not exist
- Fight file corrupted
- Scene does not exist
- Recording does not exist
- Target does not exist
- Target dies before Fight starts
- All targets die
- Fighter dies before acquiring target
- Fighter entity disappears
- World unloads
- Dimension changes
- Destination invalid
- No safe formation positions
- No valid weapon
- No valid food
- Target unreachable
- Navigation timeout
- Fight started twice
- Fight reset while attacking
- Server stops during Fight

---

# 64. Security / Abuse Prevention

Fight must not become an unintended griefing mechanism.

Administrative configuration should prevent:

- unauthorized world modification;
- unlimited item duplication;
- inventory duplication through reset;
- infinite damage loops;
- uncontrolled entity creation;
- accidental targeting of unrelated players;
- cross-world targeting bugs.

Reset must not duplicate items.

---

# 65. Inventory Duplication Prevention

This requires explicit attention.

When restoring inventory:

1. Determine whether the actor already owns its runtime inventory.
2. Clear/replace only the Fight-owned runtime inventory.
3. Restore from the saved snapshot exactly once.
4. Do not add the snapshot on top of an existing copy.
5. On repeated reset, the result must remain identical.

Test:

```
START → RESET → RESET → RESET
```

Inventory count must not increase.

---

# 66. Target Isolation

A Fight must only affect configured targets.

For a Fight targeting Player A:

- Player B must not accidentally become target merely because Player B is closer unless the targeting mode explicitly permits it.
- Unrelated players must not receive damage.
- Unrelated Mocap actors must not be pulled into the Fight.

This is a critical safety invariant.

---

# 67. Fight Isolation

Multiple Fights may exist at the same time.

Fight A must not:

- control Fight B's actors;
- change Fight B's targets;
- reset Fight B;
- consume Fight B's inventory;
- modify Fight B's state.

Runtime ownership must be explicit.

---

# 68. Runtime Ownership Registry

Every active actor should have one authoritative owner:

```
Runtime Actor → Fight ID
```

This prevents two active Fight controllers from issuing contradictory commands to the same actor.

If an actor is already owned by another active Fight, the second Fight should reject or explicitly transfer ownership according to a defined policy.

Default should be reject.

---

# 69. Reset Snapshot Ownership

Reset data belongs to the runtime Fight instance.

Do not overwrite the source recording.

Do not overwrite the permanent scene definition unless the user explicitly edits and saves it.

---

# 70. Backward Compatibility

Existing recording files must remain readable.

Existing scene files must remain readable.

Fight data is additive.

If Fight configuration changes format later:

```
Old Fight → Migration → New Fight
```

Do not silently reinterpret old values.

---

# 71. Migration Strategy

Every persisted Fight format must include a format version.

When loading an older version:

- migrate if supported;
- otherwise provide a clear error;
- never partially load dangerous data.

Migration code must be isolated from runtime combat code.

---

# 72. Documentation Discipline

Whenever implementation changes one of these:

- command syntax;
- persistence schema;
- package ownership;
- lifecycle;
- combat rules;
- target rules;
- inventory rules;
- compatibility;
- reset semantics;

update this blueprint before continuing.

The implementation and blueprint must not drift apart.

---

# 73. Implementation Order

## Phase 0 — Architecture and documentation
- Blueprint
- README
- Existing code audit
- Exact playback lifecycle mapping
- Existing persistence mapping
- Existing entity implementation mapping

## Phase 1 — Data model
- Fight definition
- IDs
- persistence
- validation
- lifecycle

## Phase 2 — Playback integration
- Fight registry
- Playback ownership
- Start/stop/reset
- Runtime participant creation

## Phase 3 — Combat runtime
- Fighter state
- Target system
- Runtime inventory
- Weapon selection
- Attack/cooldown
- Damage/death

## Phase 4 — Movement
- Follow target
- Combat positioning
- Spacing
- Recovery
- Bounded navigation

## Phase 5 — Group controls
- Group teleport
- Formation
- Fishing Rod destination control

## Phase 6 — Commands
- Creation
- Editing
- Info
- Start/stop/reset/remove
- Suggestions
- Validation

## Phase 7 — Performance
- Tick profiling
- Entity scaling
- Target search optimization
- Navigation optimization

## Phase 8 — Regression
- Existing MoCap tests
- Existing scene/playback behavior
- Existing command behavior
- Loader builds

## Phase 9 — Final hardening
- Failure cases
- Inventory duplication
- ownership isolation
- persistence migration
- restart recovery
- multiplayer testing

---

# 74. Implementation Method

The work must proceed in small technically coherent changes, but the overall feature should be implemented as quickly as practical.

For every implementation step:

1. Inspect the existing code first.
2. Identify the exact integration point.
3. Reuse existing infrastructure.
4. Make the smallest safe change.
5. Compile.
6. Run relevant tests.
7. Inspect errors.
8. Fix before moving to the next subsystem.
9. Update this blueprint/checklist.
10. Continue.

Do not make a large blind rewrite of unrelated files.

---

# 75. No-GUESS Rule

If an implementation decision depends on an existing MoCap behavior that has not yet been inspected:

**STOP AND INSPECT THE CODE.**

Do not guess:

- class ownership;
- method names;
- file format;
- entity implementation;
- playback lifecycle;
- command registration;
- API contracts;
- loader-specific behavior.

The repository is the source of truth for implementation details.

---

# 76. Change-Control Rule

A change is allowed when it is:

- required by the Fight feature;
- compatible with existing behavior;
- validated against the current architecture.

A change is not allowed merely because it is "cleaner" if it risks unrelated regressions.

If an architectural change becomes necessary:

1. Document the reason.
2. Update this blueprint.
3. Identify affected existing systems.
4. Implement the change.
5. Run regression validation.

---

# 77. Completion Checklist

The following must all be true before final completion:

- [ ] Existing MoCap recording still works
- [ ] Existing MoCap playback still works
- [ ] Existing scenes still work
- [ ] Fight can be created
- [ ] Fight can be saved
- [ ] Fight can be loaded
- [ ] Fight can be started
- [ ] Fight can be stopped
- [ ] Fight can be reset
- [ ] Fight can be removed safely
- [ ] Scene participants resolve correctly
- [ ] Runtime actor identity is isolated
- [ ] Targets resolve correctly
- [ ] Targets update dynamically
- [ ] Target death is handled
- [ ] Fighter death is handled
- [ ] Melee works
- [ ] Ranged behavior works where supported
- [ ] Shield behavior works where supported
- [ ] Food behavior works where supported
- [ ] Inventory is isolated
- [ ] Battlefield looting is disabled for Mocap fighters
- [ ] Inventory reset does not duplicate items
- [ ] Movement follows live targets
- [ ] Combat range is respected
- [ ] Spacing works
- [ ] Group teleport works
- [ ] Formation placement is safe
- [ ] Fishing Rod control works if enabled
- [ ] Terrain recovery works within defined limits
- [ ] No uncontrolled world modification occurs
- [ ] Multiple Fights are isolated
- [ ] Duplicate Fight starts are rejected/safely handled
- [ ] Server restart does not corrupt saved Fight data
- [ ] Malformed Fight data does not crash the server
- [ ] No stale target references remain active
- [ ] No runtime fight loop remains after completion
- [ ] Performance has been measured
- [ ] Fabric build passes
- [ ] NeoForge build passes, where applicable
- [ ] Existing regression checks pass
- [ ] Documentation matches the final implementation

---

# 78. Final Architectural Invariants

These statements must remain true throughout implementation:

1. **Fight is runtime, not prerecorded.**
2. **The source recording is immutable during combat.**
3. **Every runtime fighter is independently stateful.**
4. **Target selection is live.**
5. **Movement is live.**
6. **Combat is server-authoritative.**
7. **Inventory is isolated.**
8. **Dead fighters cannot continue attacking.**
9. **Reset is deterministic and cannot duplicate inventory.**
10. **One Fight cannot control another Fight's actors.**
11. **Existing MoCap behavior has priority over new convenience features.**
12. **No artificial fighter-count limit is hardcoded.**
13. **Expensive work is bounded and profiled.**
14. **Invalid configuration fails safely.**
15. **The implementation never guesses about unseen existing code.**
16. **This blueprint is updated whenever an approved architectural decision changes.**

---

# 79. Final Definition of Done

The feature is complete only when a creator can:

1. Record or reuse a MoCap character.
2. Put that character into a scene.
3. Create a Fight.
4. Assign one or more source scenes.
5. Assign targets/teams.
6. Configure power and combat parameters.
7. Save the Fight.
8. Start it through MoCap Playback.
9. Watch fighters locate and follow live targets.
10. Watch fighters choose available combat items at runtime.
11. Watch attacks, damage, knockback and death happen on the live battlefield.
12. Move targets and have fighters react.
13. Run multiple fighters without a hardcoded artificial cap.
14. Teleport a selected group to a safe formation.
15. Stop and reset the Fight.
16. Start the same Fight again without state corruption or inventory duplication.
17. Run the feature without breaking the original MoCap recording/scene/playback system.

**This document remains the master implementation contract until the Fight feature is complete.**


## Implementation Progress — Phase 1

Phase 1 establishes the Fight control/data plane without pretending that runtime combat already exists.

Implemented:
1. Versioned Fight definition model.
2. Persistent Fight directory and `.mcmocap_fight` files.
3. Defensive loading: unsupported/malformed Fight files are ignored and logged.
4. CRUD lifecycle manager.
5. Runtime active-Fight registry with duplicate-start protection.
6. Server tick integration.
7. Server-stop cleanup.
8. Playback command registration.
9. Source-scene and target-player configuration primitives.

Not implemented yet:
- runtime scene-instance ownership/binding;
- actor snapshots;
- live target acquisition;
- navigation/movement;
- attack/item logic;
- death/retarget;
- deterministic reset.

The next phase must inspect and reuse the existing `PlaybackRoot`, `RecordingPlayback`, and `ScenePlayback` entity lifecycle before adding runtime actor ownership. No entity-control code should be guessed or added without that inspection.


## Implementation Progress — Phase 2

Phase 2 establishes the first real runtime ownership boundary between Fight and the existing Playback system.

Implemented:
1. Playback exposes its runtime-controlled entities internally without changing the public API v1 contract.
2. Recording playback exposes its primary runtime entity.
3. Scene playback aggregates controlled entities from nested playback instances.
4. PlaybackRoot exposes those entities to the Fight runtime internally.
5. Fight start now instantiates configured source playables through the existing Playback pipeline.
6. Each Fight playback receives its own copied playback configuration.
7. Fight playback is made damageable by disabling invulnerability for that runtime instance.
8. Fight owns the created playback roots and their actor references.
9. Fight stop/reset/server shutdown now stops owned playback roots.
10. Partial Fight startup rolls back already-created playback roots on failure.
11. Dead runtime actors are removed from the Fight actor registry.

Still deliberately not implemented:
- target acquisition;
- movement/navigation;
- attack execution;
- weapon selection;
- combat state machine;
- death snapshot/reset restoration;
- real-player combat;
- group teleport.

### Phase 2 invariant

Fight does not directly create or replace Mocap actors. It asks the existing Playback system to create them, then records ownership of those runtime instances. This preserves normal recording/scene playback behavior and gives the future combat controller a deterministic actor set.


## Implementation Progress — Phase 3B

Phase 3B establishes the first authoritative runtime combat decision/execution layer on top of the Phase 3A participant and target infrastructure.

Implemented scope:
1. Explicit per-participant combat state.
2. Runtime attack cooldown derived from configured attack speed.
3. Live target validation before attack execution.
4. Melee attack execution against the selected target only.
5. Reuse of the existing MoCap attack/swing mechanics instead of duplicating the attack implementation.
6. Dead/invalid participants leave combat immediately.
7. No movement/navigation is introduced in this phase; targets outside attack range remain in the chase/positioning state until the movement phase owns locomotion.

Still deliberately not implemented in Phase 3B:
- autonomous movement/navigation;
- runtime weapon selection;
- shield/food logic;
- damage/knockback multiplier application;
- inventory/reset snapshots;
- death presentation/drop policy;
- multi-team relationships;
- group formation teleport/Fishing Rod control.

The next movement phase must explicitly define the ownership boundary between recorded playback movement and Fight-controlled movement before locomotion code is added.


## Implementation Progress — Phase 4A

Phase 4A establishes the movement-ownership boundary and the first bounded live chase controller.

Implemented scope for this batch:
1. Fight-controlled playback instances can explicitly suppress recorded Movement/legacy movement actions while preserving non-movement recorded actions.
2. Scene playback propagates movement ownership to nested playback instances.
3. Fight runtime claims movement ownership for every controlled playback root while the Fight is active.
4. Fight-controlled actors follow a live valid target only while outside attack range.
5. Movement uses bounded per-tick displacement and normal entity collision movement; no block placement, terrain destruction, teleport spam, or uncontrolled chunk loading is introduced.
6. Fighters rotate toward their live target while chasing.
7. Looping playback does not reapply the recorded start position while Fight movement ownership is active.
8. Movement ownership is released when the Fight runtime is reset/stopped.

Deliberately not implemented in Phase 4A:
- full pathfinding;
- jump/climb planning;
- advanced obstacle navigation;
- formation spacing;
- group teleport/Fishing Rod control;
- weapon selection;
- shield/food behavior;
- damage/knockback modifiers;
- death snapshots/inventory reset snapshots.

Movement contract for this phase:
- movementSpeed is interpreted as blocks per second and converted to a bounded per-tick displacement.
- A fighter stops translating once it is inside configured attack range.
- A target in another dimension/world is never chased.
- If collision prevents the requested displacement, the controller accepts the collision result and does not bypass blocks by teleporting through them.
- The next navigation phase may add bounded local recovery/pathfinding without changing the ownership boundary.


## Implementation Progress — Phase 4B

Phase 4B adds bounded local obstacle recovery on top of the Phase 4A movement-ownership boundary.

Implemented scope for this batch:
1. Fight participants track short-lived movement obstruction state independently from combat state.
2. A target change clears stale movement obstruction state.
3. Direct chase movement measures actual horizontal displacement instead of assuming that a requested move succeeded.
4. Repeated blocked movement enters bounded local recovery after a small number of ticks.
5. Recovery uses one horizontal perpendicular probe per tick, alternating direction when necessary.
6. Recovery remains collision-respecting and bounded; it never places, breaks, or replaces blocks and never teleports through obstacles.
7. Successful forward or recovery movement clears the obstruction counter.
8. Movement failure does not cancel target acquisition or combat; the participant remains in CHASE and can continue retrying on later ticks.

Deliberately not implemented in Phase 4B:
- full pathfinding/navigation meshes;
- jumping, climbing, swimming, doors or ladders;
- multi-node route planning;
- formation/spacing logic;
- weapon selection or item-use behavior;
- damage/knockback modifiers;
- inventory/death reset snapshots.

Recovery contract:
- obstruction detection is based on actual horizontal displacement after normal entity collision handling;
- recovery displacement never exceeds the same per-tick movement cap as direct chase;
- recovery is local and deterministic, with no world-wide or distant block search;
- dimension mismatch and invalid entities still terminate movement for that tick.

## Implementation Progress — Phase 4C

Phase 4C adds a bounded local waypoint navigator after the Phase 4B direct-chase and recovery layers.

Implemented scope for this batch:
1. Fight participants can own a short-lived navigation waypoint path independently from combat state.
2. Target changes clear stale navigation state, including the current waypoint/path.
3. Navigation is only attempted after direct movement/local recovery has remained blocked for a bounded number of ticks.
4. A bounded A* search evaluates a small horizontal grid around the fighter and stops when it reaches a collision-safe position inside combat range or the best reachable progress point within the search budget.
5. Candidate nodes use normal entity collision checks and require local block support; unloaded chunks are rejected through a non-loading chunk residency check.
6. The resulting route is bounded by maximum node count, search radius, path length, and navigation timeout.
7. Navigation only moves through already-loaded terrain and never places/breaks/replaces blocks, teleports actors, or requests distant chunks.
8. The navigator feeds waypoints back through the existing Fight movement controller, preserving the Phase 4A/4B movement-ownership boundary.
9. A failed/expired route falls back to normal CHASE behavior without cancelling target acquisition or combat.
10. Replanning is throttled and target movement invalidates stale routes.

Deliberately not implemented in Phase 4C:
- vanilla PathNavigation/Goal integration for Mob entities;
- jumping, climbing, swimming, doors or ladders;
- vertical multi-level route planning;
- formation/spacing logic;
- weapon selection or item-use behavior;
- damage/knockback modifiers;
- inventory/death reset snapshots;
- group teleport/Fishing Rod control.

Navigation contract:
- the search is horizontal and bounded; it is not a general-purpose pathfinding engine;
- only already-loaded chunks are inspected;
- node collision is checked against the actual actor bounding box;
- a route never bypasses collision by teleporting;
- navigation state is runtime-only and never mutates recordings or saved Fight definitions;
- pathfinding failure is recoverable and returns control to the existing chase/recovery loop.

## Implementation Progress — Phase 5A

Phase 5A establishes the first safe Group Controls layer: explicit runtime group selection and atomic formation teleport.

Implemented scope for this batch:
1. A Fight runtime can select a group from its existing runtime participants by side without creating new entities.
2. Group teleport is a runtime control operation and does not modify saved Fight definitions or source recordings.
3. Formation slots are deterministic and generated around the requested destination with bounded spacing.
4. Every destination slot is validated before any participant is moved.
5. Destination validation checks:
   - same server dimension;
   - loaded chunks only;
   - actual participant bounding-box collision;
   - supporting block beneath the destination;
   - no invalid/dead participant.
6. If any required slot is unsafe, the entire teleport operation is rejected and no participant is moved.
7. Successful teleport applies the formation positions atomically and clears stale navigation/recovery state for the affected participants.
8. Participants face the formation center after teleport.
9. Group teleport never places/breaks blocks, loads distant chunks, or transfers a participant between dimensions.
10. Formation spacing is bounded to prevent unreasonably large group layouts.

Deliberately not implemented in Phase 5A:
- Fishing Rod destination control;
- command/UI exposure for group selection;
- persistent formation configuration;
- dynamic combat spacing during active combat;
- cross-dimension teleport;
- item/weapon behavior.

Group-control contract:
- group selection operates only on runtime participants already owned by the Fight;
- teleport is all-or-nothing;
- formation placement is collision-aware and support-aware;
- no saved recording or Fight configuration is mutated by the operation;
- later Fishing Rod/command layers must call this same group-control boundary rather than implementing separate teleport logic.

## Implementation Progress — Phase 5B

Phase 5B adds the server-authoritative Fishing Rod destination-control layer on top of the Phase 5A group-control boundary.

Implemented scope for this batch:
1. A runtime Fishing Rod binding can be armed for an already-running Fight and a specific participant side.
2. The binding is keyed to the controlling server player and is runtime-only; it is never serialized into a Fight definition or recording.
3. A Fishing Rod interaction is recognized only for the bound server player and the triggering hand actually holding a Fishing Rod.
4. Block-targeted interactions use the server-provided block hit location; air/item interactions use the server-side entity raycast and only accept a real block hit as a destination.
5. Destination selection is server-authoritative and bounded to the existing raycast distance.
6. A valid destination delegates to the existing `FightManager.teleportGroup(...)` / `FightGroupController.teleportFormation(...)` path; no duplicate teleport logic is introduced.
7. The vanilla Fishing Rod action is consumed only when the bound control successfully performs the group teleport.
8. Invalid destinations, missing fights, stopped fights, wrong items, client-side callbacks, and unarmed interactions fall through without mutating the world.
9. Bindings are cleared when the associated Fight stops/resets/removes and when the server stops.
10. Loader-specific interaction hooks remain in Fabric/NeoForge modules; the runtime binding and destination logic remain in common.

Deliberately not implemented in Phase 5B:
- command/UI exposure for arming/disarming a Fishing Rod binding;
- persistent control bindings;
- visual destination markers;
- cross-dimension teleport;
- dynamic formation spacing;
- item/weapon combat behavior.

Fishing Rod control contract:
- the Rod is a destination-control input, not a replacement for the Fight group teleport implementation;
- no fishing hook entity is spawned by the control layer;
- no block is placed/broken and no distant chunk is loaded by destination capture;
- the selected group remains limited to active runtime participants already owned by the Fight;
- successful movement remains subject to Phase 5A atomic safety validation.

## Implementation Progress — Phase 5C

Phase 5C exposes the existing Fight group-control runtime through the existing /mocap playback fight command layer.

Implemented scope:
1. control rod arm <fight> <side> arms the already implemented runtime Fishing Rod destination controller for the command source player.
2. control rod disarm removes that player's runtime rod binding.
3. control group teleport <fight> <side> <x> <y> <z> invokes the same Phase 5A atomic group-teleport boundary directly.
4. Commands require a real server player where player-bound control is required; console sources cannot arm/disarm a player's rod binding.
5. Fight must already be running before a rod binding or runtime group teleport is accepted.
6. Side is explicit (SOURCE or TARGET); no implicit team inference is introduced.
7. Coordinates are interpreted in the running Fight's participant dimension. This phase does not perform cross-dimension transfer.
8. Command execution never mutates Fight definitions, recordings, or persistent control bindings.
9. The command layer does not duplicate formation/collision logic; all movement delegates to FightManager and the existing FightGroupController.
10. Invalid fight IDs, sides, non-running fights, non-player sources, invalid numeric values, unsafe destinations, and empty/inactive groups fail without partial movement.

Deliberately not implemented in Phase 5C:
- persistent command/UI control bindings;
- graphical configuration UI;
- team/faction definition persistence;
- weapon/ranged/shield/food behavior;
- combat inventory snapshots;
- death/reset snapshot completion.

## Implementation Progress — Phase 6A

Phase 6A adds the first persistent team/faction relationship layer required for multi-team Fight targeting.

Implemented scope:
1. Every runtime Fight participant receives a stable runtime team ID.
2. Source scenes, target scenes, and configured target players can each be assigned a team ID.
3. If no explicit assignment exists, source entries default to SOURCE and target entries/player targets default to TARGET, preserving current two-side behavior.
4. Target acquisition is no longer based only on SOURCE-vs-TARGET side separation; active participants from different teams are eligible hostile candidates, while same-team participants are treated as friendly and excluded.
5. Multiple teams are therefore supported without changing the runtime participant ownership model.
6. Team IDs are persisted as version-1-compatible additive Fight configuration using ordered assignment lists aligned with the existing scene/player reference lists.
7. Team configuration changes are rejected while the Fight is running so active runtime participants cannot silently diverge from their saved configuration.
8. Existing Fight files without team assignment properties remain valid and receive the default SOURCE/TARGET teams.
9. Team assignment is administrative configuration only; it does not create new Minecraft scoreboard teams or modify vanilla player/team state.
10. The command layer exposes explicit team assignment for source scenes, target scenes, and configured target players.

Deliberately not implemented in Phase 6A:
- FRIENDLY/HOSTILE/NEUTRAL relation matrices;
- dynamic runtime team reassignment;
- scoreboard/team integration;
- team-specific combat modifiers;
- ranged/shield/food behavior;
- inventory snapshot/reset completion.

## Implementation Progress — Phase 6B

Phase 6B establishes Fight-owned runtime combat equipment without inventing a full inventory container that the current MoCap recording format does not provide.

Repository audit findings:
1. Existing recordings persist combat-visible equipment through ChangeItem: main hand, off hand, feet, legs, chest, head, body and saddle.
2. The recording format does not currently persist the player's complete inventory/hotbar as a Fight-addressable container.
3. During normal playback ChangeItem may continue to replay recorded equipment changes, so Fight combat ownership needs an explicit suppression boundary just like movement ownership.
4. FakePlayer is a ServerPlayer and therefore can use vanilla attack mechanics with its currently equipped main-hand item.

Implemented scope:
1. Fight playback explicitly owns combat equipment while a Fight is active.
2. Recording ChangeItem actions are suppressed only while Fight equipment ownership is active; unrelated state/actions continue to replay.
3. Each Fight participant captures a private runtime equipment snapshot from its spawned actor after recording initialization.
4. Snapshot covers all eight slots already supported by ChangeItem and stores copied ItemStacks, never references into recording data.
5. The participant can restore its Fight-owned equipment snapshot idempotently.
6. Initial weapon selection is intentionally limited to the recorded/equipped main-hand and off-hand items because the current recording format exposes no complete inventory pool.
7. Melee selection prefers a non-empty hand whose item exposes positive ATTACK_DAMAGE through the actor's equipped state; otherwise it preserves the recorded main-hand choice.
8. Fight-owned equipment is not re-applied every tick; Fight-owned combat actions may legitimately change the runtime equipment state. The captured snapshot remains the reset/source baseline.
9. Stop/reset releases equipment ownership before normal playback teardown; the source recording is never mutated.
10. No battlefield pickup, loot transfer, generated item, or external inventory injection is introduced.

Deliberately not implemented in Phase 6B:
- synthetic full inventory/hotbar persistence;
- ranged ammunition management;
- shield timing;
- food consumption;
- battlefield pickup/loot;
- death-drop suppression;
- damage/knockback multiplier application;
- persistent custom loadouts.

A later full-inventory phase must extend the recording/runtime data model explicitly rather than pretending ChangeItem contains slots that it does not record.



## Implementation Progress — Phase 6C

Phase 6C adds deterministic melee-hand selection using only equipment already captured by the Phase 6B runtime snapshot.

Repository/API verification:
1. Minecraft 26.1 exposes resolved ItemStack attribute modifiers through the ItemStack modifier API; the Fight implementation must inspect the actual equipped ItemStack rather than infer weapons from item names.
2. The existing Swing attack path ultimately uses the actor's normal main-hand combat mechanics.
3. The current recording format exposes only the eight ChangeItem equipment slots, so Phase 6C does not invent a hidden hotbar/inventory.

Implemented scope:
1. Determine whether each hand contains a melee-capable item by inspecting the resolved ATTACK_DAMAGE modifier on the actual ItemStack.
2. Prefer the current main-hand weapon when valid.
3. If main hand is not melee-capable but off-hand is, swap the two equipped hand stacks at runtime so the valid weapon becomes main hand.
4. If neither hand contains a verified melee weapon, preserve the current main-hand state and let normal attack execution handle the fallback.
5. Selection is runtime-only and never changes the saved recording or the captured reset snapshot.
6. The controller performs no world scan and does not generate, duplicate, or repair items.
7. The same selection rule is applied immediately before a melee attack, not continuously every tick.

Deliberately not implemented in Phase 6C:
- ranged weapons/projectiles/ammunition;
- shield timing/blocking;
- food/consumables;
- synthetic inventory/hotbar;
- custom loadouts;
- damage/knockback multipliers;
- weapon durability restoration;
- weapon switching based on target distance.

This phase intentionally keeps weapon selection narrow so ranged, shield and consumable behavior can be added as separate state-machine decisions without coupling them to inventory assumptions.

## Implementation Progress — Phase 6D

Phase 6D adds the first real ranged-combat execution path using the existing Minecraft BowItem/ProjectileWeaponItem mechanics.

Repository/API verification:
1. The repository has no existing Fight projectile abstraction or ranged-action implementation, so no duplicate local projectile system is introduced.
2. Minecraft 26.1 exposes BowItem.releaseUsing(ItemStack, Level, LivingEntity, int) and getDefaultProjectileRange(); BowItem remains a ProjectileWeaponItem.
3. LivingEntity exposes getProjectile(ItemStack), allowing the runtime to verify ammunition availability without inventing an inventory source.
4. The Fight actor is a ServerPlayer/FakePlayer, so the normal server-side item/projectile mechanics remain the authoritative execution path.

Implemented scope:
1. Bow is recognized as a ranged weapon only when it is actually equipped.
2. Main-hand Bow is preferred; an off-hand Bow can be moved to main hand for the runtime shot.
3. Ammunition is required through the actor's existing getProjectile() resolution. No arrows are generated.
4. The target must be outside configured melee attack range but inside the bow's own default projectile range.
5. The actor faces the live target before firing.
6. The BowItem releaseUsing path is invoked server-side with a bounded full-draw charge, allowing vanilla projectile creation, trajectory and ammo handling to remain authoritative.
7. Ranged execution uses the existing Fight attack cooldown instead of creating independent timers.
8. If no valid bow/ammunition exists, the fighter falls back to normal chase/melee behavior.
9. Runtime bow selection does not modify the saved recording or reset snapshot.

Deliberately not implemented in Phase 6D:
- Crossbow charging/loaded-projectile state machine;
- synthetic inventory/hotbar;
- generated ammunition;
- projectile prediction/aim assistance;
- wall/line-of-sight ray validation;
- ranged damage/knockback multipliers;
- shield blocking;
- food/consumable behavior.

Crossbow is deliberately deferred because its 26.1 runtime requires a distinct load/charged-projectile lifecycle rather than treating it as an instant Bow shot.

## Implementation Progress — Phase 6E

Phase 6E adds a stateful Crossbow combat lifecycle rather than treating a Crossbow like an instant Bow shot.

Pre-implementation checks:
1. Repository search found no existing Crossbow/Fight ranged lifecycle, so the new logic remains isolated in the Fight equipment/ranged layer.
2. Minecraft 26.1 API verification confirms CrossbowItem exposes isCharged, getChargeDuration, getUseDuration, onUseTick, releaseUsing, performShooting, use, and getDefaultProjectileRange. Crossbow also stores charged projectiles through the ChargedProjectiles item component. citeturn0search0turn0search5
3. The implementation therefore uses the normal Crossbow loading/release/fire lifecycle and does not synthesize charged-projectile data or bypass vanilla projectile consumption.

Implemented scope:
1. Equipped Crossbow is recognized as a ranged weapon only when it is actually present in the participant's represented equipment.
2. Main-hand Crossbow is preferred; an off-hand Crossbow may be moved to main hand at runtime.
3. Ammunition is resolved through the actor's normal projectile lookup; no arrows/fireworks are generated.
4. An uncharged Crossbow enters a bounded multi-tick charge state.
5. During charging, the actor remains oriented toward its current live target.
6. Once the vanilla charge duration is reached, the Crossbow is released through its normal releaseUsing path.
7. The resulting charged state is then fired through the normal Crossbow use path on a subsequent combat tick.
8. The Fight attack cooldown starts only after a successful fire.
9. If charging becomes invalid, the participant exits the ranged state and falls back to normal chase/melee behavior.
10. Runtime Crossbow state is never written to the saved Fight definition or recording snapshot.

Deliberately not implemented in Phase 6E:
- synthetic inventory/hotbar;
- generated ammunition;
- multi-shot behavior beyond what the equipped Crossbow's own charged state provides;
- projectile prediction or aim assistance;
- line-of-sight ray validation;
- ranged damage/knockback multipliers;
- shield blocking;
- food/consumables;
- runtime loadout persistence.

## Implementation Progress — Phase 6F

Phase 6F adds defensive Shield behavior using the existing Minecraft item-use/blocking lifecycle.

Pre-implementation checks:
1. Repository search found no existing Fight shield controller or item-use runtime layer, so the implementation is isolated to the existing Fight equipment/runtime controllers.
2. Minecraft 26.1 API verification confirms LivingEntity exposes isBlocking, getItemBlockingWith, startUsingItem, stopUsingItem and related active-item state; ShieldItem provides the normal use/getUseDuration lifecycle. citeturn0search0turn1search1
3. The Fight system already suppresses recorded ChangeItem actions while equipment ownership is active, so runtime shield use can remain isolated without modifying the source recording.

Implemented scope:
1. A Shield is considered available only from the participant's represented main/off-hand equipment.
2. Off-hand Shield is preferred for defensive use; main-hand Shield is also supported without creating or searching inventory items.
3. Defensive behavior activates only when a valid live target is close enough to represent melee pressure.
4. The participant faces the live target before entering defensive use.
5. Shield activation uses ShieldItem.use and the normal LivingEntity active-item/blocking state.
6. While the defensive condition remains valid, the participant stays in USE_ITEM and keeps the Shield active.
7. When the condition becomes invalid, the participant calls stopUsingItem and returns to normal combat decision flow.
8. Shield state is runtime-only and is not serialized into Fight definitions or recordings.
9. No synthetic shield, durability repair, inventory generation, or custom damage interception is introduced.

Deliberately not implemented in Phase 6F:
- custom shield damage absorption;
- custom shield timing/angles;
- shield-bash attacks;
- automatic shield swapping from hidden inventory;
- projectile-specific defensive prediction;
- food/consumables;
- ranged damage/knockback modifiers.

## Implementation Progress — Phase 6G

Phase 6G adds runtime Food/Consumable behavior using Minecraft 26.1's DataComponents consumable lifecycle.

Pre-implementation checks:
1. Repository search found no existing Fight food/consumable controller or item-use implementation.
2. Minecraft 26.1 moved consumption behavior into the DataComponents/Consumable system: edible items expose FOOD and CONSUMABLE components, and the consumable lifecycle eventually invokes Item.finishUsingItem. citeturn3search2turn1search3
3. The existing Fight inventory boundary only exposes represented equipment slots, not a complete inventory. Therefore Phase 6G consumes only an actually equipped food item from main/off hand; it does not scan, generate, or inject hidden inventory items.
4. Defensive Shield use already owns the immediate close-range defensive case, so Food use is only selected when the participant is sufficiently outside immediate melee pressure.

Implemented scope:
1. A valid food item must have both FOOD and CONSUMABLE components.
2. Main hand is preferred; an off-hand food item may be moved to main hand at runtime.
3. Food selection is health-driven with a bounded 50% maximum-health threshold.
4. Immediate melee pressure takes priority over eating; the existing Shield controller handles close-range defense first.
5. Food use enters USE_ITEM and delegates to the normal Item/Consumable use lifecycle.
6. The server lets the normal consumable system complete the item and apply its own vanilla effects/remainder behavior.
7. A bounded post-consumption cooldown prevents repeated immediate consumption loops.
8. If food is unavailable, invalid, or the entity cannot continue using it, combat falls back to the existing ranged/melee movement decisions.
9. Runtime food use never mutates saved Fight definitions or source recordings.

Deliberately not implemented in Phase 6G:
- hidden inventory/hotbar search;
- generated food;
- custom healing formulas;
- manual potion/effect application;
- custom hunger/saturation logic;
- eating while an active Shield/Crossbow lifecycle owns the item-use state;
- configurable food threshold persistence;
- custom consumable priority tables.

## Implementation Progress — Phase 6H

Phase 6H adds the already-persisted Fight damage and knockback multipliers to runtime combat.

Pre-implementation checks:
1. Repository audit confirmed the FightDefinition already persists validated damageMultiplier and knockbackMultiplier values, but the runtime attack path was still using vanilla values unchanged.
2. The concrete Swing implementation was inspected. Player and Mob attacks already funnel through vanilla attack mechanics, so the multiplier must wrap the existing attack call rather than replace it with synthetic damage.
3. Minecraft 26.1 attribute API verification confirms transient AttributeModifier support and the ADD_MULTIPLIED_TOTAL operation. A temporary attack-damage modifier can therefore affect the existing vanilla attack calculation without persisting on the participant. LivingEntity also exposes the server-authoritative hurtServer and knockback paths. citeturn2search0turn3search0turn0search0

Implemented scope:
1. Fight melee attacks apply the configured damageMultiplier only for the duration of that individual attack.
2. The multiplier uses a transient attack-damage attribute modifier, preserving vanilla Player/Mob attack calculation, armor, enchantments, critical behavior, shields, and other existing combat mechanics.
3. Direct non-attribute fallback damage is scaled explicitly.
4. Fight knockbackMultiplier scales only the knockback impulse produced by that individual Fight attack; existing velocity is preserved.
5. Multipliers are finite, runtime-only, and never written into entity attributes permanently.
6. Existing non-Fight Swing playback keeps its original behavior.
7. Values below zero are already rejected by FightDefinition validation; no additional hidden scaling is introduced.
8. Reset/stop requires no cleanup because the temporary modifier is removed in the same attack call's finally block.

Deliberately not implemented in Phase 6H:
- global damage event interception;
- modification of damage received by fighters;
- custom armor/shield formulas;
- custom critical-hit formulas;
- persistent entity attribute changes;
- death/drop behavior;
- synthetic inventory/loadouts.

## Implementation Progress — Phase 6I

Phase 6I hardens Fight death completion and reset-state ownership.

Pre-implementation checks:
1. The current runtime was audited against the Death/Reset contracts. Participant death already stopped attack/food/shield decisions, but the Fight itself could remain registered indefinitely after every participant became inactive.
2. FakePlayer was inspected. Its death override intentionally avoids vanilla drop/removal behavior and schedules playback shutdown, so Phase 6I does not introduce a global loot rule or alter real-player death behavior.
3. Playback stop/reset semantics were inspected. A Fight reset intentionally tears down its runtime playback and creates fresh runtime actors on the next explicit start; therefore reset snapshots must remain runtime-owned metadata and must never be written into recordings or shared entity state.

Implemented scope:
1. Participant captures initial health, position, yaw and pitch alongside its existing equipment snapshot.
2. Death transitions clear target, combat timers, navigation/recovery state and active combat-use states before marking the participant DEAD.
3. Dead participants remain excluded from target selection immediately.
4. A Fight with no active participants is treated as completed and removed from the active runtime registry; it cannot continue ticking combat logic.
5. Runtime ownership is released during completion/reset exactly as during explicit stop.
6. Saved Fight definitions remain STOPPED/unchanged after runtime completion; a new START creates fresh runtime participants from the source.
7. Reset/stop remain safe for repeated invocation and do not restore runtime snapshots into unrelated entities.
8. Existing FakePlayer death/drop behavior is preserved; no global player loot suppression is added.

Deliberately not implemented in Phase 6I:
- full player inventory container snapshots;
- custom death animations;
- real-player death interception;
- automatic Fight restart after completion;
- configurable death/drop policies;
- battlefield loot/pickup systems.

## Implementation Progress — Phase 7A

Phase 7A hardens runtime target acquisition for lower per-tick allocation and avoids unnecessary candidate scans.

Pre-implementation checks:
1. FightTargetSelector was inspected against the performance contract. It rebuilt the full candidate list on every active participant tick, even when the participant already had a valid target.
2. The FightManager tick loop was inspected. Target selection is centralized there, so target retention can be improved without changing movement, combat, or playback ownership.
3. The target relationship model remains participant/team based. The optimization therefore caches only a target that is still independently validated; no stale target is trusted.

Implemented scope:
1. CURRENT_TARGET and FIXED_TARGET validate and retain the current target before rebuilding candidates.
2. All target modes first perform cheap current-target validation where their semantics allow retention.
3. Candidate collection remains restricted to active Fight participants and configured real target players; no full-world scan is introduced.
4. Candidate evaluation continues to enforce alive state, same dimension, enemy-team relationship and detection range.
5. Invalid/dead/out-of-range/dimension-mismatched targets are immediately discarded and normal candidate selection resumes.
6. No persistent cache is stored in Fight definitions or recordings; target cache remains participant runtime state.
7. Target selection behavior and ordering remain deterministic for the existing modes.

Deliberately not implemented in Phase 7A:
- asynchronous pathfinding;
- global spatial indexes;
- full-world entity scans;
- configurable retarget intervals;
- background worker threads;
- combat behavior changes.

## Implementation Progress — Phase 7B

Phase 7B removes per-tick candidate-list allocation from Fight target acquisition while preserving the existing target-selection semantics.

Pre-implementation checks:
1. The Phase 7A selector still allocated a new ArrayList for every fresh target scan and then performed a second pass for NEAREST or LOWEST_HEALTH.
2. Existing FIXED_TARGET semantics depend on candidate insertion order: active Fight participants are considered before configured real players, with duplicate player entities excluded.
3. The target modes and validation rules were inspected before refactoring. The new implementation evaluates candidates in the same logical order and keeps the existing current-target retention behavior.

Implemented scope:
1. Target candidates are evaluated in a single pass instead of being copied into a temporary List.
2. NEAREST tracks the nearest valid candidate directly using squared distance.
3. LOWEST_HEALTH tracks the lowest-health valid LivingEntity directly.
4. FIXED_TARGET returns the first valid candidate in the same participant-then-configured-player order as before.
5. Duplicate configured players that are already Fight participants remain excluded.
6. CURRENT_TARGET retention remains unchanged and still occurs before candidate evaluation.
7. No full-world scan, persistent cache, asynchronous work, or combat behavior change is introduced.
8. No Fight definition or recording data is changed.

Deliberately not implemented in Phase 7B:
- spatial indexes;
- background target workers;
- retarget scheduling changes;
- navigation/pathfinding changes;
- combat-rule changes.

## Implementation Progress — Phase 7C

Phase 7C hardens and reduces bounded navigation work without changing the Fight's movement ownership model.

Pre-implementation checks:
1. The current navigation controller was inspected end-to-end with FightManager. A valid waypoint path was being reconsidered on a fixed interval even when the target had not moved materially and the path remained usable.
2. The existing manager/controller interaction was checked for stall accounting. While a navigation waypoint existed, a failed waypoint move could remain in navigation mode without incrementing the navigation-specific stall counter, delaying recovery until the navigation timeout/repath cycle.
3. The bounded A* contract was checked: loaded-chunk checks, collision/support validation, search-node/path-node caps, timeout, and target-distance threshold must remain bounded and unchanged.

Implemented scope:
1. Remove the unconditional fixed-interval A* repath from an otherwise valid waypoint path.
2. Repath only when the target moved beyond the existing target-repath threshold, the navigation path timed out, the current waypoint/path is exhausted, or bounded local progress stalls.
3. Count failed waypoint movement as navigation stall progress inside the navigation controller.
4. After bounded navigation stall, clear the stale path and allow a fresh bounded search instead of repeatedly retrying the same waypoint.
5. Preserve the existing direct movement/recovery controller and all existing A* search limits.
6. Preserve loaded-chunk-only behavior and no terrain modification.
7. Keep all navigation state runtime-only.

Deliberately not implemented in Phase 7C:
- global spatial indexes;
- asynchronous/background pathfinding;
- unbounded A*;
- world/chunk loading;
- terrain modification;
- movement-speed or combat-rule changes.

## Implementation Progress — Phase 7D

Phase 7D adds bounded retry backoff for failed local navigation searches so a blocked fighter does not repeatedly rebuild the same bounded path search while the target and local route conditions have not materially changed.

Pre-implementation checks:
1. The current Phase 7C flow was traced from FightManager into FightNavigationController. When direct chase remained blocked, the manager could request a new navigation search every eight ticks even when the previous bounded search had already failed.
2. Navigation state reset semantics were checked so the retry backoff cannot survive a target identity change, Fight reset, or a materially moved target.
3. The existing navigation safety limits were rechecked: search radius, maximum expanded nodes, maximum path nodes, loaded-chunk-only checks, collision checks, and no terrain modification remain unchanged.

Implemented scope:
1. Add a small runtime-only navigation retry cooldown to each Fight participant.
2. After a bounded path search returns no usable path, delay another identical search for a short fixed interval.
3. A materially moved target bypasses the retry delay and permits immediate bounded replanning.
4. A target identity change clears the retry delay.
5. Successful direct movement clears the retry delay so a previously blocked route can be reconsidered promptly.
6. Fight reset/deactivation clears all navigation retry state.
7. No changes to target selection, movement speed, path search bounds, combat rules, or persistence.

Deliberately not implemented in Phase 7D:
- global path caches;
- shared cross-fighter path caches;
- asynchronous pathfinding;
- terrain mutation;
- world/chunk loading;
- configurable retry timing.

## Implementation Progress — Phase 7E

Phase 7E fixes a navigation replanning trigger precedence issue discovered during the post-7D audit.

Pre-implementation checks:
1. The complete Phase 7D navigation decision order was traced, including target movement detection, navigation timeout, waypoint completion, retry cooldown, and bounded path search.
2. A state-transition edge case was reproduced logically: when a target had moved materially or a path had expired in the same tick that the current waypoint was reached, waypoint advancement could overwrite the earlier repath reason and incorrectly continue without immediate replanning.
3. The correction was kept local to the navigation decision boundary so target selection, direct movement, search bounds, retry backoff, and combat behavior remain unchanged.

Implemented scope:
1. Preserve target-moved and navigation-expired repath reasons when advancing a reached waypoint.
2. Replan immediately when any existing repath condition remains true after waypoint advancement.
3. Continue the current waypoint normally when no repath condition exists.
4. Preserve the Phase 7D failed-search retry cooldown.
5. Keep all navigation state runtime-only.

Deliberately not implemented in Phase 7E:
- new pathfinding algorithms;
- larger search bounds;
- terrain modification;
- async navigation;
- target-selection changes;
- combat behavior changes.

## Implementation Progress — Phase 7F

Phase 7F reduces bounded navigation search math overhead by using squared horizontal distance for nearest-progress tracking and attack-range termination checks, while preserving the existing A* priority heuristic and route ordering.

Pre-implementation checks:
1. The Phase 7E navigation search was audited at the search-node level. The per-node progress check and attack-range termination used Euclidean square roots even though both comparisons only require relative distance or a squared threshold.
2. The A* priority calculation was deliberately left unchanged because replacing its Euclidean heuristic with a different metric could alter search ordering and route behavior.
3. Search bounds, neighbor ordering, collision checks, loaded-chunk checks, and waypoint generation were checked and remain unchanged.

Implemented scope:
1. Track the best search-node distance using squared horizontal distance.
2. Compare the attack-range termination condition against a squared attack-range threshold.
3. Keep the A* priority heuristic Euclidean so path ordering remains unchanged.
4. Preserve all existing search limits and route generation.
5. Keep the optimization runtime-only with no persistence changes.

Deliberately not implemented in Phase 7F:
- new pathfinding algorithms;
- larger search limits;
- heuristic replacement;
- global/shared path caches;
- async pathfinding;
- terrain or chunk loading changes.
## Implementation Progress — Phase 7G

Phase 7G adds a bounded per-search walkability cache to avoid repeating the same loaded-chunk, collision, and support checks for the same local grid cell during one navigation search.

Pre-implementation checks:
1. The Phase 7F search was traced at the neighbor-expansion level. A grid cell can be evaluated repeatedly from different neighboring nodes, causing duplicate world collision/support and loaded-chunk checks.
2. A per-search cache was chosen instead of a persistent/global cache so block/entity changes cannot leave stale navigation data across ticks or fights.
3. The cache is bounded by the existing search node budget and is discarded after each search. Existing path ordering, neighbor ordering, collision rules, loaded-chunk rules, and search limits remain unchanged.

Implemented scope:
1. Cache the boolean walkability result for each GridPos during one bounded findPath call.
2. Reuse the cached result whenever the same cell is evaluated again in that search.
3. Preserve the exact existing isWalkable validation when a cell is first evaluated.
4. Keep the cache local to one search invocation; no cross-tick or cross-fighter persistence.
5. Preserve all existing A* scoring and route reconstruction behavior.

Deliberately not implemented in Phase 7G:
- persistent/world-wide path caches;
- shared caches between fighters;
- async pathfinding;
- larger search limits;
- terrain modification;
- chunk loading changes.
## Implementation Progress — Phase 7H

Phase 7H reduces temporary collection resizing during bounded navigation searches by pre-sizing the per-search work structures to the existing bounded search scale. This is allocation tuning only; search behavior and limits remain unchanged.

Pre-implementation checks:
1. The current findPath implementation was traced for temporary collections. Each search creates the walkability cache, A* cost map, predecessor map, and priority queue; their default capacities can resize while the bounded search expands.
2. The existing hard limits were verified first: MAX_SEARCH_NODES remains 96 and SEARCH_RADIUS remains 8. The new capacities are only initial capacities and do not increase any search limit.
3. Path reconstruction was checked separately so its existing MAX_PATH_NODES cap and ordering remain unchanged.

Implemented scope:
1. Pre-size the per-search walkability cache, best-cost map, predecessor map, and priority queue.
2. Keep all collections local to one findPath call.
3. Preserve identical candidate expansion, scoring, neighbor ordering, collision checks, and route reconstruction.
4. Do not introduce persistent caches or shared mutable navigation state.

Deliberately not implemented in Phase 7H:
- larger search limits;
- global/shared caches;
- async pathfinding;
- new heuristics;
- terrain modification;
- chunk loading changes.
## Implementation Progress — Phase 7I

Phase 7I reduces redundant square-root calculations in the bounded Fight movement layer by using squared horizontal distance for threshold checks, while preserving the existing normalized movement vector and actual displacement measurement.

Pre-implementation checks:
1. FightMovementController was audited separately from navigation. Its threshold checks only need squared distance, while movement normalization still requires the actual distance.
2. The actual post-move displacement measurement was intentionally left unchanged because it determines whether Minecraft's collision-aware movement produced sufficient real movement.
3. Stop-distance, minimum-movement, speed cap, recovery direction, and movement vector behavior were checked before changing the comparisons.

Implemented scope:
1. Use squared horizontal distance for minimum/stop threshold comparisons.
2. Calculate the square root only when a normalized movement vector or exact movement amount is required.
3. Preserve the existing bounded speed cap and recovery behavior.
4. Preserve actual post-move displacement measurement.
5. Keep movement ownership and collision handling unchanged.

Deliberately not implemented in Phase 7I:
- movement-speed changes;
- collision bypass;
- teleportation;
- pathfinding changes;
- recovery algorithm changes.
## Implementation Progress — Phase 7J

Phase 7J removes repeated detection-range arithmetic during one Fight target-selection pass.

Pre-implementation checks:
1. FightTargetSelector was audited after Phase 7B. The selector already evaluates candidates in one pass, but every candidate validation recalculated `detectionRange * detectionRange`.
2. The public validation behavior was checked before changing the internal helper. A private squared-range helper can preserve the existing public `isValid(...)` contract while avoiding repeated arithmetic inside a selection pass.
3. Candidate ordering, team filtering, alive/dimension checks, CURRENT_TARGET/FIXED_TARGET retention, and configured-player handling were checked and remain unchanged.

Implemented scope:
1. Compute the squared detection range once per `select(...)` call.
2. Pass that squared threshold through the internal candidate-selection and validation path.
3. Keep the existing public `isValid(...)` method behavior unchanged.
4. Preserve all target-selection ordering and semantics.
5. Keep the optimization runtime-only with no persistence or cached world state.

Deliberately not implemented in Phase 7J:
- persistent target caches;
- retarget interval changes;
- world-wide spatial indexes;
- target-selection behavior changes;
- asynchronous work.


## Implementation Progress — Phase 7K

Phase 7K removes repeated attack-range squaring from the per-participant Fight decision loop without changing combat behavior.

Pre-implementation checks:
1. The Phase 7J target-selector optimization was rechecked first; its squared detection-range value is scoped to target selection and does not cover the separate attack-range checks in FightManager.
2. The active Fight tick path was traced through defense, food, ranged combat, navigation/chase, and melee positioning. The configured attack range is immutable for the current Fight definition, while the same squared threshold was recomputed at multiple decision points for every active participant.
3. The ranged branch was checked separately because its range is equipment-dependent and must remain runtime-derived. Only the fixed Fight attack-range threshold is precomputed in this phase.

Implemented scope:
1. Compute `attackRangeSqr` once per active participant tick from the validated Fight attack range.
2. Reuse that squared threshold for ranged-vs-melee distance gating and normal chase/positioning decisions.
3. Keep the original `attackRange` value for APIs that require the actual distance, including Food and navigation/movement controllers.
4. Preserve the existing ranged-weapon range calculation and its runtime dependency on equipped equipment.
5. Preserve all target selection, movement, navigation, attack timing, damage, knockback, item-use, and persistence behavior.
6. No new cache, mutable shared state, or persistence field is introduced.
7. The optimization is runtime-only and cannot alter saved Fight definitions or recordings.

Deliberately not implemented in Phase 7K:
- ranged-range caching;
- target-selection changes;
- movement/pathfinding changes;
- attack-speed changes;
- combat-rule changes;
- persistent runtime caches.


## Implementation Progress — Phase 7L

Phase 7L removes the remaining repeated ranged-range squaring from the per-participant Fight decision loop without changing ranged-weapon selection or combat behavior.

Pre-implementation checks:
1. The Phase 7K attack-range optimization was revalidated first. Its attackRangeSqr is independent of the equipment-derived ranged threshold, so the next safe local optimization is the separate rangedRange * rangedRange comparison.
2. FightEquipmentController.getRangedRange(participant) was inspected. The returned ranged distance is derived from the participant's currently equipped Bow/Crossbow and projectile availability, so the range value itself must remain runtime-derived and must not become a shared or persistent cache.
3. The ranged decision branch was traced from range calculation through Bow/Crossbow execution. Only the repeated arithmetic is changed: the actual rangedRange value remains available for the existing rangedRange > attackRange comparison, while its squared threshold is computed once for the squared-distance comparison.

Implemented scope:
1. Compute rangedRangeSqr once after the existing runtime ranged-range lookup.
2. Reuse that value for the existing targetDistanceSqr <= rangedRange * rangedRange gate.
3. Preserve the runtime equipment lookup and projectile-availability semantics.
4. Preserve the existing rangedRange > attackRange comparison using the unsquared range values.
5. Preserve Bow/Crossbow preparation, firing, cooldowns, chase/navigation, target selection, damage, knockback, item-use, and persistence behavior.
6. No persistent cache, shared mutable state, or equipment-state mutation is introduced.
7. The optimization remains local to the active participant tick and cannot alter saved Fight definitions or recordings.

Deliberately not implemented in Phase 7L:
- ranged-range caching across ticks;
- equipment selection changes;
- target-selection changes;
- movement/pathfinding changes;
- attack-speed changes;
- combat-rule changes;
- persistent runtime caches.


## Implementation Progress — Phase 7M

Phase 7M hoists immutable-for-the-current-tick Fight configuration reads out of the per-participant decision loop. This phase was explicitly defined after the Phase 7L audit because the blueprint had no pre-defined next phase.

Pre-implementation checks:
1. The blueprint was audited through Phase 7L and contained no officially defined Phase 7M, so no new feature or behavior-changing phase was assumed. The next phase was defined as a narrow runtime lookup optimization only.
2. FightManager.tick() was traced end-to-end. Target mode, detection range, attack range, attack speed, movement speed, damage multiplier, and knockback multiplier are all FightDefinition values read during the participant loop.
3. Mutation semantics were checked. FightDefinition setters can update a definition between server ticks, so the optimization snapshots these values at the start of each tick rather than storing them in persistent/runtime participant state. This preserves configuration changes for the next tick while preventing repeated getter calls within the same tick.
4. No new Minecraft API is required; the change uses only existing FightDefinition getters and local Java variables.

Implemented scope:
1. Read the seven runtime combat/target configuration values once at the beginning of each FightRuntime tick.
2. Reuse the local values for every active participant in that tick.
3. Keep attackRangeSqr derived from the same per-tick attackRange snapshot.
4. Preserve the existing definition object passed to FightTargetSelector because selector semantics still depend on its configured target lists/team relationships.
5. Preserve the existing equipment-derived ranged range and all participant runtime state.
6. Preserve configuration mutation semantics: changes made before a tick are observed on that tick; no persistent cached configuration is introduced.
7. No serialized fields, API changes, combat-rule changes, target-selection ordering changes, movement changes, or navigation changes are introduced.

Deliberately not implemented in Phase 7M:
- persistent FightDefinition caches;
- cross-tick configuration snapshots;
- target candidate caching changes;
- equipment caching;
- movement/pathfinding changes;
- combat behavior changes;
- new Minecraft APIs.


## Implementation Progress — Phase 7N

Phase 7N removes duplicate squared-distance calculation inside FightTargetSelector candidate evaluation. The same candidate distance was first calculated for range validation and then recalculated for nearest-target ordering.

Pre-implementation checks:
1. Phase 7M was verified as a local per-tick configuration-read optimization. The next safe hotspot is inside target selection, where candidate validation and nearest ordering can evaluate the same distance twice.
2. FightTargetSelector was traced for all candidate sources: active Fight participants and explicitly configured target players. Both paths use the same validation semantics before nearest-distance ordering.
3. The existing public isValid(...) contract was checked. It remains boolean and continues to use the same alive, actor, identity, dimension, and squared-range conditions.
4. A negative sentinel is safe internally because squared distances are never negative. It is used only by the new private helper and is never exposed through the public API.

Implemented scope:
1. Compute a valid candidate's squared distance once during validation.
2. Reuse that exact value for nearest-target ordering instead of calling distanceToSqr(...) a second time.
3. Apply the same optimization to configured target-player candidates.
4. Preserve FIXED_TARGET and LOWEST_HEALTH behavior, candidate ordering, team filtering, duplicate participant filtering, alive checks, dimension checks, and detection-range semantics.
5. Preserve the public isValid(...) method signature and behavior.
6. No persistent cache, shared mutable state, new API, or cross-tick state is introduced.

Deliberately not implemented in Phase 7N:
- target caching across ticks;
- changes to target priority/order;
- spatial indexes;
- asynchronous selection;
- team-relation changes;
- detection-range behavior changes.


## Implementation Progress — Phase 7O

Phase 7O consolidates the public Fight target-validation path onto the already centralized squared-distance validation helper. Before this phase, isValid(...) performed the same alive/entity/dimension checks once and then called a helper that repeated those checks again. The helper was introduced in Phase 7N and already returns the exact internal validity result needed by isValid(...).

Pre-implementation checks:
1. The Phase 7N helper was audited line-by-line. Its invalid conditions exactly cover non-living/dead targets, dead actors, self-targeting, cross-dimension targets, and out-of-range targets—the same conditions enforced by isValid(...).
2. The public isValid(...) signature and boolean contract were checked. The refactor keeps the same signature and maps the helper's internal negative sentinel to the same true/false result; no sentinel escapes the private helper.
3. All Fight-package call sites were checked. FightTargetSelector.select(...) is called only by FightManager, while isValid(...) remains a public compatibility method. No caller depends on the duplicated internal checks or on intermediate state.
4. The change was restricted to FightTargetSelector.java plus this blueprint entry. No target ordering, team filtering, detection-range semantics, or combat behavior is altered.

Implemented scope:
1. Make isValid(...) call getValidDistanceSqr(...) directly and return whether the result is non-negative.
2. Remove the now-redundant validation checks from isValid(...).
3. Remove one unused local Entity actor variable from selectCandidate(...).
4. Preserve the exact public method signature, return type, and validation conditions.
5. Keep all Phase 7N candidate-distance reuse behavior unchanged.

Deliberately not implemented in Phase 7O:
- public API signature changes;
- target caching;
- target ordering changes;
- detection-range changes;
- team/relation changes;
- persistent state;
- asynchronous selection;
- movement or navigation changes.

## Implementation Progress — Phase 7P

Phase 7P removes an unnecessary square-root operation from FightMovementController.tryMove(...). The method only needs to determine whether the actual horizontal movement reached a non-negative minimum threshold, so the comparison can be performed using squared distances without changing the acceptance condition.

Pre-implementation checks:
1. The full tryMove(...) call path was traced. It is used by moveToward(...) for normal movement and bounded perpendicular recovery, so this optimization affects the hot movement path without changing ownership, collision handling, or recovery flow.
2. The mathematical condition was checked. The previous comparison was movedDistance >= requiredDistance, where both values are non-negative. Comparing movedDistanceSqr >= requiredDistanceSqr is therefore equivalent for the same X/Z displacement.
3. Input bounds were checked. requestedDistance is produced only after a positive finite distanceToMove is calculated from validated movement inputs, and SUCCESS_RATIO/MIN_SUCCESSFUL_MOVE are finite non-negative constants. The squared threshold is therefore safe and bounded.
4. Regression/API checks were performed. No public method signature, movement speed calculation, stop-distance logic, collision call, blocked-tick handling, recovery direction, or entity movement API changes.

Implemented scope:
1. Replace Math.sqrt(...) in tryMove(...) with a squared horizontal-distance calculation.
2. Square the same non-negative required movement threshold once.
3. Preserve the existing finite-value guard and exact success threshold.
4. Keep all normal movement, collision, recovery, navigation, and Fight behavior unchanged.

Deliberately not implemented in Phase 7P:
- movement speed formula changes;
- stop-distance changes;
- collision behavior changes;
- recovery threshold changes;
- navigation/pathfinding changes;
- target selection changes;
- persistent/shared caches;
- public API changes;

## Implementation Progress — Final Hardening Batch (Phases 8–9)

The remaining blueprint work was audited as a regression/final-hardening pass rather than as a new combat subsystem. The code already contains the runtime, persistence, ownership, target validation, movement/navigation, equipment, ranged, shield, food, group-control, loader event, and server-stop foundations described by the earlier phases.

Pre-implementation checks:
1. The Phase 7P GREEN commit was verified as the current baseline. The remaining blueprint sections were compared against the actual command registration, FightManager lifecycle, Fabric/NeoForge event wiring, playback ownership hooks, persistence loader, and runtime ownership registry.
2. A concrete integration gap was confirmed: FightCommand.java existed and was fully implemented, but MocapCommand.java did not attach FightCommand to the existing administrative /mocap command tree. The Fight feature therefore had no reachable command path through the project's registered command root.
3. A lifecycle-safety gap was confirmed: several FightDefinition mutation commands could modify saved configuration while a Fight runtime was already active, while team-assignment mutations already correctly rejected active runtimes. This could make the saved definition and live runtime diverge mid-fight.
4. No new Minecraft API, persistence field, combat rule, target algorithm, movement algorithm, or shared cache is required for these hardening fixes. The changes stay inside the existing command/lifecycle architecture.

Implemented scope:
1. Register FightCommand under the existing /mocap administrative command root using the same permission model already applied by MocapCommand.
2. Reject all saved Fight configuration mutations while that Fight is running, covering source scenes, target players/scenes, target mode, combat numeric settings, and target clearing.
3. Keep active runtime state independent until the Fight is stopped/reset.
4. Preserve existing command syntax and error handling; only the previously unreachable Fight command tree and unsafe active-runtime mutation cases are corrected.
5. Preserve all combat, target, movement, navigation, equipment, persistence-format, Fabric, and NeoForge behavior outside this integration hardening.

Manual/runtime validation still required before final Definition of Done:
- full cinematic scenario;
- real combat interaction across melee/ranged/shield/food;
- target movement/death/retargeting;
- repeated start/stop/reset with inventory observation;
- multiplayer and dimension/chunk edge cases;
- existing MoCap recording/playback regression checks.

Deliberately not implemented in this final hardening batch:
- new target modes;
- new combat behaviors;
- full inventory/hotbar persistence;
- new loot/drop rules for unrelated real players;
- advanced pathfinding;
- persistent runtime Fight recovery across server restart;
- new public API surface;
- asynchronous processing;
- artificial fighter-count limits.

## Implementation Progress — Final Audit (Post-Build #169)

Build #169 completed successfully on commit `7883b9491132ed5b4fa9109a584015c38c1c3d99`. A repository-level final audit was then performed against the Completion Checklist, Final Architectural Invariants, and Definition of Done.

Code-side audit result:
1. Fight command registration is present under `/mocap fight ...`.
2. Fight CRUD, persistence loading/saving, malformed-file rejection, start/stop/reset/remove lifecycle, active-runtime isolation, and server-stop cleanup are implemented.
3. Runtime participants are independently owned; target validation, live retargeting, movement ownership, bounded navigation/recovery, group formation teleport, and Fishing Rod destination control are implemented.
4. Melee, Bow, Crossbow, Shield, Food/Consumable, damage, knockback, death, completion, and reset handling are implemented within the existing playback architecture.
5. Active-Fight configuration mutation is rejected, preventing saved-definition/live-runtime divergence.
6. Fabric and NeoForge loader-specific Fishing Rod hooks are present; no additional interaction hook is required by the current 26.1 architecture.
7. No repository TODO/FIXME markers or unsupported-operation stubs were found in the audited codebase, and no additional blueprint-defined implementation phase remains after Phase 9.

Not promoted to a false GREEN status:
- live in-game cinematic validation;
- real-player combat observation;
- target movement/death/retargeting observation;
- repeated start/stop/reset inventory observation;
- multiplayer/dimension/chunk edge-case observation;
- original MoCap recording/scene/playback regression observation;
- measured runtime performance profiling.

These items require an actual running Minecraft server/world and cannot be truthfully marked complete from a repository build alone. No speculative subsystem, new persistence format, synthetic inventory layer, server-restart runtime recovery system, or new combat behavior was added merely to make the checklist appear complete.

Definition-of-Done status: **implementation/code audit complete; runtime validation remains the only unverified layer.**


## Implementation Progress — Final Audit (Post-Build #170)

Build #170 completed successfully on commit `c59aaa3c868787c0571256b21fdf66606897f85f` with Java 25 and all loader builds passing.

A second implementation review was performed after Build #170, including a fresh source audit of `FightManager`, `FightParticipant`, `FightEquipmentController`, `FightDefinition`, `FightCommand`, `FakePlayer`, and the playback lifecycle. The review specifically rechecked inventory isolation, equipment restoration, death/drop behavior, target-player handling, runtime ownership, persistence, server restart semantics, and command reachability.

External API compatibility was also rechecked against current NeoForge 26.1 documentation. The current interaction architecture already provides the required player interaction pipeline and the repository's NeoForge Fishing Rod hooks are therefore not missing an additional event. NeoForge 26.1 also officially targets Java 25 and recommends dedicated-server testing; no API migration is required by this audit.

Result:
1. No additional safe code change was identified that is required by the locked Fight blueprint.
2. Adding speculative inventory persistence, restart-time runtime reconstruction, new combat rules, new target modes, or a new test-only abstraction would expand scope beyond the approved architecture and could introduce regressions without solving a verified defect.
3. Runtime-only validation remains the final unverified layer: an actual dedicated Minecraft server/world must execute cinematic playback, combat, retargeting, inventory reset, multiplayer/dimension/chunk, and recording/playback regression scenarios.
4. This audit intentionally leaves those scenarios unmarked rather than manufacturing a code-side GREEN result.

**Implementation decision:** no speculative production-code upgrade is made in this pass because the repository already satisfies the implementation scope and Build #170 is GREEN. The next meaningful validation step is execution on a real dedicated server/world, not another source-code rewrite.

## Final Runtime Validation Batch — Ready for Dedicated-Server Execution

The implementation/code phase is complete. The remaining work is consolidated into one runtime validation batch so individual scenarios are not implemented or marked complete speculatively.

### Validation order

1. **Baseline / regression** — start the dedicated server with the current mod build; verify existing recording, recording save/load, scene creation, scene playback, nested scene playback, playback offsets/delays, player naming/skin handling, and existing commands before starting any Fight.
2. **Fight lifecycle** — create/load a Fight, configure source and target, start, stop, reset, remove, then repeat START → STOP → RESET → START and START → DEATH → RESET → START.
3. **Mocap vs Mocap** — verify independent participants, target acquisition, chase, melee, death, target invalidation, completion, and reset.
4. **Mocap vs real player** — verify live target movement, dynamic retargeting, damage, target death/disappearance, and that the real player is never movement-controlled by Fight.
5. **Equipment/combat matrix** — verify melee, Bow, Crossbow, Shield, Food/Consumable, item switching, cooldown/recovery, damage, knockback, and no cross-participant equipment leakage.
6. **Inventory/drop isolation** — verify battlefield pickups are not consumed by Fight actors, dead Fight actors do not become loot sources for other Fight actors, and reset/start does not duplicate or lose recorded combat equipment.
7. **Group control** — verify Fishing Rod arming/disarming, valid destination resolution, formation spacing, collision/terrain safety, invalid destination rejection, and no unrelated-player teleport.
8. **Movement/navigation recovery** — verify direct chase, small obstacles, bounded navigation, stall recovery, holes/traps, target movement, and timeout/failsafe behavior without destructive world modification.
9. **Multiplayer isolation** — run multiple independent Fights concurrently and verify participant ownership prevents cross-Fight control, target selection, equipment changes, or reset interference.
10. **Dimension/chunk edges** — verify configured actors and targets across dimensions/chunks, unloaded/invalid destinations, and stale/dead target references.
11. **Persistence/restart** — stop and restart the dedicated server; verify Fight definitions reload correctly, malformed files remain rejected, no RUNNING Fight is reconstructed accidentally, and existing recordings/scenes remain intact.
12. **Long-run stability** — run a representative Fight for an extended period and observe server log errors, runaway movement, stuck states, entity leaks, duplicate actors, and abnormal tick cost.
13. **Loader regression** — repeat the critical interaction scenarios on both Fabric and NeoForge builds where the feature is supported, with special attention to Fishing Rod interaction hooks.

### Pass criteria

A scenario is GREEN only after it has been observed on a running server/world with no unexpected log exception, stuck runtime state, participant ownership leak, inventory/equipment duplication, unrelated-player mutation, terrain damage, or existing MoCap regression.

### Evidence to capture

For each scenario record: server/mod build, loader, world/dimension, Fight definition, command sequence, expected result, observed result, relevant log excerpt, and final status. Failed scenarios must produce a concrete reproduction before production-code changes are made.

### Implementation boundary

No additional production subsystem is to be added solely to make these checks easier. If runtime validation exposes a concrete defect, the defect becomes the next focused implementation change; otherwise this blueprint remains the final implementation baseline. NeoForge 26.1 provides Game Tests for in-game behavior testing, but introducing a new Fight-specific GameTest abstraction is intentionally deferred until a concrete runtime scenario shows that it is necessary. Dedicated-server execution remains the authoritative validation layer for this feature.


## Final Persistence Hardening — Post Validation-Batch Review

A second implementation review identified one concrete persistence edge case worth fixing before runtime validation: Fight startup previously activated the runtime and returned success even if the definition state could not be persisted. The file replacement path also relied on delete-and-rename behavior that could temporarily remove the previous definition on platforms where replacement is not atomic.

The implementation now:
- rolls back the newly-created Fight runtime if the RUNNING state cannot be persisted;
- restores the in-memory definition to STOPPED during that rollback;
- writes the temporary definition first and then replaces the destination with `java.nio.file.Files.move(..., REPLACE_EXISTING, ATOMIC_MOVE)`;
- falls back to a non-atomic `REPLACE_EXISTING` move only when the filesystem does not support atomic moves;
- keeps the existing file untouched when the temporary write itself fails.

No combat, targeting, movement, navigation, equipment, or command behavior was changed by this hardening pass.


## Final Runtime-Lifecycle Hardening — Post Validation-Batch Review

A fresh lifecycle audit identified two concrete cleanup gaps that were safe to correct without expanding the Fight architecture:

- failed/isolated Fight runtimes now clear any Fishing Rod binding associated with that Fight;
- global Fight shutdown now clears all Fishing Rod bindings;
- explicit STOP/RESET now convert runtime cleanup exceptions into logged failures instead of leaving the definition in an ambiguous RUNNING state;
- STOP/RESET now report persistence failure instead of silently returning success when the STOPPED definition could not be saved.

No combat, targeting, movement, navigation, equipment, persistence-format, or command syntax was changed.


## Final Power-System Completion — Post Build #175 Audit

A final source audit identified one concrete implementation gap against the locked Power System contract: `FightDefinition.power` was validated and persisted but had no runtime effect.

The runtime now applies a bounded Power mapping:

- Power `1` → `0.75x` attack-speed and movement-speed factor;
- Power `10` → `1.25x` factor;
- values `2..9` interpolate linearly between those bounds;
- the factor is applied only to runtime attack speed and movement speed;
- configured damage remains controlled by `damageMultiplier`, so Power does not silently multiply damage;
- the attack-speed safety clamp remains in `calculateAttackCooldown`.

This completes the documented Power influence without introducing unbounded statistics or a new AI system. Runtime behavior still requires dedicated-server validation before the Power scenarios can be marked GREEN.


## Final Configuration-Persistence Hardening — Post Power Audit

A source audit identified a concrete consistency gap in Fight configuration updates: setters modified the in-memory FightDefinition before persistence, but a failed save could leave the in-memory definition changed even though the command reported failure and the previous file could still contain the old configuration.

Configuration mutations now snapshot the previous definition and restore it when persistence fails. This applies to scene/target/team assignments, target mode, Power, attack speed/range, detection range, movement speed, damage multiplier, knockback multiplier, and target clearing.

This keeps the in-memory configuration and persisted configuration aligned after a failed write, without changing successful command syntax or runtime combat behavior.


## Final World-Lifecycle and Auto-Stop Persistence Hardening

A post-GREEN lifecycle audit identified concrete reliability gaps that can be corrected without changing Fight combat semantics:

- `ensureLoaded()` no longer marks Fight definitions as loaded before the world-scoped Fight directory is initialized, preventing an early call from permanently suppressing definition loading for that server session;
- `stopAll()` now clears the static definition cache and resets the loaded flag after persisting STOPPED state, so a later server/world instance in the same JVM reloads definitions from its own Fight directory;
- automatic runtime isolation now logs a persistence error if the STOPPED definition cannot be written;
- shutdown persistence failures are now logged instead of being silently ignored;
- formatting artifacts in the previously hardened configuration setters were normalized without behavior changes.

This pass does not change targeting, Power scaling, damage, movement, navigation, equipment, command syntax, or the persistence format. Dedicated-server gameplay validation remains required for runtime-only scenarios.


## Final Target-Retention Hardening — Post World-Lifecycle Audit

A fresh performance audit identified a concrete remaining implementation gap against the locked target-acquisition and performance rules: NEAREST and LOWEST_HEALTH target selection was being evaluated every Fight tick even when the current target was still valid.

The runtime now retains a valid target between selection passes:

- CURRENT_TARGET and FIXED_TARGET retain a valid current target until invalidation;
- NEAREST and LOWEST_HEALTH re-evaluate on a bounded 5-tick selection interval while still immediately invalidating dead, stale, cross-dimension, out-of-range, or otherwise invalid targets;
- target-selection cooldown is runtime-only and is cleared when a participant is deactivated;
- no target reference is persisted;
- target selection ordering, team filtering, live-player handling, damage, movement, navigation, equipment, and command syntax are unchanged;
- the existing squared-distance validation path remains authoritative.

The bounded interval is deliberately internal rather than a new persisted configuration field so the saved Fight format and command/API surface remain unchanged. This satisfies the blueprint's target-retention/performance requirement without introducing a world-wide spatial index, asynchronous selection, or speculative AI layer.

Runtime target movement/death/retargeting remains part of the dedicated-server validation batch.

## Final Lifecycle Consistency Hardening — Post Target-Retention Audit

A final source audit identified one concrete failure-path leak in `stop`/ `reset`: the runtime entry was removed before the saved definition was resolved. If the definition map and active-runtime map ever became inconsistent, the runtime could be detached without cleanup.

The implementation now resolves the definition before detaching the runtime. If the definition is missing but an active runtime still exists, the runtime is explicitly cleaned up and its Fishing Rod binding is cleared before the command reports the missing definition.

This is defensive lifecycle hardening only. It does not change Fight configuration format, command syntax, targeting, movement, navigation, equipment, combat formulas, or persisted runtime state.

The remaining validation that cannot be safely replaced by source-level changes is live dedicated-server gameplay validation across the documented Fight matrix.


## Final Automated Definition Validation Layer — Post Lifecycle Audit

A lightweight JUnit 5 regression suite now covers the pure `FightDefinition` model: defaults, numeric bounds and finite-value rejection, team/reference alignment, target-mode copying, copy isolation, and state/team preservation.

The common build executes the JUnit platform as part of the normal Gradle `test` lifecycle. This remains deterministic configuration validation and does not replace dedicated-server gameplay validation.

## Final Fixed-Target Semantics Hardening — Post Automated Definition Audit

A focused runtime audit identified a semantic gap in the existing `FIXED_TARGET` mode: the selector retained a valid current target, but after that target became invalid it could acquire a different candidate. The mode is now explicitly lock-on semantics for each runtime participant:

- the first valid target acquired is locked for that participant;
- while the locked target remains valid, it is retained;
- if the locked target dies, disappears, changes dimension, or leaves the configured detection range, the participant has no replacement target;
- the lock is runtime-only and is cleared when the participant is deactivated/reset through the normal Fight runtime lifecycle;
- no persisted entity reference or new Fight file field is introduced.

This keeps `FIXED_TARGET` deterministic without adding a new persisted target-identity architecture. Other target modes remain unchanged. Dedicated-server observation of target death/disappearance remains part of the final runtime validation matrix.

## Final Playback Ownership Enforcement — Post Source Audit

A post-GREEN source audit found that the Phase 4A/6B ownership hooks were present but the action layer did not yet enforce them at the point where recorded Movement and ChangeItem actions mutate the actor.

This was a concrete integration defect, not a new feature request.

Implemented scope:
1. `ActionContext.changePosition(...)` now always advances the playback's internal recorded position, but does not snap the live entity while movement ownership is suppressed.
2. `Movement.execute(...)` now exits after updating the internal playback position when Fight movement ownership is active, so recorded movement cannot overwrite Fight-controlled locomotion, head rotation, ground state, or movement packets.
3. `ChangeItem.execute(...)` now leaves the live equipment untouched while Fight equipment ownership is active, while still reporting the recorded action as processed so playback timing continues normally.
4. No public API interface was changed; the suppression boundary remains internal to the existing playback implementation.
5. Normal playback behavior is unchanged when the suppression flags are false.
6. Scene playback continues to propagate the existing ownership flags to nested recordings.

This closes the concrete movement/equipment ownership gap described by Phases 4A and 6B without changing Fight combat rules, persistence, targeting, navigation, or command syntax.

Runtime validation remains required for the final cinematic matrix.

## Automated Server-Smoke Validation Layer — Post Runtime-Readiness Audit

A loader-level server smoke layer is now added without changing production Fight behavior.

### Fabric
- Fabric Loom GameTest source set is enabled for server-side tests only.
- A dedicated GameTest verifies Fight definition creation, persisted configuration mutation, target-mode mutation, reset behavior, and active-runtime isolation on a real Minecraft test server.

### NeoForge
- NeoForge JUnit integration is enabled with the official Test Framework and ephemeral test-server provider.
- A dedicated server-backed JUnit smoke test verifies the same Fight definition lifecycle against an actual Minecraft server instance.

These tests intentionally validate server initialization, world-scoped Fight persistence, and configuration lifecycle rather than pretending to cover full cinematic combat. The full runtime matrix still requires recordings/scenes and observation of actual actors, targets, equipment, navigation, and multiplayer behavior.


---

# 57. Automated Configuration Boundary Validation — Post Server-Smoke Audit

The loader-backed server smoke layer now also verifies the Fight configuration boundary before any real Fight runtime is started.

Covered cases:

- duplicate Fight IDs are rejected;
- valid target-player references are accepted;
- blank/null target-player references are rejected without throwing;
- blank/null target-mode values are rejected without throwing;
- starting without a configured source scene fails cleanly;
- stopping an inactive Fight fails cleanly;
- manager shutdown/reset clears the in-memory registry and a later lookup reloads the persisted definition;
- a reloaded definition remains STOPPED.

This is intentionally limited to configuration/lifecycle boundaries. It does not claim coverage for actual Mocap scene playback, runtime participant spawning, autonomous combat, navigation, equipment use, damage/death behavior, multiplayer isolation, or long-run performance.
