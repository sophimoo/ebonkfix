# Ebonkfix

A Meteor Client addon that improves the **bounce mode** in meteor.

## Features

- **Bounce Mode** — improves the stock `Bounce` elytra with custom changes for the oldest anarchy server in minecraft.
  - `Vanilla` (default) — emulates vanilla jump taps. No Grim flags, ~24 bps at low ping, 40 bps at 50+ ping
  - `Packet` — restarts gliding every airborne tick via `START_FALL_FLYING`. ~40 bps at any ping, but will flag Grim ElytraA/B/C without viaversion to 1.20.5/6 and lower.
- **Highway Obstacle Passer** — optional Baritone-driven pathing that pauses bounce input so Baritone keeps full control around obstacles. Custom start position, "away from start" direction, and distance settings are available.

### Requirements

- Minecraft / Meteor Client matching the versions declared in `gradle.properties` and `fabric.mod.json`.
- Baritone (optional, soft dependency — only used when Obstacle Passer is enabled).

### Building

Run the Gradle `build` task and copy the generated jar from `build/libs/` into your Meteor Client mods folder.

## Credits

Much of the code (Highway Obstacle Passer, portal trap detection, the `Entity`/`LivingEntity`/`KeyBinding` mixins, and helpers) is adapted from [meteor-stashhunting-addon](https://github.com/miles352/meteor-stashhunting-addon) — many thanks to [miles352](https://github.com/miles352) for their addon.

## License

Original template portions: CC0 1.0 Universal
Additional code and modifications: GNU GPL v3.0 or later
