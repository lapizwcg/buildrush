# BuildRush

Hold right click and drag to build a whole line — or a whole wall — in one go.

A small, focused Fabric mod for Minecraft **26.2**. Place one block the normal way,
keep the button held, and drag your crosshair: a preview of ghost blocks follows your
aim. Let go, and they all get placed at once.

![BuildRush in action](preview.gif)

## How it works

1. **Right click** a face with any block in hand — the first block is placed by vanilla.
2. **Keep holding** and move your crosshair away — ghost blocks appear along the drag.
3. **Release** — every green ghost is placed.

- Drag along one axis to build a **line**. Keep drifting sideways and it becomes a
  **rectangle** — the second axis is picked up automatically.
- **Green** ghosts mean you have enough blocks. **Red** ghosts mean you have run out —
  they simply will not be placed.
- Vanilla's auto-repeat placement is suppressed while dragging, so holding the button
  never spams single blocks.
- Clicking a door, chest or any other interactive block behaves exactly as usual —
  no drag starts, because no block was placed.

## Counting your blocks

BuildRush counts what you actually have, including blocks stored inside **any item with
the vanilla `container` component** — shulker boxes, backpacks from other mods, and so
on. No hard dependency on any storage mod: if it uses the vanilla component, it works.

## Server friendly

The client only ever *asks*. Everything is validated server side before a single block
is placed:

- at most **128 blocks** per drag,
- target must be within **40 blocks** of the player,
- every position must be genuinely replaceable and unobstructed,
- blocks are consumed from the inventory (and containers) by the server itself,
- spectators are ignored.

This means BuildRush is safe to run on a multiplayer server: a modified client cannot
place blocks it does not own, reach further than allowed, or exceed the limit.

Works in single player too — the integrated server runs the same validation.

## Requirements

- Minecraft 26.2
- Fabric Loader 0.19.0+
- Fabric API

## Building

```
gradle build
```

The mod jar appears in `build/libs/`.

## License

All rights reserved.
