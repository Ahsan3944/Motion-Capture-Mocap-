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

Planned/implemented scope for this batch:
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
