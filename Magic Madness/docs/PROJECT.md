# Magic Madness — Project Architecture

<!-- #region 1. PACKAGE STRUCTURE -->
## 1. Package Structure (`com.magicmadness`)

* `com.magicmadness` — Mod entrypoints (`MagicMadness`, `MagicMadnessClient`).
* `com.magicmadness.config` — Server & world spell configs (`ModConfigs`, `PowerRules`, `SpellConfig`, `WorldSettings`, `Unit`).
* `com.magicmadness.config.client` — In-game Client & Server config UI (`ClientSettings`, `ConfigChoiceScreen`, `SettingsScreen`, `SettingsPages`, `DirtBackgroundScreen`).
* `com.magicmadness.network` — Custom payload packets (`ModNetwork`, `CastSpellPayload`, `SpellCooldownPayload`, `SpellFxPayload`, `WorldSettingsPayload`, `WorldSettingsEditPayload`) & `client/ClientPayloadHandler`.
* `com.magicmadness.engine.math` — Procedural math helpers (`Vectors`, `Noise`, `Ease`).
* `com.magicmadness.engine.tick` — Server tick-driven spell loop scheduler (`Effects`).
* `com.magicmadness.engine.fx` — Server-side mathematical particle geometry (`ParticleFx`).
* `com.magicmadness.engine.client.render` — Zero-texture 3D vertex geometry painter (`ConstructPainter`).
* `com.magicmadness.engine.client.fx` — Trauma-based Perlin `CameraShake` & additive `ScreenFlash`.
* `com.magicmadness.engine.client.ui` — Winding-safe 2D procedural vector GUI & pixel-art school icon renderer (`GuiShapes`).
* `com.magicmadness.spell` — Core spell definitions & server casting (`MagicSchool`, `Spell`, `Targeting`, `SpellTargets`, `SpellCasting`, `fire/FireballSpell`, `electric/LightningSpell`, `air/WindGustSpell`).
* `com.magicmadness.spell.client` — Keybind `G`, Radial Menu, Cooldown HUD & 3D spell constructs (`ModKeybinds`, `ClientSpellCooldowns`, `SpellRadialMenu`, `SpellFx`, `FireFx`, `LightningFx`, `WindFx`).
<!-- #endregion -->
