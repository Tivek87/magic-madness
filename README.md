# Magic Madness

<!-- #region 1. OVERVIEW & ACTIVE SPELLS -->
**Magic Madness** (`magicmadness`) is a 100% procedural, zero-texture Spell Combat & 3D VFX mod for **Minecraft 1.21.1 (NeoForge 21.1+)**.

Every visual effect, UI element, pixel-art school icon, radial spell wheel, and 3D spell construct is rendered entirely via mathematical geometry and vertex buffers—zero `.png` textures or external assets.

### Active Spells & Casting System (`v0.0.1-alpha`)
* **Hold `[G]` — Spell Radial Menu (`Magic Madness` Keybind Category)**:
  * Hold **`G`** in-game to open the procedural **Spell Radial Menu** (sized cleanly for GUI Scale 3).
  * **Center Circle Ring & Orbiting Aim Ball**: Move your mouse to guide the glowing aim ball around the outside of the center circle ring toward a spell sector while your camera stays rock-steady, then **release `G`** to cast!
  * **6 Magic Schools**: **Heat** (`0xFF7A3D`), **Air** (`0xA8F5E0`), **Nature** (`0x5CE65C`), **Electric** (`0x62C6FF`), **Void** (`0xC07CFF`), and **Hydro** (`0x3898FF`).
* **1. `Fireball` (`Heat` School — `10.0` Damage, `10m` Arc, `5.0s` Cooldown)**:
  * Launches a weighted 3D voxel fireball along a 10-block parabolic arc (`6.0` direct + `4.0` splash = `10.0` impact damage, plus `4s` afterburn), with spiraling flame ribbons, a 3D voxel explosion core, and steam douse in water.
* **2. `Lightning` (`Electric` School — `16.0` Damage, `24m` Range, `7.0s` Cooldown)**:
  * Locks onto targets with an electric ground rune and forms a **9-lobe 3D voxel thundercloud** overhead before striking with a 14-segment branched 3D lightning bolt (`10.0` strike + `6.0` shockwave = `16.0` damage) and chaining to up to `3` nearby enemies (`5` in rain/storms).
* **3. `Wind Gust` (`Air` School — `3.0` Damage, Massive Knockback, `10m` Cone, `3.5s` Cooldown)**:
  * Unleashes a 10-block storm vortex cone with 4 expanding 3D wind rings, 3 curved 3D wind-crescent blades, and 6 spiraling slipstreams. Deals light slash damage (`3.0`) with massive knockback (`2.25` push + `0.78` upward lift), reflects incoming projectiles, extinguishes fire, and cushions mid-air falls.
<!-- #endregion -->

---

<!-- #region 2. IN-GAME CLIENT & SERVER SPELL CONFIG -->
## In-Game Client & Server Spell Config

Open **Mods → Magic Madness → Config** in-game or from the main menu:
* **Client Spell Settings (`config/magic_madness/client/client.toml`)**: Tune camera shake, screen flash, particle multiplier, 3D geometry detail (`Low / Medium / Full`), ash disintegration, radial menu/HUD opacity, cooldown numbers, kill-confirm crosshair flick, and spell volume.
* **Server Spell Settings (`config/magic_madness/server/` (`general.toml`, `fireball.toml`, `lightning.toml`, `wind_gust.toml`))**: Accessible to the singleplayer/LAN host or listed server owners (`owners` in `general.toml`). Live-tune global spell multipliers and every spell's damage, knockback, cooldown, and range.
<!-- #endregion -->

---

<!-- #region 3. DOCUMENTATION & QUICK START -->
## Documentation & Quick Start

* **Run Client**: See [`RUN_CLIENT.txt`](RUN_CLIENT.txt) (`cd "C:\Multiverse Madness\Magic Madness" && .\gradlew.bat runClient`).
* **Technical Spell & VFX Reference**: [`Magic Madness/docs/SPELLS.md`](Magic%20Madness/docs/SPELLS.md)
* **Architecture & Package Layout**: [`Magic Madness/docs/PROJECT.md`](Magic%20Madness/docs/PROJECT.md)
* **Latest Status**: [`Magic Madness/docs/LATEST.md`](Magic%20Madness/docs/LATEST.md)
* **Player Changelog**: [`Magic Madness/docs/CHANGELOG.md`](Magic%20Madness/docs/CHANGELOG.md)
<!-- #endregion -->
