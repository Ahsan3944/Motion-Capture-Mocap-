# MoCap Fight System — Architecture & Implementation Blueprint

Status: DESIGN LOCKED / IMPLEMENTATION PENDING

Repository: Ahsan3944/Motion-Capture-Mocap-
Base system: Existing Motion Capture recording, recordings, scenes, playback, actions, events and commands.
Current project target: Minecraft 26.1 / Java 25 / Fabric + NeoForge.

## 1. Goal

Add a native Fight system inside MoCap Playback.

The Fight system is a runtime/on-field combat controller. It does NOT pre-generate, simulate, record, or pre-calculate a complete fight.

A recorded MoCap remains a reusable recorded character. During playback it can become a combat-capable NPC-like Mocap actor.

Pipeline:

Recording -> Scene -> Playback -> Mocap Actors -> Runtime Fight Controller

## 2. Core Rules

- Existing recording/playback behavior must remain intact.
- Fight is controlled from MoCap Playback.
- Fight must be configured and saved before it is started.
- A saved Fight has a name/ID and references its source scene and target(s).
- Fight starts only when explicitly selected and started.
- No prerecorded combat path.
- No AI-generated fight recording.
- No offline fight simulation.
- All target selection, movement, range checks, attacks, inventory decisions and combat reactions happen on-field at runtime.
- A fighter continues trying to complete its objective until its target is dead, the fight is stopped/reset, or the fighter itself dies.
- A hardcoded fighter-count limit must not be introduced. Practical capacity is governed by server performance.

## 3. Fight Participants

Supported relationships:

1. Mocap Scene -> Real Player
2. Mocap Scene -> Multiple Real Players
3. Mocap Scene -> Mocap Scene
4. Mocap Actors -> Mocap Actors
5. Multiple fighters engaging multiple nearby opponents

A target may be one player, multiple players, or another selected scene.

For free combat, a fighter can select a valid nearby opponent using runtime distance/availability rules. It is not pre-assigned to a prerecorded attack path.

## 4. Fight Configuration

A Fight definition should contain, at minimum:

- Fight ID/name
- Source scene(s)
- Target player(s) and/or target scene(s)
- Power level: 1–10
- Attack speed
- Attack range
- Detection/engagement range
- Movement/combat speed
- Optional damage/knockback modifiers
- Targeting mode
- Runtime combat rules
- Reset/start state reference

Do not hardcode values that should be configurable.

## 5. Mocap Actor Model

A scene instance becomes a runtime Mocap Actor.

It must preserve the recorded character's:

- Position/orientation baseline
- Skin/player appearance
- Armor
- Main-hand item
- Off-hand item
- Inventory contents
- Recorded playback/animation state

The actor may be duplicated many times from one recording.

Each duplicate is an independent runtime entity/state instance.

## 6. Inventory Rules

The actor may only use items that exist in its recorded/assigned combat inventory.

It must NOT:

- Pick up dropped items from the battlefield
- Loot another dead fighter
- Automatically add newly found equipment
- Gain external items during combat unless an explicit future feature allows it

Possible runtime choices from its own inventory include:

- Sword/axe/melee weapon
- Bow/ranged weapon
- Shield/off-hand
- Food
- Golden Apple or other edible item
- Other usable recorded items

Weapon/item switching is runtime-driven by simple combat calculations and available inventory, not prerecorded.

Examples:

- Close target -> melee weapon
- Target far away -> ranged weapon if available
- Low health -> usable food if available
- Defensive situation -> shield/off-hand if available

Exact decision rules should remain deterministic, configurable and lightweight rather than introducing a general-purpose AI planner.

## 7. Runtime Combat State

Use a lightweight state machine, not a fight generator.

Suggested states:

IDLE
SEARCH_TARGET
CHASE
POSITION
ATTACK
RECOVER
USE_ITEM
DEAD

State transitions are evaluated during live ticks.

The actor continuously reacts to the current battlefield state.

## 8. Targeting

Targeting is calculated at runtime.

Rules can include:

- Valid enemy/team
- Target availability
- Distance
- Current target
- Target alive/dead
- Engagement range
- Nearest valid opponent

When multiple valid opponents exist, the configured targeting mode may select the nearest/eligible target.

Target movement must be followed dynamically.

No target movement is baked into the recording.

## 9. Movement and Navigation

