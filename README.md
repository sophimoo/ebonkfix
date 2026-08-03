# Ebonkfix

A Meteor Client addon that improves the **bounce mode** in meteor.

## Features

- **Bounce Mode** — improves the stock `Bounce` elytra with custom changes for the oldest anarchy server in minecraft.
  - `Vanilla` 24 bps at low ping, 40 bps at 50+ ping
  - `Packet` 40 bps at any ping, but will flag Grim ElytraA/B/C without viaversion to 1.20.5/6 and lower.
- `Highway Obstacle Passer` Requires baritone

### Requirements

- Minecraft 1.21.4 & Meteor 1.21.4 42
- Baritone if you want to use obstacle passer

### Building

Run the Gradle `build` task and copy the generated jar from `build/libs/` into your Meteor Client mods folder.

## Credits

Much of the code (Highway Obstacle Passer, portal trap detection, the `Entity`/`LivingEntity`/`KeyBinding` mixins, and helpers) is adapted from [meteor-stashhunting-addon](https://github.com/miles352/meteor-stashhunting-addon) — many thanks to [miles352](https://github.com/miles352) for their addon.
