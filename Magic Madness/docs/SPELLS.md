# Magic Madness — Complete Technical Architecture: Magic Schools, Spells & Custom 3D VFX

<!-- #region 1. MAGIC SCHOOLS & SPELL ARCHITECTURE -->
## 1. Overview: Magic Schools & Spell Architecture

This document describes in full technical detail how the **6 Magic Schools** (`Heat`, `Air`, `Nature`, `Electric`, `Void`, `Hydro`), the **5 Core Spells** (`Fireball`, `Lightning`, `Poison Cloud`, `Wind Gust`, `Void Step`), and the **dual-layer custom VFX engine** (server-side geometric particle math + client-side 60–240+ FPS procedural 3D meshes & GLSL post-processing shaders) work in **Magic Madness** (`com.magicmadness`).

### 1.1 The 6 Magic Schools (`MagicSchool.java`)
Package: `com.magicmadness.spell.MagicSchool`

Every school is defined as an enum constant in `MagicSchool` with its display name, elemental domain, a fixed **24-bit RGB hex color**, and a dedicated pure square-block pixel-art icon matrix in `GuiShapes`:

| Enum ID | Display Name | Elemental Domain | RGB Hex Color | Pixel-Art Matrix (`GuiShapes`) |
| :--- | :--- | :--- | :--- | :--- |
| `HEAT` | Heat | Fire, heat, lava | `0xFF7A3D` | `HEAT_SCHOOL_PIXELS` (`23×24`) |
| `AIR` | Air | Wind, air | `0xA8F5E0` | `AIR_SCHOOL_PIXELS` (`31×33`) |
| `NATURE` | Nature | Plants, earth, fungi, poison | `0x5CE65C` | `NATURE_SCHOOL_PIXELS` (`32×35`) |
| `ELECTRIC` | Electric | Lightning, electricity | `0x62C6FF` | `ELECTRIC_SCHOOL_PIXELS` (`25×29`) |
| `VOID` | Void | Void, darkness | `0xC07CFF` | `VOID_SCHOOL_PIXELS` (`44×46`) |
| `HYDRO` | Hydro | Water, ice, snow | `0x3898FF` | `HYDRO_SCHOOL_PIXELS` (`38×41`) |

---

### 1.2 The Spell Registry (`Spell.java`)
Package: `com.magicmadness.spell.Spell`

All active spells are registered in the `Spell` enum. Each spell binds a `MagicSchool`, a default cooldown in server ticks (`20 ticks = 1.0 second`), its own primary RGB color, and a method reference (`Consumer<ServerPlayer>`) to its server-side `cast` implementation:

| Spell Enum | Display Name | School | Base Cooldown | Spell RGB Color | Server Entrypoint |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `FIREBALL` | Fireball | `MagicSchool.FIRE` | `40 ticks` (`2.0s`) | `0xFF7A3D` / `0xFF8A2A` | `FireballSpell::cast` |
| `LIGHTNING` | Lightning | `MagicSchool.LIGHTNING` | `100 ticks` (`5.0s`) | `0x62C6FF` / `0x48DBFB` | `LightningSpell::cast` |
| `POISON_CLOUD` | Poison Cloud | `MagicSchool.NATURE` | `160 ticks` (`8.0s`) | `0x5CE65C` / `0x7FD46B` | `PoisonSpell::cast` |
| `WIND_GUST` | Wind Gust | `MagicSchool.AIR` | `60 ticks` (`3.0s`) | `0xA8F5E0` / `0xDDEEF2` | `WindGustSpell::cast` |
| `VOID_STEP` | Void Step | `MagicSchool.DARK` | `240 ticks` (`12.0s`) | `0xC07CFF` / `0x9B5CFF` | `VoidStepSpell::cast` |

- **Dynamic Cooldown Scaling:** `Spell.cooldown()` multiplies the configured spell cooldown (`SpellConfig`) by the global world multiplier `PowerRules.COOLDOWNS.get()`. If `<= 0`, the cooldown is `0 ticks`.
- **School Lookup:** On class load, `Spell` builds a static `EnumMap<MagicSchool, List<Spell>> BY_SCHOOL` so `Spell.ofSchool(school)` returns all spells belonging to a school in $O(1)$ time.

---

### 1.3 Casting Flow, Cooldowns & Target Filtering (`SpellCasting.java` & `SpellTargets.java`)
Packages:
- `com.magicmadness.spell.SpellCasting`
- `com.magicmadness.spell.SpellTargets`

1. **Server Validation & Cooldowns (`SpellCasting.tryCast`):**
   - When the server receives a `CastSpellPayload(spell.getId())`, `SpellCasting.tryCast` checks `player.isAlive()`, `!player.isSpectator()`, and whether the spell is enabled in `PowerRules.allowedSpells()`.
   - Checks `CooldownTracker<Spell> COOLDOWNS` to verify `COOLDOWNS.left(player, spell, 0) <= 0`.
   - When ready, invokes `spell.cast(player)`, starts the cooldown via `COOLDOWNS.start(player, spell, cooldown, 0)`, and syncs it to the client via `SpellCooldownPayload(spell.ordinal(), cooldown)`.
   - On death (`LivingDeathEvent`) or logout (`PlayerLoggedOutEvent`), all active cooldowns and any active `Void Step` state are immediately cleared.
2. **Target Filtering (`SpellTargets.hits`):**
   - No area spell ever uses raw unfiltered entity lists. Every spell filters candidates through `SpellTargets.hits(caster, entity)`:
     - Ignores the caster (`entity == caster`), dead entities (`!entity.isAlive()`), and spectators (`entity.isSpectator()`).
     - Checks `Targeting.isTargetable(entity, caster)` (respects Creative mode, invulnerability, and PvP rules).
     - Checks `PowerRules.FRIENDLY_FIRE` and team/ally relations so allied players and friendly summons are protected when friendly fire is off.
3. **Physical Knockback (`SpellTargets.push`):**
   - Reads `resist = target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)` and scales push force by `scale = max(0.0, 1.0 - resist) * PowerRules.KNOCKBACK.get()`.
   - Whenever a spell pushes an entity, both `target.hasImpulse = true` and **`target.hurtMarked = true`** are set. Setting `hurtMarked = true` forces Minecraft to immediately send a velocity packet to affected players so knockback is felt seamlessly on their client.
<!-- #endregion -->

---

<!-- #region 2. DUAL-LAYER CUSTOM SPELL VFX ARCHITECTURE -->
## 2. The Dual-Layer Custom Spell VFX Architecture

The visual engine combines **two synchronized layers**:
1. **Layer 1 — Server-Side Tick-Driven Geometric Particle Engine (`Effects`, `ParticleFx`, `ParticleBatch`)**
2. **Layer 2 — Client-Side 60–240+ FPS Procedural 3D Mesh & Construct Renderer (`SpellFxPayload`, `SpellFx`, `ConstructPainter`, `Particles`)**

```text
[Server: Spell.cast(player)]
       │
       ├──► 1. Effects.start(level, (lvl, age) -> ...)  [Every server tick (20 Hz)]
       │         └──► ParticleFx + ParticleBatch (Mathematical 3D shapes: spirals, pentagrams, Fibonacci spheres)
       │
       └──► 2. SpellFxPayload.send(...)                 [1 compact network packet!]
                 └──► Client: SpellFx.spawn(...)
                        ├──► ClientTickEvent (20 Hz): Local ambient particles (Particles.java) + Entity tracking
                        └──► RenderLevelStageEvent (60-240+ FPS): ConstructPainter 3D meshes, tapers, flares & shaders
```

### 2.1 Layer 1: Server-Side Geometric Particle Engine (`ParticleFx.java`, `ParticleBatch.java`, `Effects.java`)
Packages:
- `com.magicmadness.engine.fx.ParticleFx`
- `com.magicmadness.engine.fx.ParticleBatch`
- `com.magicmadness.engine.tick.Effects`

