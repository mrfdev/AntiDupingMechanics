# AntiDupingMechanics (Paper)

Note: this is made for 1MoreBlock.com - with GPT's help, and I don't recommend running this live until version 2.x

A small Paper plugin that blocks a few storage mechanics commonly abused in dupes, while staying player-friendly:

- **Chest boats**: players can still ride them, but cannot open their chest inventory.
- **Chested animals (donkey/mule/llama etc.)**: players can still mount them, but cannot open the chest inventory.
- **Prevent attaching chests** to chested-animals (so new chested animals cannot be created).
- **Bundles**: players can keep bundles and empty them, but **cannot add items into bundles**.

Includes:
- **Per-world config overrides**
- **Bypass permission**: `antiduping.bypass`
- **Reload**: `/antiduping reload`
- **Status**: `/antiduping status`
- **Debug logging** via config

## Commands

- `/antiduping reload` (permission: `antiduping.admin`)
- `/antiduping status` (permission: `antiduping.admin`)

## Permissions

- `antiduping.admin` — use `/antiduping reload|status`
- `antiduping.bypass` — bypass all blocks (recommended for staff)

## Per-world configuration

Config lives in `plugins/AntiDupingMechanics/config.yml`.

- `worlds.__default__` is the baseline for all worlds.
- Add `worlds.<worldname>` to override any keys.

Example: allow everything in an `admin` world:

```yaml
worlds:
  __default__:
    chest_boats: { enabled: true, block_open_inventory: true }
    donkeys: { enabled: true, block_open_inventory: true, block_attach_chest: true }
    bundles: { enabled: true, block_insert_items: true }
  admin:
    chest_boats: { enabled: false }
    donkeys: { enabled: false }
    bundles: { enabled: false }
```

## Build

Requires Java 21+.

```bash
./gradlew build
```

Jar will be in `build/libs/AntiDupingMechanics-1.1.0.jar`.

## Notes

Bundle behavior in Minecraft can vary across versions/clients. This plugin blocks the common insert methods:
- click/drag items onto a bundle in inventories
- certain right-click interactions involving item entities (where applicable)

If you find a specific interaction path that still inserts into bundles on your server, note the exact steps and we can tighten the checks.