Combat movement must be live.

The fighter:

- Moves toward the current target
- Recalculates target position while moving
- Stops/positions at a valid attack distance
- Repositions when the target moves
- Avoids entering another actor's body/hitbox where possible
- Maintains configurable spacing between fighters

Terrain handling is part of the runtime rules.

If a fighter falls into a hole or encounters an obstacle, it should attempt to recover and continue toward the target.

Where supported by the implementation:

- Basic path/step navigation
- Gravity-aware movement
- Obstacle handling
- Block placement/recovery rules

These must be implemented safely and must not corrupt the world or cause uncontrolled block placement.

## 10. Spacing / Anti-Overlap

Group teleport and combat both require spacing.

When many actors are teleported to a target location, do not place every actor on exactly one block.

Instead:

- Determine a nearby placement area around the destination
- Generate valid positions with configurable spacing
- Avoid placing actors inside each other
- Spread the group naturally after teleport
- Preserve each actor's reset position/state

The same separation concept should be reusable during combat.

## 11. Group Teleport / Fishing Rod Control

A playback/group control should allow the whole selected Mocap group to be moved to a location.

A Fishing Rod interaction can be used as a runtime location marker/control:

- Detect the rod landing/target position
- Select the configured Mocap group
- Teleport the group into a small area around that position
- Use automatic spacing instead of a single-block stack
- Keep actors separated by roughly 1–2 blocks where terrain permits
- Preserve the reset/start state

This is a playback control, not a new recording.

## 12. Death

When a Mocap fighter dies:

- Remove it from active combat participation
- Stop target chasing/attacking
- Do not let other Mocap fighters loot its dropped equipment
- Preferably prevent its combat inventory/armor from becoming normal loot if the intended scene system requires clean cinematic behavior
- Preserve enough state to reset the scene

On Fight/Scene reset:

- Recreate/reactivate the actor
- Restore its saved inventory/armor
- Restore its configured position
- Restore its baseline playback state
- Clear dead/combat state

## 13. Real Player vs Mocap

Real players remain real players.

Mocap actors are controlled by the Fight runtime layer.

For Mocap vs Real Player:

- Mocap follows the real player's current position
- Real player's movement is never prerecorded by the Fight system
- Combat calculations use live player state

For Mocap vs Mocap:

- Both sides are runtime-controlled
- Their fight is not predetermined
- Each actor reacts to the current positions/states of the others

## 14. Playback Integration

Fight must be a native Playback feature.

Conceptual command tree:

/mocap playing ...

    fight
        list
        create <fight_name> ...
        info <fight_name>
        start <fight_name>
        stop <fight_name>
        reset <fight_name>
        remove <fight_name>

Exact command syntax must follow the existing command architecture and suggestion system.

The final implementation must not create a parallel command system outside MoCap.

## 15. Existing Systems to Reuse

Before adding new infrastructure, reuse existing:

- RecordingManager
- RecordingContext / RecordingId / RecordingSource
- Scene system
- Playback system
- Action system
- Server tick events
- Entity events
- Command suggestions/utilities
- Settings system
- Existing file/persistence layer
- API/event infrastructure where appropriate

Do not replace existing recording or playback formats unless technically unavoidable.

## 16. Proposed Package Boundary

Primary common-layer concept:

net.mt1006.mocap.mocap.fight

Suggested responsibilities:

- FightManager
- FightDefinition / FightId
- FightRuntime
- FightParticipant
- FightTarget
- FightState
- CombatRules
- CombatController
- CombatNavigator
- CombatSpacing
- CombatInventory
- CombatAction

Names are provisional. Existing project conventions take priority during implementation.

## 17. Command Layer

Extend the existing command architecture instead of creating a separate command handler.

The command layer should provide:

- Fight creation
- Fight editing
- Fight listing
- Fight information
- Fight start
- Fight stop
- Fight reset
- Target assignment
- Scene assignment
- Combat parameter configuration

Command suggestions should use existing CommandSuggestions infrastructure.

## 18. Persistence

Fight definitions must be persistent.

A saved Fight should survive server/mod restart and contain only configuration/state references necessary to recreate the runtime fight.

Do not store a generated combat timeline.

Do not store a prerecorded fight result.

Store configuration, participant references, target references and reset/start information.

## 19. Runtime Tick Design

