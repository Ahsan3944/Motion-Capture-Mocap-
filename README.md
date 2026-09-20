# Motion Capture (MoCap)

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

## New: Runtime Fight System

MoCap Playback now has a planned native **Fight** layer.

The Fight system turns scene recordings into runtime-controlled Mocap combat actors. It does **not** generate or record a fight in advance.

### Core idea

```
Recording
   ↓
Scene
   ↓
MoCap Playback
   ↓
Mocap Actors
   ↓
Runtime Fight
   ↓
Live Target / Live Battlefield
```

The recording supplies the character's recorded appearance, movement/animation baseline, armor and inventory. The Fight controller decides what the actor does **on the field** after Fight Start.

### Fight rules

- Fight is configured and saved before starting.
- Fight is started from MoCap Playback.
- A Fight can use one or more scenes and one or more targets.
- Supported concepts include Mocap vs Player, Mocap vs Mocap and Scene vs Scene.
- Target movement is followed live.
- Attack range, attack speed, power and other combat rules are configurable.
- Power uses a 1–10 scale.
- Combat is not prerecorded.
- No generated fight timeline is stored.
- Fighters continue pursuing/attacking until the target is dead, the fight is stopped/reset, or the fighter dies.
- Fighters use lightweight runtime calculations rather than a general-purpose AI planner.
- A hardcoded 50/100/500 fighter limit is not part of the design; practical capacity depends on server performance.

### Combat inventory

A Mocap fighter can only use its assigned/recorded inventory.

It cannot:

- Pick up battlefield drops
- Loot dead fighters
- Automatically gain new equipment

Runtime combat can switch between available items such as melee weapons, bows, shields and food when those items exist in the fighter's inventory.

### Movement and terrain

Combat movement is calculated live.

Fighters can pursue moving targets, maintain combat distance and attempt basic terrain/obstacle recovery. If a fighter falls into a hole or encounters terrain, the runtime movement system can attempt to recover and continue the fight according to the configured rules.

Mocap actors must not intentionally overlap each other's bodies. Group movement and combat use spacing logic.

### Group teleport

Playback will support moving a selected Mocap group to a runtime location.

A Fishing Rod destination control can be used to select a landing position. The group is placed around the destination with spacing instead of stacking every actor on one block.

The group can then spread into nearby valid positions while preserving its reset state.

### Fight command concept

The exact syntax follows the existing MoCap command architecture, but the intended command structure is:

```
/mocap playing ...
    fight
        list
        create <fight_name> ...
        info <fight_name>
        start <fight_name>
        stop <fight_name>
        reset <fight_name>
        remove <fight_name>
```

The final command names and arguments will follow the repository's existing command/suggestion conventions.

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

## Architecture direction

The existing MoCap recording and playback systems remain the foundation.

The Fight layer is intended to reuse:

- Recording system
- Scene system
- Playback system
- Actions
- Server tick events
- Entity events
- Command suggestions/utilities
- Settings/persistence
- Existing API/event infrastructure where appropriate

The Fight system will be implemented as a runtime extension of Playback rather than as a replacement for MoCap.

## Implementation tracking

Detailed architecture, rules, package boundaries, command design, persistence, performance requirements, implementation phases and Definition of Done are maintained in:

```
docs/MOCAP_FIGHT_SYSTEM_BLUEPRINT.md
```

That file is the implementation source of truth for the Fight feature.

## Compatibility

The repository currently declares:

- Minecraft: 26.1
- Java: 25
- Fabric
- NeoForge

Do not assume compatibility with another Minecraft version without updating and validating the build configuration.

## References

- CurseForge: https://www.curseforge.com/minecraft/mc-mods/motion-capture-mod-mocap
- Modrinth: https://modrinth.com/mod/motion-capture