Instead of firing individual `ServerLevel.sendParticles` calls one by one, a spell starts a tick task via `Effects.start(level, (lvl, age) -> ...)`. The lambda receives its tick age `age` (`0, 1, 2, ...`) every server tick and stops as soon as it returns `false`.

Inside that tick loop, **`ParticleFx`** constructs mathematical 3D shapes that are bundled via **`ParticleBatch`** and sent to players within `VIEW_RANGE = 128.0` blocks:
- **Color & Transition Dust:**
  - `ParticleFx.dust(rgb, size)` creates a `DustParticleOption` with an exact RGB color and scale (`0.01` to `4.0`).
  - `ParticleFx.fade(fromRgb, toRgb, size)` creates a `DustColorTransitionOptions` that smoothly blends from one RGB color to another over its lifetime.
- **Orthonormal 3D Basis Vectors (`ParticleFx.basis(Vec3 normal)`):**
  - Given any direction vector `n` (such as the flight vector of a Fireball), computes two perpendicular unit vectors `u` and `v` via cross products (`n.cross(ref)`). This allows circles, pentagrams, and spirals to be oriented at any angle in 3D space.
- **Mathematical Shapes in `ParticleFx`:**
  - `disc(level, center, normal, radius, points, turn, particle)`: Draws an oriented 3D circle using `u * cos(angle) + v * sin(angle)`.
  - `discStar(level, center, normal, radius, tips, perEdge, skip, turn, particle)` & `groundStar(...)`: Draws a true **star polygon** (such as a 5-pointed pentagram with `tips = 5, skip = 2`) by linearly interpolating segments between vertex `i` and vertex `i + skip`.
  - `sphere(level, center, radius, count, ...)` & `sphereOut(...)`: Distributes `count` particles evenly across a 3D sphere using the **Fibonacci sphere algorithm** and the golden angle (`GOLDEN_ANGLE = 2.399963229728653 rad`):
    - `y = 1.0 - (2.0 * i + 1.0) / count`
    - `ring = sqrt(1.0 - y * y)`
    - `theta = GOLDEN_ANGLE * i`
    In `sphereOut`, each point receives an outward velocity vector (`dx * speed, y * speed, dz * speed`), producing a uniform spherical shockwave.
  - `zigzag(level, from, to, steps, jitter, particle)`: Linearly interpolates between points A and B and adds random 3D jitter to each intermediate point (excluding the start and end points) for lightning and energy arcs.
  - `helix(level, origin, height, radius, turns, steps, particle)`: Draws a rising 3D spiral.
  - `shockwave(...)`: Shoots `points` particles radially outward in a flat plane at speed `speed`.

---

### 2.2 Layer 2: Client-Side Procedural 3D Renderer (`SpellFxPayload`, `SpellFx`, `Particles`, `ConstructPainter`)
Packages:
- `com.magicmadness.spell.SpellFxPayload`
- `com.magicmadness.spell.client.SpellFx`
- `com.magicmadness.spell.client.Particles`
- `com.magicmadness.engine.client.render.ConstructPainter`

Vanilla particles alone cannot render smooth light beams, 3D glass flasks, branched lightning bolts, or black holes. **Magic Madness** therefore uses a dedicated **client-side 3D vertex renderer**:

1. **Network Synchronization with Deterministic Seed (`SpellFxPayload.java`):**
   - When a spell triggers, the server sends **one compact packet** (`SpellFxPayload`) to all players within `128` blocks containing:
     - `Kind` (`FIREBALL_FLY`, `FIREBALL_BURST`, `LIGHTNING_CHARGE`, `LIGHTNING_BOLT`, `LIGHTNING_ARC`, `POISON_VIAL`, `POISON_CLOUD`, `WIND_GUST`, `VOID_ENTER`, `VOID_LEAVE`, `VOID_AMBUSH`)
     - `Vec3 from` & `Vec3 to` (start/end position or direction vector)
     - `int entity` (tracked entity ID, or `-1`)
     - `int seed` (random seed generated by the server)
     - `int ticks` (lifetime in ticks)
   - **Why `seed` matters:** Every client feeds the exact same `seed` into `Noise.of(seed, ...)` and `Noise.direction(seed, ...)`. As a result, a lightning bolt or flame tongue bends at the **exact same coordinates on every player's screen** without streaming hundreds of vertices over the network.