Use server tick events already present in the project.

Do not perform expensive full-world searches every tick.

Runtime work should be staged:

- Frequent: movement/position correction and active combat timing
- Periodic: target validation/reselection
- Event-driven where possible: damage/death/item-use/entity events

The exact intervals must be benchmarked rather than guessed.

## 20. Performance

No artificial fighter-count cap should be imposed by design.

However, 500+ active actors can be expensive.

Implementation must therefore:

- Avoid unnecessary scans
- Avoid repeated object allocation
- Cache active participants
- Use squared-distance checks where appropriate
- Reuse target information until invalid
- Avoid full-world entity searches when a local participant list is available
- Separate active and inactive/dead participants
- Keep combat calculations lightweight

Performance testing is required before claiming a practical maximum.

## 21. Multiplayer / Synchronization

Combat authority should remain server-side.

The server owns:

- Fighter state
- Target state
- Movement decisions
- Attack timing
- Damage
- Death
- Inventory restrictions
- Reset state

Clients receive the resulting entity/player state normally.

No client-side fight simulation should be required for correctness.

## 22. Safety Rules

Fight implementation must not:

- Break normal recording/playback
- Modify saved recordings destructively
- Modify existing scene definitions destructively
- Give Mocap actors unauthorized inventory
- Allow automatic battlefield looting
- Leave dead actors permanently broken after reset
- Place blocks uncontrollably
- Create an infinite combat loop after the target is dead
- Keep attacking invalid/dead targets
- Cause actors to permanently overlap
- Introduce a hardcoded 50-player/100-player/500-player cap

## 23. Implementation Order

Phase 0 — Documentation
- Lock this blueprint
- Update README
- Keep this document as the implementation source of truth

Phase 1 — Fight Data Model
- Fight definition
- Persistence
- Participant/target references
- Combat settings

Phase 2 — Playback Integration
- Add Fight to Playback
- Fight selection
- Start/stop/reset lifecycle

Phase 3 — Mocap Combat Actor
- Runtime actor state
- Inventory/armor restrictions
- Combat state machine

Phase 4 — Targeting + Movement
- Runtime target acquisition
- Target following
- Combat spacing
- Obstacle/terrain recovery

Phase 5 — Combat
- Attack
- Damage
- Range
- Cooldown
- Weapon switching
- Food/use-item behavior
- Death handling

Phase 6 — Group Controls
- Group teleport
- Fishing Rod destination control
- Formation/spacing

Phase 7 — Scale/Optimization
- Large scene testing
- 50/100/250/500+ stress tests where practical
- Tick/load profiling

Phase 8 — Validation
- Existing MoCap regression tests
- Fight vs Player
- Fight vs Mocap
- Scene vs Scene
- Death/reset
- Inventory isolation
- Terrain recovery
- Restart persistence
- Command/suggestion validation

## 24. Definition of Done

The Fight feature is considered complete only when:

- A Fight can be created and saved.
- A scene can be assigned to it.
- One or more targets can be assigned.
- Playback can select and start the Fight.
- Fighters move toward live targets.
- Fighters fight using only their assigned inventory.
- Weapon/item switching works at runtime.
- Fighters do not loot battlefield items.
- Fighters handle basic terrain obstacles/recovery.
- Fighters maintain reasonable spacing.
- Death removes a fighter from active combat.
- Reset restores the complete initial state.
- Fight vs real player works.
- Fight vs Mocap scene works.
- Multiple simultaneous fighters work.
- No prerecorded fight timeline is generated.
- Existing MoCap recording/playback behavior remains functional.
- Build and tests pass on the repository's configured target.

## 25. Non-Goals

Do NOT implement as part of this feature:

- General-purpose autonomous AI
- AI-generated fight choreography
- Precomputed fight recordings
- Offline combat simulation
- A separate replacement for MoCap Playback
- A separate unrelated command framework
- A forced maximum fighter count

## 26. Source-of-Truth Rule

When implementation decisions conflict with this document:

1. Preserve existing MoCap behavior.
2. Preserve the runtime/on-field nature of Fight.
3. Never convert Fight into a prerecorded/generated combat sequence.
4. Reuse existing MoCap architecture before adding duplicate infrastructure.
5. Update this blueprint whenever an approved architectural change is made.

This file is the working source of truth for the Fight implementation.
