# Motion Capture (MoCap)

[![Build & Verify](https://github.com/Ahsan3944/Motion-Capture-Mocap-/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/Ahsan3944/Motion-Capture-Mocap-/actions/workflows/build.yml)

Every push and pull request is checked automatically by GitHub Actions. The workflow shows each build step separately and uploads the generated Fabric, NeoForge and common artifacts when the build succeeds.

**Motion Capture** is a Minecraft mod for recording and replaying player movement and actions, and for composing reusable recordings into scenes. It is intended for creating complex Minecraft scenes without requiring the original real players to perform every take.

## Current capabilities

MoCap recordings can contain:

- Movement
- Sprinting, jumping, swimming and sneaking
- Hand swings
- Item use
- Items in hands and armor
- States such as fire, invisibility and glowing
- Block placement, breaking and interaction
- Nearby entities and riding
- Reusable recordings
- Scenes containing multiple recordings or other scenes
- Per-instance player name, position offset and start delay
- Playback of recordings and scenes

## Runtime Fight System

MoCap Playback is being extended with a native **Fight** runtime layer.

The Fight system turns scene instances into runtime-controlled combat actors. It does **not** generate a fight in advance and does not store a prerecorded combat path.

### Core pipeline

```
Recording
   ↓
Scene
   ↓
MoCap Playback
   ↓
Runtime Mocap Actors
   ↓
Fight Controller
   ↓
Live Targets / Live Battlefield
```

The source recording remains the reusable source. Combat decisions, target tracking, movement, item selection, attacks, damage and death handling happen at runtime.

### Fight principles

- Fight is configured and saved before it starts.
- Fight starts from MoCap Playback.
- A Fight can use one or more scenes and targets.
- Mocap vs Player, Mocap vs Mocap and Scene vs Scene are supported design targets.
- Target movement is followed live.
- Power is configurable on a 1–10 scale.
- Combat is never converted into a prerecorded/generated fight timeline.
- Fighters continue pursuing valid objectives until the target is dead, the Fight is stopped/reset, or the fighter dies.
- Fighters use only their assigned/recorded combat inventory.
- Battlefield pickups and automatic looting are not allowed.
- No artificial 50/100/500 fighter limit is part of the design; practical capacity depends on measured server performance.
- Existing recording, scene and playback behavior remains the foundation.

### Combat inventory

A Mocap fighter has an isolated runtime combat inventory derived from its assigned source state.

Runtime decisions can select available:

- Melee weapons
- Ranged weapons
- Shields/off-hand items
- Food/consumables
- Other explicitly allowed recorded items

The fighter cannot automatically loot dead fighters or battlefield drops.

Reset must restore the initial inventory without duplicating items.

### Movement and terrain

Combat movement is calculated live.

Fighters can:

- Follow moving targets
- Maintain combat distance
- Reposition when targets move
- Avoid permanent overlap
- Attempt bounded recovery from basic obstacles or holes

The default design does not permit uncontrolled terrain destruction or unlimited block placement.

### Group teleport

Playback can move a selected Mocap group to a runtime destination.

The group is placed using safe spacing instead of stacking every actor on one block. A Fishing Rod destination control is part of the planned group-control workflow.

### Fight command concept

The exact command syntax follows the existing MoCap command architecture. The intended structure is:

```
/mocap playing fight list
/mocap playing fight create <name>
/mocap playing fight info <name>
/mocap playing fight start <name>
/mocap playing fight stop <name>
/mocap playing fight reset <name>
/mocap playing fight remove <name>
```

Additional configuration commands will be added only where they fit the existing command tree and validation system.

## Existing commands

```
/mocap recording [...]
/mocap recordings [...]
/mocap scenes [...]
/mocap playing [...]
/mocap settings [...]
/mocap info
/mocap help
```

### Basic workflow

1. Start recording:

```
/mocap recording start
```

2. Stop:

```
/mocap recording stop
```

3. Save:

```
/mocap recording save <name>
```

4. Play a recording:

```
/mocap playing start <name>
```

5. Create a scene:

```
/mocap scenes add <scene_name>
```

6. Add a recording to a scene:

```
/mocap scenes addTo <scene_name> <recording_name> [delay] [x_offset] [y_offset] [z_offset] [player_name] [skin_source_type] [skin_source]
```

7. Play a scene:

```
/mocap playing start .<scene_name>
```

A dot before the name identifies a scene in the existing command design. Scenes can also contain other scenes.

## Architecture

The Fight layer is an additive runtime extension of MoCap Playback.

It is designed to reuse:

- Recording system
- Scene system
- Playback system
- Action system
- Server tick events
- Entity events
- Command suggestions/utilities
- Settings and persistence
- Existing API/event infrastructure where appropriate

The Fight layer must not replace the existing recording or playback architecture merely to add combat.

## Implementation source of truth

The complete Fight architecture, lifecycle, runtime rules, persistence model, inventory isolation, target system, movement rules, group teleport, performance requirements, failure handling, testing matrix and implementation checklist are maintained in:

```
docs/MOCAP_FIGHT_SYSTEM_BLUEPRINT.md
```

That document is the master implementation contract for the feature.

## Compatibility

The repository currently declares:

- Minecraft: 26.1
- Java: 25
- Fabric
- NeoForge

Do not assume compatibility with another Minecraft version without explicitly updating and validating the build configuration.


## Runtime Fight System — Implementation Status

The runtime Fight System is being implemented incrementally on top of the existing playback architecture.

### Phase 1 completed
- Added versioned Fight definitions.
- Added persistent Fight storage under `mocap_files/fights/`.
- Added safe load/validation with malformed-file isolation.
- Added Fight lifecycle commands under `/mocap playback fight`.
- Added source-scene and target-player configuration primitives.
- Added Fight runtime ticking and server-stop cleanup hooks.
- Added duplicate-start protection and runtime isolation scaffolding.

### Phase 2 completed
- Reused the existing PlaybackRoot, RecordingPlayback and ScenePlayback entity lifecycle.
- Bound Fight runtime ownership to playback-created actors.
- Added independent playback configuration per Fight source.
- Added damageable Fight playback state.
- Added rollback of partially initialized Fight playback roots.
- Added cleanup of owned playback roots on stop/reset/server shutdown.

### Phase 3A completed
- Added independent runtime Fight participants for controlled living actors.
- Added source/target side isolation.
- Added entity ownership protection across simultaneous Fights.
- Added live target acquisition with nearest/current/lowest-health/fixed selection modes.
- Added target-scene playback roots for Scene-vs-Scene / Mocap-vs-Mocap setups.
- Added persisted target/combat configuration setters for power, attack speed/range, detection range, movement speed, damage and knockback multipliers.
- Revalidates live targets every runtime tick without scanning the full world.

### Phase 3A intentionally does not yet provide
- Autonomous movement/navigation.
- Runtime attack execution and hit validation.
- Weapon/item switching.
- Full combat state machine.
- Health/inventory/armor reset snapshots.
- Death presentation/drop policy.
- Multi-team relationship configuration beyond source/target sides.
- Group formation teleport and Fishing Rod destination control.

These systems will continue to reuse the existing Recording, Scene and Playback systems rather than replacing them.