2. **Lifecycle & Sub-Tick Interpolation (`SpellFx.java`):**
   - Maintains up to `MOST = 64` active `Fx` instances.
   - During `ClientTickEvent.Post` (`20 Hz`), `SpellFx.follow` tracks moving projectiles (such as a flying Fireball) and stores the last **14 positions** in an `ArrayDeque<Vec3> trail`. Simultaneously, `Particles.tick` spawns client-side supporting particles scaled by `ClientSettings.PARTICLE_AMOUNT` and the player's graphics setting (*All = 100%*, *Decreased = 50%*, *Minimal = 15%*).
   - During `RenderLevelStageEvent` at `Stage.AFTER_TRANSLUCENT_BLOCKS` (at the player's full frame rate, e.g. `120–240+ FPS`), `SpellFx.onRenderLevel` computes the exact fractional age:
     $$\text{age} = (\text{gameTime} - \text{fx.born}) + \text{partialTick}$$
   - Performs a **Frustum Culling** check (`painter.visible(at, cullRadius)`) first so off-screen effects cost zero GPU time.
3. **3D Primitives of `ConstructPainter.java`:**
   - `ConstructPainter` extends `PainterSolid` and `PainterLight`, writing colored and additive-glow quads/triangles directly to the vertex buffers:
     - **`lightTaper(a, b, rA, rB, color, alpha)` & `glowTaper(...)`:** Draws a 3D tapered light beam between points `a` and `b` with start radius `rA` and end radius `rB`. `glowTaper` automatically draws both a bright inner core and a soft outer halo (`1.8x` wider at `35%` alpha). Chaining multiple `glowTaper` segments forms curved 3D flames, wind ribbons, and lightning bolts.
     - **`lightDisc(at, radius, color, alpha)` & `glowDisc(...)`:** Camera-facing (billboarded) glowing spheres/discs with a bright core and soft edge.
     - **`flare(at, radius, color, alpha)`:** A bright camera-facing star burst for explosions and impacts.
     - **`circle(center, normal, radius, width, color, alpha)`:** A flat 3D ring with configurable normal vector and stroke width.
     - **`edge(a, b, color, alpha)` / `lightLine` / `glowLine`:** Crisp 3D lines for runes and lightning forks.
     - **`haze(center, rx, ry, rz, color, alpha)`:** A volumetric semi-transparent 3D box for fog banks and plasma domes.
     - **`shell(center, radius, color, alpha)`:** A 3D spherical shell outline.
     - **`shape(Shape, Frame, fillRgb, edgeRgb, glowRgb, alpha, glowStrength)`:** Renders a full **3D mesh** (`Mesh.lathe`, `Mesh.cylinder`, `Mesh.torus`) transformed by an orthonormal 3D `Frame` (`center`, `right`, `up`, `forward`, `scale`), complete with face fill, wireframe edges, and outer glow.

---

### 2.3 Camera Feedback, Procedural Crosshair HUD & GLSL Shockwave Lens (`CameraShake`, `ScreenFlash`, `GuiShapes`, `Lens`)
- **Camera Shake & Screen Flash (`CameraShake.java` & `ScreenFlash.java`):**
  - Nearby players within `SHAKE_REACH = 32.0` blocks feel spell impacts through a quadratic camera shake (`CameraShake.add(strength * shake^2, ticks)` scaled by `ClientSettings.CAMERA_SHAKE`).
  - Nearby players within `FLASH_REACH = 20.0` blocks see a tinted screen flash (`ScreenFlash.add(rgb, alpha * flash, ticks)` scaled by `ClientSettings.SCREEN_FLASH`).
- **Procedural Crosshair Charge & Cooldown Ring (`GuiShapes.java`):**
  - All rings, arcs, and spark strokes around the crosshair are drawn procedurally with `RenderType.gui()` quads (zero PNG textures):
    - **Automatic Winding-Order Correction (`GuiShapes.quad`):** Computes the 2D cross product (`turn = (x0*y1 - x1*y0) + ...`) and reverses the 4 vertices when `turn > 0` so backface culling never hides a quad.
    - **Procedural Circular Arcs (`GuiShapes.arc`):** Subdivides arcs from `fromDegrees` to `toDegrees` (`0°` at top, clockwise: $x = c_x + \sin(\theta)\cdot r,\ y = c_y - \cos(\theta)\cdot r$) in steps of at most `STEP_DEGREES = 5.0°` between `inner = 7.5px` and `outer = 9.5px`.
    - **4-Phase Crosshair Feedback:**
      1. *Charging:* Fills clockwise `0° -> 360°` with 2 white electric spark strokes (`GuiShapes.stroke`) flickering at the moving tip every `45 ms`.
      2. *Full-Charge Pop (`380 ms`):* Flashes white, expands a shock ring from `9.5px` to `19.5px`, and fires 6 jagged 3-segment lightning bolts radially outward.
      3. *Cooldown Ring (`10.0px .. 11.5px`):* Smoothly counts down from `360°` to `0°` using `partialTick`.
      4. *Ready Shine (`450 ms`):* Sweeps a `50°` cyan tail + `8°` white head `360°` around the outer ring when a spell comes off cooldown.
- **GLSL Refraction Lens Shader (`Lens.java` & `shaders/core/lens.fsh`):**
  - For high-energy spell shockwaves, `Lens.draw` copies the rendered world Color and Depth buffers via `GL30._glBlitFrameBuffer` into an offscreen `TextureTarget` at `RenderLevelStageEvent.Stage.AFTER_LEVEL`.
  - Solves per-pixel 3D ray-sphere intersection (`tIn = b - h`, `tOut = b + h`), discards pixels behind closer geometry (`if (scene < t) discard;`), bends the background with concentric wave ripples (`wave = sin(off * 20.0 - Clock * 0.6)`), and samples the background **12 times in a Fibonacci spiral (`turn = k * 2.39996`)** with **chromatic aberration** (Red at `at - fringe`, Green at `at`, Blue at `at + fringe`) and a **Fresnel rim glow (`pow(off, 5.0)`)**.
<!-- #endregion -->

---

<!-- #region 3. DEEP-DIVE ANALYSIS OF THE 5 CORE SPELLS -->
## 3. Deep-Dive Analysis per Spell

---

### 3.1 FIREBALL (`FireballSpell.java` & `FireFx.java`)
- **School:** `MagicSchool.FIRE` (`0xFF5511` / `0xFF7A3D`)
- **Cooldown:** `40 ticks` (`2.0 seconds`)
- **Packages:**
  - Server: `com.magicmadness.spell.fire.FireballSpell`
  - Client VFX: `com.magicmadness.spell.fire.client.FireFx`

#### A. Server-Side Mechanics & Physics
1. **Spawn & Projectile Behavior:**
   - Computes the right-hand cast point via `Targeting.handPoint(player)` and look vector `look = player.getLookAngle()`.
   - Spawns an **anonymous subclass of vanilla `SmallFireball`** at `hand` with initial velocity `look.scale(SPEED)` where `SPEED = 1.25` blocks/tick (`25 blocks/s`).
   - Sets `accelerationPower = 0.1` so the fireball never slows down in flight.
   - **Maximum Flight Lifetime (`MAX_FLIGHT_TICKS = 90`, `4.5s`):** If the fireball has not hit anything after `90 ticks`, it automatically triggers `impact(level, position())` and discards itself (`discard()`).
   - **Water Interaction (`douse`):** Every tick, `tick()` checks `this.isInWaterOrBubble()`. If true (and `waterExtinguishesFire` is enabled), the fireball immediately extinguishes with a hiss (`FIRE_EXTINGUISH`), `20 CLOUD` steam puffs, `14 BUBBLE_POP`, and `10 SMOKE` particles without exploding.
2. **Hit Detection, Fire Spread & Blast Damage:**
   - **Block Hit (`onHitBlock`):** Verifies chunk build permission (`mayInteract(level, pos)`), `GameRules.RULE_DOFIRETICK`, and `igniteBlocks`. Then `spreadFire(level, hitPos)` ignites the hit block and has a **50% chance** to ignite each of the 8 surrounding columns in a `3×3` area (`dx ∈ [-1..1], dz ∈ [-1..1], dy ∈ [0, -1, 1]`).
   - **Entity Hit (`onHitEntity`):** Direct hits deal `directDamage` (`6.0` = `3 hearts`) scaled by `PowerRules.DAMAGE.get()` and set the target on fire for `5 seconds`.
   - **Explosion (`blast` within `BLAST_RADIUS = 2.5` blocks):**
     - Finds all `LivingEntity` within `2.5` blocks that pass `SpellTargets.hits(caster, e)` and a **Line-of-Sight raycast** (`Targeting.clearPath(level, at, eyePosition)` so walls block the blast).
     - Marks each target for **Ash Disintegration** (`ClientSettings.ASH_DISINTEGRATION`) so fatal hits dissolve the target into rising embers and ash.
     - **Distance-Scaled Splash Damage:**
       $$\text{falloff} = 1.0 - 0.6 \times \frac{\text{distance}}{2.5}$$
       $$\text{damage} = 4.0 \times \text{PowerRules.DAMAGE.get()} \times \text{falloff}$$
       Deals `4.0` (`2 hearts`) at the center, tapering to `40%` (`1.6` damage) at the outer edge.
     - Ignites victims for `burnSeconds` (`3–4s`) and pushes them radially outward (`strength = 0.45 * falloff`, `lift = 0.25 * falloff`).

#### B. Server-Side Particle Effects (`FireballSpell.java`)
- **1. Hand Ignition (`ignition`, lasts `8 ticks`):**
  - Draws a shrinking, rotating golden disc (`ParticleFx.disc`, `radius = 0.55 * (1.0 - age / 9.0)`, `18` points, spin `age * 0.45 rad`) and a **counter-rotating 5-pointed golden pentagram** (`ParticleFx.discStar` with `5` tips, `skip = 2`, spin `-age * 0.35 rad`) transitioning from `GOLD` (`0xFFD36B`) to `FLAME` (`0xFF6A1A`).
  - At `age == 0`, `ParticleFx.implosion` pulls `16 SMALL_FLAME` particles from `0.6` blocks inward to the hand.
- **2. Flight Trail (`trail`, every tick in flight):**
  - **Core:** Rotating mini-sphere (`ParticleFx.sphere`, `r = 0.25`, `6` points) of `CORE` (`0xFFE27A`) + transition dust `FLAME` (`0xFF6A1A`) $\rightarrow$ `EMBER` (`0x7A1F0A`).
  - **Double DNA Helix:** Uses `ParticleFx.basis(direction)` to compute perpendicular axes `u` and `v` and draws **2 opposing spiral strands** (`strand = 0..1`, `step = 0..2`) at radius `0.45` blocks (`angle = age * 0.9 + strand * PI + step * 0.45`) using `FLAME` and `SMALL_FLAME`.
  - **Heat Tail:** `3` steps behind the fireball (`0.35` blocks per step) with shrinking `GOLD -> EMBER` dust, `LARGE_SMOKE`, and a `35%` chance of `LAVA` / `FALLING_LAVA` drips.
- **3. Impact Animation (`impact`, lasts `50 ticks` = `2.5s`):**
  - **Tick 0:** `FLASH`, `EXPLOSION`, a Fibonacci sphere (`sphereOut`) of **`70 FLAME`** (`speed = 0.32`) + **`22 LARGE_SMOKE`** (`speed = 0.18`), `35 LAVA` sparks, a 5-pointed `groundStar` pentagram (`r = 2.0`), a radial `shockwave` (`36 FLAME` points), and `GENERIC_EXPLODE` + `FIRECHARGE_USE` audio.
  - **Ticks 1–10:** Expanding ground rings (`ParticleFx.ring`) growing from `0.35` to `2.85` blocks. At `age == 6`, spawns a dark scorch ring (`SCORCH = 0x2A1A12`, `40` points at `r = 2.3`).
  - **Ticks 2–34:** Rising smoke plume (`CAMPFIRE_COSY_SMOKE` with `dy = 0.07` + `EMBER -> SCORCH` soot) curling up to `3.6` blocks high.

#### C. Client-Side Custom 3D VFX (`FireFx.java`)
Palette: `WHITE (0xFFF6D6)`, `YELLOW (0xFFD84A)`, `ORANGE (0xFF6A1A)`, `RED (0xC8230C)`, `EMBER (0x5A1408)`, `SMOKE (0x261B16)`.
1. **Flying Fireball (`FireFx.fly` via `SpellFxPayload.Kind.FIREBALL_FLY`):**
   - **Sub-tick interpolation:** `pos = fx.was.lerp(fx.at, partialTick)`.
   - **14-Point Light Trail (`trail`):** Connects the 14-tick `ArrayDeque<Vec3> trail` with a dual tapered beam: a wide `glowTaper` (`ORANGE -> RED`, thickness up to `0.32`) and a bright `lightTaper` core (`YELLOW -> ORANGE`, thickness up to `0.15`), plus a rising `SMOKE` `lightDisc` every 3rd point.
   - **6 Waving Flame Ribbons (`ribbon = 0..5`):** 6 procedural ribbons of `5` segments (`1.3` blocks long) waving with a sine wave perpendicular to flight (`wave = sin(phase + u * 5.0) * 0.18 * u`) and tapering from `0.16` to `0.02`.
   - **Boiling 3D Fire Core (`FireFx.ball`):**
     - Outer `glowDisc` (`RED` + `ORANGE`, `1.9r`), **4 animated fire lobes** shifting each frame via `Noise.direction(seed + k * 19, step)` (`ORANGE` + `YELLOW` `lightDisc`), and a `YELLOW` + `WHITE` core `glowDisc` with a `flare`.
     - **Camera Fade (`close`):** When closer than `2.2` blocks to the camera, `close = clamp((camDist - 0.6) / 1.6, 0.2, 1.0)` dims the white core so the caster's view is never blinded on cast.
2. **Explosion (`FireFx.burst` via `SpellFxPayload.Kind.FIREBALL_BURST`, lasts `40 ticks`):**
   - **Ticks 0–4:** Large `WHITE`/`YELLOW` `flare` (`r = 3.6`) and `ORANGE` `glowDisc` (`r = 3.0`).
   - **Ticks 0–22 (`7` rising fireballs):** `7` separate `ball()` lobes swell with a spring curve (`Ease.backOut(age / 6.0)`), drift outward, and rise (`rise = age * (0.04 + 0.03 * n)`).
   - **Ticks 0–10 (Ground heat ring):** Flat `circle` expanding from `0.6` to `4.8` blocks (`ORANGE` + `YELLOW`).
   - **Ticks 0–18 (`10` Dancing Fire Tongues — `FireFx.tongue`):** `10` procedural fire tongues in a circle, each built from `5` chained `glowTaper` + `lightTaper` segments reaching `2.4` blocks high and twisting in 3D via `sin(age * 0.45 + v * 4.0 + index)` and `cos(...)`.
   - **Ticks 3–40 (Mushroom Smoke Cloud):** `9` dark `SMOKE` `lightDisc` clouds rising to `2.7` blocks and billowing wider at the cap.

---

### 3.2 LIGHTNING (`LightningSpell.java` & `StormFx.java`)
- **School:** `MagicSchool.LIGHTNING` (`0x00D2FF` / `0x62C6FF`)
- **Cooldown:** `100 ticks` (`5.0 seconds`)
- **Packages:**
  - Server: `com.magicmadness.spell.lightning.LightningSpell`
  - Client VFX: `com.magicmadness.spell.lightning.client.StormFx`

#### A. Server-Side Mechanics: Aim Tracking, Virtual Lightning & Chain Lightning
1. **14-Tick Charge Phase with Real-Time Entity Tracking (`CHARGE = 14 ticks` = `0.7s`):**
   - On cast, runs `Targeting.aim(player, level, RANGE)` with **`RANGE = 40.0` blocks** (`0.5m` aim assist).
   - If locked onto a moving enemy, the tick loop updates **`target[0] = aim.current()`** every tick during the `14-tick` charge so the ground rune tracks the target as it tries to flee.
   - Sends `SpellFxPayload.Kind.LIGHTNING_CHARGE` and plays `BEACON_ACTIVATE` + rising `CREEPER_PRIMED` static ticks (pitch `1.2` $\rightarrow$ `2.04`).
2. **Primary Strike at `age == 14` (`strike`):**
   - **Virtual `LightningBolt` (No Vanilla Blocky Bolt Entity Spawned):**
     Creates `EntityType.LIGHTNING_BOLT.create(level)` in memory **without calling `level.addFreshEntity(bolt)`**, sets `bolt.moveTo(spot)` and `bolt.setCause(caster)`, filters targets in a `6×12×6` box through `EventHooks.onEntityStruckByLightning` (canceling hits on the caster and allies), marks enemies for ash disintegration, and invokes **`struck.thunderHit(level, bolt)`** (dealing `5.0` strike damage, igniting targets, and triggering all vanilla mob transformations: Creeper $\rightarrow$ Charged Creeper, Villager $\rightarrow$ Witch, Pig $\rightarrow$ Zombified Piglin, Mooshroom toggle).
   - **Ground Shock & Terrain Debris (`shock` & `igniteAround`):**
     - Enemies within `SHOCK_RADIUS = 3.0` blocks receive `shockDamage` (`4.0` = `2 hearts`), **`Slowness III`** (`amplifier = 2`) for `SHOCK_TICKS = 30` (`1.5s`), and an upward jolt (`strength = 0.35`, `lift = 0.25`).
     - Reads `level.getBlockState(ground)` and blasts `40 BlockParticleOption(ParticleTypes.BLOCK, groundState)` debris chunks upward.
3. **Chain Lightning (`age > 14`, `chainStep`):**
   - Every **`CHAIN_EVERY = 2 ticks` (`0.1s`)** after the main strike, searches from the last struck point (`chainFrom[0]`) for the nearest unhit enemy within **`CHAIN_REACH = 6.0` blocks** with clear Line-of-Sight (`Targeting.clearPath`).
   - **Weather-Dependent Jumps (Storm Bonus):**
     - Clear weather: chains up to **`maxChainsClear = 3` targets**.
     - Rain or thunder at the strike position (`level.isRainingAt(BlockPos.containing(spot))`): increases to **`maxChainsRain = 5` targets**!
   - Each chained target receives **`4.0 * PowerRules.DAMAGE.get()`** lightning damage, **`Slowness III`** (`1.5s`), knockback (`0.25` push, `0.15` lift), a server-side dual `ParticleFx.zigzag` (`16` steps `ELECTRIC_SPARK` + `12` steps `CYAN -> DEEP` dust), and a client-side `SpellFxPayload.Kind.LIGHTNING_ARC` between chest heights (`y + height * 0.6`).

#### B. Client-Side Custom 3D VFX (`StormFx.java`)
Palette: `WHITE (0xF5FCFF)`, `PALE (0xB8F2FF)`, `CYAN (0x48DBFB)`, `BLUE (0x1B9CFC)`, `DEEP (0x0A3D91)`, `STORM (0x1E272E)`, `GREY (0x3A4650)`.
1. **Phase 1: Charging Ground Rune & Thundercloud (`StormFx.charge`, `14 ticks`):**
   - **3-Ring Self-Drawing Arcane Ground Rune (`RUNE = 2.2` blocks):** Draws 3 concentric rings (`r = 0.45, 0.72, 1.0 * 2.2`, `40` segments) at `y + 0.04` that sweep closed as `p = age / 14.0` progresses (`sweep = p * 2 * PI`) while counter-rotating (`spin * (r % 2 == 0 ? 1.0 : -1.3)`).
   - **8 Radial Rune Spokes with Glyph Hooks:** Once `p > 0.25`, 8 spokes grow from the inner to outer ring with angled glyph hooks (`hook = angle + 0.22 rad`).
   - **Crackling Ground Sparks (`jag`):** `3` to `8` small 5-segment jagged arcs jumping across the rune.
   - **16-Part Rotating Thundercloud (`CLOUD = 18.0` blocks high):** Spawns `18` blocks above the target from `16` rotating `STORM` and `GREY` `lightDisc` clouds (`radius = 1.6..3.2`) with internal `CYAN`/`WHITE` sheet-lightning flashes.
   - **Leader Thread (`p > 0.7`):** In the final `0.2s` before impact, a thin `CYAN`/`WHITE` `glowTaper` + `lightTaper` thread links the ground rune to the cloud `18` blocks above.
2. **Phase 2: The Thunderbolt (`StormFx.bolt`, `32 ticks`):**
   - **Stepped Leader (`age < LEADER = 1.5 ticks`):** A dim leader races from `18` blocks high to the ground along a `22`-segment jagged path (`StormFx.jagged`) using `Noise.direction(seed, i) * sin(PI * u)` so both endpoints remain anchored.
   - **3 Return Strokes (`STROKES = 3`, `STROKE_GAP = 2.0 ticks`):**
     - Starting at `age = 1.5`, **3 blinding main discharges** fire in rapid succession (`shape = seed + stroke * 31`), each rendered with **3 concentric layers** across all `22` segments:
       1. Outer corona: `DEEP` blue `glowTaper` (`thickness * 4.5`, `35%` alpha);
       2. Middle plasma: `CYAN` `glowTaper` (`thickness * 1.8`, `85%` alpha);
       3. Inner core: `WHITE` `lightTaper` (`thickness * 0.36`, `100%` alpha).
     - **5 Branches (`b = 0..4`):** 5 jagged 6-segment side branches fork outward and downward (`CYAN` `glowTaper` + `WHITE` `lightTaper`).
   - **Sky Flash, Plasma Dome & Glowing Ground Cracks:**
     - Calls `Minecraft.getInstance().level.setSkyFlashTime(2)` to flash the world sky.
     - Renders a `5.0`-block `flare`, an expanding 3D plasma dome (`painter.haze` up to `2.9m` wide × `2.0m` high), a `4.5m` ground shock `circle`, and **9 radial jagged ground cracks** (`jag`) over a charred scorch mark (`0x0D1117`) cooling quadratically (`cool * cool`) from white-hot to deep blue.
3. **Phase 3: Chain-Lightning Arc (`StormFx.arc`, `9 ticks`):**
   - Draws two intertwined 10-segment jagged arcs between chained enemies (primary arc with `0.14 * len` offset and secondary arc with `0.05 * len` offset, updating `frameSeed = seed + (int)(age * 1.5)` every `~0.67` tick) with `flare` bursts at both ends.

---

### 3.3 POISON CLOUD (`PoisonSpell.java` & `PoisonFx.java`)
- **School:** `MagicSchool.NATURE` (`0x2ED573` / `0x5CE65C`)
- **Cooldown:** `160 ticks` (`8.0 seconds`)
- **Packages:**
  - Server: `com.magicmadness.spell.nature.PoisonSpell`
  - Client VFX: `com.magicmadness.spell.nature.client.PoisonFx`

#### A. Server-Side Mechanics: Parabolic Flask Arc & Escalating Toxin
1. **Mathematical Parabolic Throw (No Vanilla Potion Entity):**
   - Raycasts up to **`RANGE = 24.0` blocks** via `Targeting.aimPoint(player, level, 24.0)` from `hand = Targeting.handPoint(player)`.
   - Computes flight duration and arc peak from distance:
     $$\text{flight} = \text{clamp}\left(\text{round}\left(\frac{\text{distance}}{1.2}\right),\ 6,\ 18\right) \text{ ticks } (0.3\text{s} - 0.9\text{s})$$
     $$\text{peak} = 1.2 + \text{distance} \times 0.15 \text{ blocks}$$
   - At each tick (`t = (age + 1) / flight`), exact flask position is:
     $$\text{point}(t) = \text{lerp}(\text{hand}, \text{target}, t) + \left(0,\ \sin(t \cdot \pi) \times \text{peak},\ 0\right)$$
   - Trails `TOXIC -> FOG` dust, green `ENTITY_EFFECT` droplets, and `SLIME_BALL` item particles in flight.
2. **Flask Shatter (`shatter` at `t >= 1.0`) & Swamp Cloud (`160 ticks` = `8.0s`):**
   - Shatters at `target` with `SPLASH_POTION_BREAK`, `BREWING_STAND_BREW`, and `SLIME_SQUISH` sounds, `24 SLIME_BALL` particles, **`10 GLASS_BOTTLE` glass shards**, a 36-point `ENTITY_EFFECT` `shockwave`, and `SpellFxPayload.Kind.POISON_CLOUD` for **`DURATION = 160 ticks` (`8.0s`)**.
   - **Spread & Fade (`currentRadius`):**
     - Max radius **`RADIUS = 3.5` blocks**.
     - First `SPREAD_TIME = 8 ticks` (`0.4s`): grows from `30%` (`1.05m`) to `100%` (`3.5m`).
     - Final `FADE_TIME = 25 ticks` (`1.25s`): shrinks smoothly to `40%` (`1.4m`).
   - **Swamp Gas Belches (`belch`):** Every `14 ticks` (`age % 14 == 7`), a gas pocket bursts inside the swamp (`8 SNEEZE` + `10 FOG -> DARK` dust + `SLIME_SQUISH_SMALL`).
3. **Escalating Toxin (`poison`, every `PULSE = 10 ticks` = `0.5s`):**
   - Tracks exposure time per victim in `Map<UUID, Integer> inside` (`inside.merge(uuid, 10, Integer::sum)`).
   - Every `0.5s`, enemies inside the cylinder (`r = currentRadius`, height `-0.5 .. +2.0` blocks) receive:
     - **`Slowness I`** (`amplifier = 0`) for `40 ticks` (`2.0s`).
     - **Initial Exposure (`soaked <= 40 ticks` / `2.0s`):** **`Poison I`** (`amplifier = 0`) for `50 ticks` (`2.5s`).
     - **Escalated Exposure (`soaked > 40 ticks` / over `2.0s` in cloud):** Automatically upgrades to **`Poison II`** (`amplifier = 1`), plays `SoundEvents.WITCH_DRINK`, and bursts `10` toxic green `ENTITY_EFFECT` spores from the victim!

#### B. Client-Side Custom 3D VFX (`PoisonFx.java`)
Palette: `BRIGHT (0xB8FF6B)`, `TOXIC (0x63E63A)`, `FOG (0x3F7A2A)`, `MURK (0x1F3A14)`, `CORK (0x9C6B3B)`.
1. **True 3D Glass Alchemy Flask Mesh (`PoisonFx.VIAL` & `PoisonFx.CORK_SHAPE`):**
   - Builds a **true 3D alchemy flask mesh** at startup using a **surface of revolution (`Mesh.lathe`)** with `14` radial segments and `9` profile rings from base to lip:
     - Base: `(r=0.02, y=-0.26)` $\rightarrow$ `(r=0.17, y=-0.22)`
     - Bulbous belly: `(r=0.24, y=-0.12)` $\rightarrow$ `(r=0.22, y=0.0)`
     - Narrowing shoulder & neck: `(r=0.14, y=0.09)` $\rightarrow$ `(r=0.07, y=0.16)` $\rightarrow$ `(r=0.07, y=0.22)`
     - Flared pouring lip: `(r=0.10, y=0.24)` $\rightarrow$ `(r=0.09, y=0.26)`
   - Topped by a second 3D mesh (`CORK_SHAPE`): a **10-sided 3D cylinder (`Mesh.cylinder`)** from `y = 0.20` to `y = 0.27` in cork brown (`CORK = 0x9C6B3B`).
   - **Flight & Tumbling (`PoisonFx.vial`):**
     - Evaluates the exact parabolic arc `arc(from, to, t)` and tangent `ahead` (`t + 0.04`), tumbles the 3D `Frame` around its transverse axis (`spin = age * 0.9 rad`), and renders the translucent glass flask (`alpha = 0.55`, `FOG` fill, `BRIGHT` wireframe edges, `TOXIC` glow), the cork (`alpha = 0.85`), a **sloshing `TOXIC`/`BRIGHT` liquid core** at `frame.at(0, -0.05, 0)`, and a 5-segment dripping `glowTaper` trail.
2. **The 3D Poison Swamp (`PoisonFx.cloud`):**
   - **Ground Rune & Rotating Pentagram:** At `y + 0.04`, draws a `TOXIC` outer `circle`, a `MURK` swamp floor, and a slowly rotating **5-pointed star pentagram** (`painter.edge` in `BRIGHT` connecting vertex `k` to `k + 2`).
   - **Volumetric Fog Box:** A `painter.haze` spanning the full radius (`1.3m` high).
   - **22 Rolling Fog Banks (`i = 0..21`):** `22` large `FOG` and `MURK` `lightDisc` clouds drifting in noise-driven circles.
   - **5 Rising Spiral Gas Tendrils (`t = 0..4`):** `5` tendrils of `8` chained `lightTaper` + `glowTaper` segments spiraling up to `1.6m` and fading at the top (`1.0 - u`).
   - **14 Swelling & Popping 3D Poison Bubbles (`b = 0..13`):**
     - Each bubble cycles on `cycle = ((time * speed) + offset) % 1.0`.
     - During `cycle < 0.9`, rises to `1.4m` and swells from `0.06` to `0.20` (`TOXIC` `glowDisc` + `BRIGHT` specular highlight).
     - During the final **`10%` (`cycle >= 0.9`), pops open**: radius snaps to `2.2×` while alpha fades to `0`!

---

### 3.4 WIND GUST (`WindGustSpell.java` & `WindFx.java`)
- **School:** `MagicSchool.AIR` (`0xDDEEF2` / `0xA8F5E0`)
- **Cooldown:** `60 ticks` (`3.0 seconds`)
- **Packages:**
  - Server: `com.magicmadness.spell.air.WindGustSpell`
  - Client VFX: `com.magicmadness.spell.air.client.WindFx`

#### A. Server-Side Mechanics: Self-Rescue, Advancing Cone & Projectile Deflection
1. **Immediate Caster Utility (`cast`):**
   - **Extinguish Self:** If burning (`player.isOnFire()`), calls `player.clearFire()` and plays `FIRE_EXTINGUISH`.
   - **Mid-Air Fall Cushion (`CUSHION = 20 ticks` = `1.0s`):** If falling (`!player.onGround() && player.getDeltaMovement().y < 0`), calls `player.resetFallDistance()`, grants **`Slow Falling`** for `1.0s`, and spawns a 16-point `CLOUD` ring beneath the feet.
2. **Advancing 120° Pressure-Wave Cone (`8 ticks`, `RANGE = 8.0` blocks, `WAVE_SPEED = 1.0` block/tick = `20 m/s`):**
   - **Cone Filter via Dot Product:**
     - Visual half-angle is `50°` (`100°` total); physical hitbox uses `60°` half-angle (**`120°` total cone width**, `cos(60°) = 0.5`) with Line-of-Sight check (`Targeting.clearPath`):
       $$\text{distance} \le \text{front} \quad \wedge \quad \widehat{(\text{pos} - \text{origin})} \cdot \vec{\text{look}} \ge \cos(60^\circ)$$
   - **Enemy Knockback (`push`):**
     - Tracked via `Set<UUID> pushed` so each target is hit once as the wave front reaches it:
       $$\text{closeness} = 1.0 - \frac{\text{distance}}{8.0} \times 0.6 \quad (100\% \rightarrow 40\%)$$
       $$\text{strength} = 2.2 \times \text{closeness}, \qquad \text{lift} = 0.5$$
     - Extinguishes burning targets (`entity.clearFire()`) and douses fire blocks in the cone when `extinguishFire` is enabled.
   - **Projectile Deflection & Item Blowback (`blowAway`):**
     - Pushes loose `ItemEntity` and `ExperienceOrb` instances (`+0.6 * look`, `+0.25 Y`).
     - **Deflects & Claims Projectiles (`Projectile`):** Reverses all incoming arrows, tridents, fireballs, and wind charges in the cone along `look.scale(max(0.8, speed * 1.6)) + (0, 0.1, 0)`, sets **`shot.setOwner(caster)`** (so deflected projectiles hit the original shooter and credit the caster), marks `hasImpulse = true` & `hurtMarked = true`, plays `BREEZE_DEFLECT`, and spawns `6 CRIT` + `4 CLOUD` particles.

#### B. Client-Side Custom 3D VFX (`WindFx.java`, lasts `LIFE = 14 ticks`)
Palette: `WHITE (0xFFFFFF)`, `PALE (0xEAF6FA)`, `SKY (0xB8E0EE)`, `DEEP (0x78B4CC)`.
1. **3 Spiral Foot Ribbons (`WindFx.swirl`, ticks `0..9`):**
   - Draws **3 spiral air currents** (`r = 0..2`) of `16` segments each coiling `1.5` turns (`3 * PI`) from the caster's feet up to `2.3` blocks high (`WHITE` `lightTaper` + `SKY` `glowTaper`).
2. **The 3D Wind Cone (`WindFx.gust`):**
   - Builds an orthonormal 3D frame `(ahead, side, up)` around `ahead = fx.to.normalize()`.
   - **4 Layered 3D Arc Crescents (`layer = 0..3`):**
     - `4` curved wind crescents (`0.55` blocks apart), each built from `18` segments across `-50° .. +50°`.
     - **3D Camber (`bow` & `wave`):** Bows forward at the center via `bow = d * (1.0 + 0.12 * cos(yaw * 1.8))` and undulates vertically via `wave = sin(u * PI * 3.0 + time * 0.5 + layer) * 0.12`, tapering at the tips (`tip = sin(PI * u)`) across 3 layers (`SKY` `glowTaper` `0.22`, `WHITE` `lightTaper` `0.09`, and `PALE` highlight edge `+0.04 up`).
   - **6 Rolling Air Curls (`WindFx.curl`, `w = 0..5`):** `6` corkscrew vortices of `10` segments curling `1.6 * PI` radians inward (`r = size * (1.0 - 0.6 * u)`) along the wave front.
   - **12 High-Speed Wind Needles (`s = 0..11`):** `12` needle-thin streaks (`1.8m` long, tapering `0.0` $\rightarrow$ `0.07`) racing through the cone at `16 blocks/s`.

---

### 3.5 VOID STEP (`VoidStepSpell.java`, `VoidFx.java`, `ClientVoidState.java` & `void_world.fsh`)
- **School:** `MagicSchool.DARK` (`0x6C5CE7` / `0xC07CFF`)
- **Cooldown:** `240 ticks` (`12.0 seconds`)
- **Duration:** `200 ticks` (`10.0 seconds`)
- **Packages:**
  - Server: `com.magicmadness.spell.dark.VoidStepSpell`
  - Client 3D VFX: `com.magicmadness.spell.dark.client.VoidFx`
  - Client Shader & Glow Controller: `com.magicmadness.spell.dark.client.ClientVoidState`
  - GLSL Fragment Shader: `assets/magicmadness/shaders/program/void_world.fsh`

#### A. Server-Side Mechanics: True Stealth, Equipment Spoofing, AI Blindness & Ambush Strike
1. **State Tracking (`Walk` record in `ACTIVE` map):**
   - Stores `new Walk(wasSilent, beforeInvis, gameTime)` so prior silence and any pre-existing `INVISIBILITY` potion duration are restored accurately on exit.
2. **True Invisibility + Network Equipment Spoofing (`hideEquipment`):**
   - Standard Minecraft `INVISIBILITY` leaves armor, held items, and offhand shields visible to other players. `VoidStepSpell` solves this by:
     1. Applying a hidden `MobEffectInstance(MobEffects.INVISIBILITY, 200, 0, false, false, true)` (`visible = false` $\rightarrow$ **zero potion bubbles**);
     2. Setting `player.setSilent(true)` (silent footsteps and swimming);
     3. Applying *Speed II* / a **`+50%` Movement Speed modifier** (`SPEED_BONUS = 0.5`, `Operation.ADD_MULTIPLIED_TOTAL`);
     4. **Spoofing all 6 equipment slots (`MAINHAND`, `OFFHAND`, `HEAD`, `CHEST`, `LEGS`, `FEET`) as empty** by broadcasting `ClientboundSetEquipmentPacket(player.getId(), emptySlots)` with `ItemStack.EMPTY` via `chunkSource.broadcast(player, packet)`.
   - **Anti-Flicker & Late-Tracker Protection:**
     - On `PlayerEvent.StartTracking`, immediately sends the empty equipment packet to any player entering render distance.
     - On `LivingEquipmentChangeEvent`, re-broadcasts the empty equipment packet at end-of-tick so swapping weapons during stealth never flashes the item.
3. **Complete Mob-AI Blindness:**
   - Clears `mob.setTarget(null)` for all `Mob` entities targeting the caster within `clearAggroBlocks` (`24–64m`) and cancels `LivingChangeTargetEvent` while `VoidStepSpell.isInVoid(entity)` is true.
4. **Caster-Only Whispers, Footsteps & Enemy Beacons (`whisper`):**
   - Uses player-specific `level.sendParticles(player, ...)` (visible **only** to the caster):
     - Every `3 ticks` while moving: purple `REVERSE_PORTAL` footstep wisps;
     - Every `10 ticks`: `VIOLET` dust + `REVERSE_PORTAL` beacon `0.5m` above every enemy within **`enemyRadarBlocks = 32.0` blocks**;
     - At `age == 160` (`2.0s` before stealth expires): plays a private `BEACON_DEACTIVATE` warning cue.
5. **The Ambush Strike (`VoidStepSpell.ambush` on `LivingIncomingDamageEvent`):**
   - Landing a direct hit from stealth:
     - Multiplies total attack damage by **`ambushDamageMultiplier = 1.5×` (`+50%` damage)**!
     - Inflicts **`Blindness I`** and **`Slowness II`** (`amplifier = 1`) for **`40 ticks` (`2.0 seconds`)**.
     - Plays `WARDEN_SONIC_BOOM` + `ENDERMAN_TELEPORT` + `PLAYER_ATTACK_CRIT`, spawns a 40-point Fibonacci sphere of `REVERSE_PORTAL` + `SOUL_FIRE_FLAME` + `SCULK_SOUL`, sends `SpellFxPayload.Kind.VOID_AMBUSH`, and immediately ends stealth (`leave(player)`).

#### B. Client-Side Custom 3D VFX (`VoidFx.java`)
Palette: `BLACK (0x040008)`, `ABYSS (0x07000E)`, `DEEP (0x4A1A99)`, `VIOLET (0x9B5CFF)`, `PALE (0xE2CCFF)`, `WHITE (0xFAF5FF)`.
1. **The "Black-Hole Sphere" (`VoidFx.sphere`):**
   - Combines outer violet glow layers with an opaque pitch-black core:
     1. Outer `DEEP` (`0x4A1A99`) `glowDisc` at `1.9×` radius;
     2. Middle `VIOLET` (`0x9B5CFF`) `glowDisc` at `1.25×` radius;
     3. Two opaque `ABYSS` (`0x07000E`) `lightDisc` layers at `1.05×` and `0.80×` radius darkening the background;
     4. A razor-sharp `VIOLET` + `PALE` **gravitational-lens ring** (`painter.circle` at `1.08×` radius facing the camera).
2. **Void Enter — Implosion Followed by Burst (`VoidFx.enter`, `30 ticks`):**
   - **Phase 1 (`age < 6 ticks` — Implosion):** `18` violet light rays (`lightTaper` + `glowTaper`) pull inward from `3.6m` to the caster's chest while the black `sphere()` grows from `0.15m` to `1.1m`.
   - **Phase 2 (`age >= 6 ticks` — Burst):** Snaps open with `Ease.backOut` to `1.45m` radius with a `3.0m` `flare`, two expanding shock rings (`6.0m` ground ring + `3.8m` chest ring), and **`18` flying `ABYSS`/`VIOLET` spatial rift shards**.
3. **Void Leave (`VoidFx.leave`, `16 ticks`):**
   - Brief `flare`, black `sphere()` collapsing from `1.35m` to `0.05m`, shrinking ground ring, and `10` fading light streaks.
4. **Void Ambush — 3 Diagonal Spatial Claw Rifts (`VoidFx.strike`, `14 ticks`):**
   - Computes the strike plane `(right, up)` perpendicular to `ahead` and slashes **3 parallel diagonal claw rifts** (`k = -1, 0, 1`, length `1.9m`) across the victim (`0.0 -> 0.6 -> 0.0` `VIOLET` `glowTaper` outer slash + pitch-black `0.0 -> 0.22 -> 0.0` `ABYSS` `lightTaper` core + `PALE` cutting edge).

#### C. Custom GLSL Post-Processing Shader & Wallhack Glow (`ClientVoidState.java` & `void_world.fsh`)
1. **Wallhack Enemy Outlines (`ClientVoidState.markEnemies`):**
   - Scans loaded entities within `32.0` blocks every client tick and enables client-side **Glowing outline** (`GlowFlag.setGlowing(entity, true)`) on hostile targets so Minecraft's outline buffer renders their silhouettes through solid walls.
2. **Shader Lifecycle & Heartbeat:**
   - Loads `magicmadness:shaders/post/void_world.json` in `GameRenderer`, plays a local `WARDEN_HEARTBEAT` every `20 ticks` (`1.0s`), and smoothly interpolates `Intensity` (**`8 ticks` fade-in**, **`15 ticks` fade-out** scaled by `ClientSettings.VOID_SHADER_STRENGTH`).
3. **Per-Pixel GLSL Fragment Shader Pipeline (`void_world.fsh`):**
   - **Step 1 — Luminance (`luma`):** `dot(color, vec3(0.299, 0.587, 0.114))`.
   - **Step 2 — 3×3 Sobel Edge-Detection Filter:**
     Samples the 8 neighboring texels (`tl, tc, tr, ml, mr, bl, bc, br`) and computes:
     $$g_x = -tl - 2\cdot ml - bl + tr + 2\cdot mr + br$$
     $$g_y = -tl - 2\cdot tc - tr + bl + 2\cdot bc + br$$
     $$\text{edge} = \text{clamp}\left(\sqrt{g_x^2 + g_y^2} \times 2.5,\ 0.0,\ 1.0\right)$$
   - **Step 3 — Shadow World with Violet Contours:**
     Replaces world colors with a deep purple abyss (`vec3(0.04, 0.015, 0.08) + vec3(0.16, 0.10, 0.24) * pow(light, 1.6)`) and highlights all block/world edges in bright violet (`+ vec3(0.62, 0.38, 1.0) * edge`).
   - **Step 4 — Crimson Conversion of Marked Enemy Outlines (`outline`):**
     Detects Minecraft's pure-white entity outline (`R, G, B > 0.97`) via `outline = smoothstep(0.97, 0.995, min(original.r, min(original.g, original.b)))` and recolors those pixels to **bright crimson red (`vec3(1.0, 0.3, 0.35)`)**!
   - **Step 5 — Breathing Pulse & Vignette:**
     Modulates brightness by a `1 Hz` heartbeat sine wave (`pulse = 0.9 + 0.1 * sin(Time * 6.28318)`) and darkens screen edges with a radial vignette (`smoothstep(0.25, 0.95, length(fromCenter) * 1.3)`), blended by `Intensity`.
<!-- #endregion -->

---

<!-- #region 4. MASTER SPELL & VFX COMPARISON TABLE -->
## 4. Master Comparison Table (All 5 Core Spells)

| Property | **Fireball** (`FIRE`) | **Lightning** (`LIGHTNING`) | **Poison Cloud** (`NATURE`) | **Wind Gust** (`AIR`) | **Void Step** (`DARK`) |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Base Cooldown** | `2.0s` (`40t`) | `5.0s` (`100t`) | `8.0s` (`160t`) | `3.0s` (`60t`) | `12.0s` (`240t`) |
| **Range & Radius** | `25 m/s` flight (`4.5s` max), `2.5m` blast | `40m` raycast, `3.0m` shock, `6.0m` chain | `24m` throw, `3.5m` cloud (`8.0s` duration) | `8.0m` cone (`120°`), `20 m/s` wave | Self (`10.0s` stealth), `32m` radar, `24m` de-aggro |
| **Damage & Effects** | `3 hearts` direct + `2 hearts` AoE, `4s` burn, ash disintegration | `2.5 hearts` strike + `2 hearts` shock/chain, *Slowness III* (`1.5s`) | *Slowness I* + *Poison I* $\rightarrow$ after `2.0s` in cloud **`Poison II`** | Knockback (`up to 2.2` + `0.5` lift), douses fire, *Slow Falling* (`1s`), deflects projectiles | **Ambush: `1.5×` melee damage** + *Blindness I* & *Slowness II* (`2.0s`) |
| **Special Mechanics** | Fizzles into steam in water; `3×3` fire spread (`50%` per column) | Tracks moving target during `0.7s` charge; **3 chains** (**5 in rain**); virtual `LightningBolt` mob conversion | Mathematical parabolic arc (`6–18t`); exposure time tracker per UUID | Transfers projectile owner (`setOwner`) of deflected shots to caster | Spoofs empty armor/hand packets (`ItemStack.EMPTY`); clears mob aggro |
| **Server `ParticleFx`** | Counter-rotating hand pentagram, double DNA helix in flight, `92`-point Fibonacci sphere on impact | Dual zigzag chain arcs, `40` block debris chunks from struck ground | Parabolic drip trail, glass/slime shatter burst, gas belch every `14t` | Foot cloud ring on mid-air cast, cone clouds, `CRIT` burst on deflection | Caster-only `REVERSE_PORTAL` footsteps & enemy beacons; `40`-point Fibonacci sphere on ambush |
| **Client 3D VFX (`ConstructPainter`)** | `14`-point taper trail, `6` waving flame ribbons, boiling 3D fire core, `10` 3D fire tongues + mushroom smoke cloud | `3`-ring self-drawing rune + `8` spokes, `16`-part thundercloud at `18m`, stepped leader + `3` return strokes + `5` branches + `9` ground cracks | True **3D Lathe-mesh glass flask + cylinder cork** with liquid core, `5`-pointed swamp pentagram, `5` gas spirals, **`14` popping 3D bubbles** | `3` foot spirals, `4` cambered 3D wind crescents, `6` corkscrew air curls, `12` speed needles | Black-hole sphere (`ABYSS` core + lens ring), `18` implosion rays, `3` diagonal claw rifts + **GLSL Sobel shader (`void_world.fsh`)** with crimson wallhack outlines |
<!-- #endregion -->

---

<!-- #region 5. REFERENCE ARCHITECTURE: PROCEDURAL IK POSE, WIND-UP SYNC & TIME-BUBBLE LENS SHADER -->
## 5. Reference Architecture (Examples): Procedural Body/Hand IK, Charge Sync & GLSL Time-Bubble Lens

> **Note:** All systems in Sections 3.2–3.5 and Section 5 serve as **Reference / Example Architecture** for upcoming spells and shaders. Currently, **Fireball** (`10.0 blocks` weighted arc range, `5.0s` / `100 ticks` cooldown) is the first active built spell in **Magic Madness**.

### 5.1 Charge Wind-Up Synchronization (` CHARGE_SHOWN = 0.2F `)
1. **Separating Quick Casts from Hold-Charged Spells:**
   - For hold-to-charge spells (`HOLD_TICKS = 15` = `0.75s`), the client waits until the key is held for at least **`CHARGE_SHOWN = 0.2F` (`3 ticks` = `0.15s`)** and the spell is off cooldown before starting the charge stance and broadcasting the charge state to the server (with a `40-tick` safety timeout).
   - While charging, a rising electrical/arcane hum plays every `5 ticks` at chest height (`COPPER_BULB_TURN_ON`, volume `0.25 + 0.35 * charge`, pitch `1.5 + 0.5 * charge`).

### 5.2 Procedural 1st-Person & 3rd-Person Body/Hand IK (`Shape(up, open, drive, shock)`)
- **4 Mathematical Animation Parameters:**
  - During wind-up (`12.0 ticks`): `up = Ease.smooth(charge)` (`0 -> 1`), `open = 1.0` (arms spread wide), `drive = 0.0`, `shock = 0.0`.
  - On release (`MEET = 4.0 ticks`):
    $$\text{shut} = \min\left(1.0,\ \frac{\text{age}}{4.0}\right), \qquad \text{open} = 1.0 - \text{shut}^2$$
    The quadratic curve ($\text{shut}^2$) accelerates the hands to maximum speed at the exact moment of impact, followed by `12.0 ticks` of forward torso `drive` and `6.0 ticks` of `Ease.bump` recoil `shock`.
- **1st-Person Dual Arm Rendering (`RenderHandEvent`):**
  - Renders **both player arms simultaneously** in first-person, spreading to the screen edges (`SPREAD = (±1.02, 0.08, -0.38)`) during charge and snapping together in front of the camera (`(±0.05, -0.1, -0.75)`) on release.

### 5.3 5-Layer Shockwave VFX & GLSL Time-Dilation Lens (`Lens.java` & `lens.fsh`)
1. **Layer 1 — Hand Flash (`0..8t`):** `3.2m` `flare`, `5.0m` ice-blue `glowDisc`, and `1.4m` white core between the palms.
2. **Layer 2 — Light-Bending GLSL Time Bubble (`0..12t`, `4.0m` radius):**
   - Swells with a cubic ease-out (`1.0 - (1.0 - u)^3`) from `0.5m` to `4.0m` radius ahead of the caster.
   - Copies the rendered world Color and Depth buffers via `GL30._glBlitFrameBuffer`, solves per-pixel ray-sphere intersection in `lens.fsh`, discards occluded pixels (`if (scene < t) discard;`), bends the background with concentric pressure waves (`sin(off * 20.0 - Clock * 0.6)`), and samples 12 times in a Fibonacci spiral with **chromatic aberration** and **Fresnel rim glow (`pow(off, 5.0)`)**.
3. **Layer 3 — Time-Dilated Spark Streaks (`190 streaks`):**
   - Uses piecewise time dilation:
     $$\text{slowed}(\text{age}) = \begin{cases} 0.35 \times \text{age} & \text{if } \text{age} < 12 \text{ (inside the time bubble)} \\ 12 \times 0.35 + (\text{age} - 12) & \text{if } \text{age} \ge 12 \text{ (after bubble bursts)} \end{cases}$$
   - All `190` spark streaks move at **35% speed** while inside the bubble, then snap to full speed at `age == 12`.
4. **Layer 4 — Forked Wavefront Arcs (`14 arcs`):** 5-segment jagged arcs with a `60%` midpoint fork chance crackling along the advancing cone.
5. **Layer 5 — Rolling Dust Wall (`30 puffs`):** `30` `lightDisc` fog puffs rolling out to `9.0m` and fading quadratically (`left * left`).
<!-- #endregion -->

