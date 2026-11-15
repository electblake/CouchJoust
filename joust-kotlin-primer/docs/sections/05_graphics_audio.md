# Original graphics, animation and audio specification

This section specifies native game assets and visual/audio behavior. The companion files recover the original pixels, collision silhouettes, animation anchors, title geometry, sound event table and generated sound output from source commit `87e22aab945709fa107df609c0efcb839a17c1f9`. Use these assets directly in the Kotlin game. Their extraction tools run offline and are not Android runtime requirements.

## 1. Delivered asset data

The `joust-assets` directory contains 101 indexed sprite/terrain/effect PNG files, 99 individual font glyph PNGs, the source-derived title logo, illustrative atlases, an arena reconstruction, 76 PCM WAV files, and their machine-readable reference data. The ordinary image manifest covers 80 moving-object frames, 12 terrain/transporter images and the full bottom cliff. `special-effects.json` adds three pterodactyl dissolve frames and five cliff-destruction frames.

`asset-manifest.json` is the principal import file. It includes native pixel dimensions, source lines, game palette, image hashes, exact palette-index grids, 43 collision profiles and 103 actor/effect descriptor records. Every `index_rows` entry is a hexadecimal string with one character `0`–`f` per pixel; rows run top to bottom and pixels left to right. Ordinary sprite index 0 is transparent. PNGs retain indexed pixels, so the coding agent does not need to infer palette indices from RGB colors. Every animation descriptor provides numeric pixel offsets, its source image, collision profile, logical frame and facing. Files `special-effects.json` and `title-line-data.json` supply the two special drawing formats in already decoded form. [Image descriptors and source data][asset-start]

Important companion paths:

| File or directory | Intended use |
|---|---|
| `sprites/` | Exact extracted source images, including terrain and effects |
| `fonts/` | Original small/large bitmap glyphs |
| `asset-manifest.json` | Palette, pixel grids, image dimensions, terrain placement, animation anchors, collision silhouettes, sound events |
| `special-effects.json` | Dissolve/debris pixel grids and intentional erase masks |
| `title-line-data.json` | Title logo geometry, indexed raster and palette blocks |
| `sound-events.json` | Complete 40-event priority/command/hold table |
| `audio/audio-manifest.json` | 39 command probes, 37 audible event captures, hashes, timing and completion metadata |
| `audio/commands/` | Original individual sound-command PCM |
| `audio/events/` | Complete original scheduled event PCM references |
| `audio/dac-traces/` | Original sample values and integer timing for every audio capture |
| `extract_assets.py`, `extract_effects_title.py` | Reproducible source-pixel/data extraction |
| `audio/tools/` | Reproducible offline sound rendering and verification |

The illustrative arena images are reconstructions from source terrain, not gameplay screenshots. They deliberately do not imply that lava, score text, sprites or a particular wave state have been simulated. The title illustration similarly contains the decoded logo, not the complete animated attract screen.

## 2. Coordinates, raster and palette

### Source pixels and screen placement

Keep all gameplay geometry and asset anchors in source pixel coordinates. Source images have integer pixel positions; the Android screen size must not change collision geometry, velocities, sprite offsets or the number of visible logical pixels. The illustrative full working canvas is 304 × 256 pixels. The official reference display crops source coordinates x=6…297 and y=7…246 inclusive, producing 292 × 240 displayed pixels. Consequently an asset at source `(x,y)` appears at `(x−6,y−7)` in the cropped image. Keep the crop separate from game coordinates; applying the crop to physics would move every platform and actor. [Reference display bounds][mame-display]

Use nearest-neighbor pixel sampling for the game artwork. A TV presentation can fit the original monitor composition into a centered 4:3 area; do not stretch the gameplay geometry to the TV's 16:9 aspect. Treat the source raster and its physical presentation as separate decisions. Menu layout can use the TV's full resolution, while the game remains in its original coordinate system.

### Default game palette

These are the 16 source palette entries from `COLOR1`. RGB values below use the official reference renderer's color conversion. The register byte is also retained, so color cycling can apply every original color exactly under that conversion. Actual CRT calibration is a separate display property. [Source palette][asset-palette] [Reference color conversion][mame-palette]

| Index | Source color byte | Reference RGB |
|---|---:|---|
| `0` | `00` | (0, 0, 0) |
| `1` | `ff` | (255, 255, 255) |
| `2` | `70` | (0, 217, 95) |
| `3` | `58` | (0, 118, 95) |
| `4` | `0f` | (255, 38, 0) |
| `5` | `3f` | (255, 255, 0) |
| `6` | `51` | (38, 81, 95) |
| `7` | `e8` | (0, 174, 255) |
| `8` | `14` | (137, 81, 0) |
| `9` | `90` | (0, 81, 160) |
| `a` | `5d` | (174, 118, 95) |
| `b` | `11` | (38, 81, 0) |
| `c` | `1f` | (255, 118, 0) |
| `d` | `a4` | (137, 137, 160) |
| `e` | `0a` | (81, 38, 0) |
| `f` | `67` | (255, 137, 95) |

For native color conversion, let `r = byte & 7`, `g = (byte >> 3) & 7`, and `b = (byte >> 6) & 3`. R/G use normalized weights proportional to `(1/1200,1/560,1/330)`; B uses `(1/560,1/330)`, normalized separately. Multiply the weighted sum of enabled bits by 255 and round. Blue does not share R/G's normalization denominator.

Do not flatten every runtime asset permanently into the initial RGB palette. Attract/high-score effects change palette entries while the source pixels remain unchanged. Fonts and materialization effects also use recoloring. Retaining the index grids supports these effects without new artwork. The high-score screen loads its own initial palette and increments its cycling color; the marquee uses separate palette blocks. [High-score palette animation][asset-high-colors] [Marquee palettes and cycling][asset-marquee]

## 3. Characters and frame assembly

### Component inventory

| Component | Original graphics recovered | Native use |
|---|---:|---|
| Ostrich | 14 frames | Player-one mount, separate right/left images |
| Stork | 14 frames | Player-two mount, separate right/left images |
| Buzzard | 14 frames | Enemy mount, separate right/left images |
| Mounted/dismounted riders | 13 frames | Two player riders, three enemy riders, three standing enemies |
| Eggs/hatching | 6 frames | Upright/rolling eggs and expanding hatch stages |
| Lava troll | 6 frames | Growing hand/grab animation |
| Lava flames | 4 frames | Flame animation |
| Player death poof | 3 frames | Three discrete rising explosion images |
| Pterodactyl | 6 frames | Three poses in each facing |
| Pterodactyl dissolve | 3 frames | Packed source effect, separately decoded |
| Cliff debris | 5 frames | Packed source effect, separately decoded |

The original pixels already distinguish player colors, mounts and enemy types. Player one uses rider palette index 5 (yellow); player two uses index 7 (blue/cyan); Bounder, Hunter and Shadow Lord use indices 4, D and 9. Some source prose calls colors by historical names; preserve the actual pixel/palette values. The riders are 14 × 7 pixels when mounted. Dismounted enemies are 10 × 12 pixels. [Rider graphics and descriptors][asset-riders]

### Anchor and draw order

An actor's movement position is not the image top-left. For each selected descriptor, draw at:

`drawX = actorX + x_offset_pixels`

`drawY = actorY + y_offset_pixels`

Draw the rider first, then the mount, using palette index 0 as transparent. Preserve the listed order; mount pixels can cover rider pixels where the components overlap. Right and left artwork is individually authored, with different widths and offsets, so do not replace it with a generic horizontal mirror. [Actor composition][asset-compose]

The mounted rider has two placements: ordinary right `(4,−19)`, ordinary left `(0,−19)`, skidding right `(4,−17)`, skidding left `(0,−17)`. All ordinary running/flying rider frames reuse the ordinary placement. The rider's two-pixel drop during skid also changes the visible lance height. [Rider descriptors][asset-riders]

### Mount frame matrix

The logical sequence is `SKID, STAND, RUN1, RUN2, RUN3, RUN4, FLY1, FLY2, FLY3`, with a right/left descriptor for each. The following table gives the exact native sprite name and `(dx,dy)` pixel offset. The JSON additionally preserves the collision-profile selection. [Ostrich/stork frame tables][asset-mounts] [Buzzard frame table][asset-buzzard]

| Mount | Logical frame | Right image and offset | Left image and offset |
|---|---|---|---|
| Ostrich | SKID | `ORUNSR` (0,-17) | `ORUNSL` (2,-17) |
| Ostrich | STAND | `ORUN4R` (0,-19) | `ORUN4L` (2,-19) |
| Ostrich | RUN1 | `ORUN1R` (0,-19) | `ORUN1L` (2,-19) |
| Ostrich | RUN2 | `ORUN2R` (0,-19) | `ORUN2L` (4,-19) |
| Ostrich | RUN3 | `ORUN3R` (0,-19) | `ORUN3L` (4,-19) |
| Ostrich | RUN4 | `ORUN4R` (0,-19) | `ORUN4L` (2,-19) |
| Ostrich | FLY1 | `OFLY1R` (0,-19) | `OFLY1L` (0,-19) |
| Ostrich | FLY2 | `OFLY1R` (0,-19) | `OFLY1L` (0,-19) |
| Ostrich | FLY3 | `OFLY3R` (0,-19) | `OFLY3L` (0,-19) |
| Stork | SKID | `SRUNSR` (0,-18) | `SRUNSL` (4,-18) |
| Stork | STAND | `SRUN4R` (0,-19) | `SRUN4L` (2,-19) |
| Stork | RUN1 | `SRUN1R` (0,-19) | `SRUN1L` (0,-19) |
| Stork | RUN2 | `SRUN2R` (0,-19) | `SRUN2L` (2,-19) |
| Stork | RUN3 | `SRUN3R` (0,-19) | `SRUN3L` (2,-19) |
| Stork | RUN4 | `SRUN4R` (0,-19) | `SRUN4L` (2,-19) |
| Stork | FLY1 | `SFLY1R` (0,-19) | `SFLY1L` (2,-19) |
| Stork | FLY2 | `SFLY1R` (0,-19) | `SFLY1L` (2,-19) |
| Stork | FLY3 | `SFLY3R` (0,-19) | `SFLY3L` (0,-19) |
| Buzzard | SKID | `BRUNSR` (0,-12) | `BRUNSL` (2,-12) |
| Buzzard | STAND | `BRUN4R` (0,-13) | `BRUN4L` (0,-13) |
| Buzzard | RUN1 | `BRUN1R` (0,-13) | `BRUN1L` (0,-13) |
| Buzzard | RUN2 | `BRUN2R` (0,-13) | `BRUN2L` (2,-13) |
| Buzzard | RUN3 | `BRUN3R` (0,-13) | `BRUN3L` (2,-13) |
| Buzzard | RUN4 | `BRUN4R` (0,-13) | `BRUN4L` (0,-13) |
| Buzzard | FLY1 | `BFLY1R` (0,-14) | `BFLY1L` (0,-14) |
| Buzzard | FLY2 | `BFLY1R` (0,-19) | `BFLY1L` (0,-19) |
| Buzzard | FLY3 | `BFLY3R` (0,-19) | `BFLY3L` (0,-19) |

`STAND` is the fourth running graphic. For ostrich/stork, `FLY2` reuses the `FLY1` image. Buzzard `FLY2` also reuses the first flying image but moves its top upward by five pixels; it is not visually identical to the first descriptor. These are source aliases and placement decisions, not missing assets.

Animation selection follows the movement state machine. Do not run all sprite sheets at one fixed frame rate. Ground animation timing depends on movement; flying/flap states select their own descriptors. Keep collider selection linked to the logical descriptor even when two descriptors point to the same pixel image. The source has separate collision silhouettes and does not use sprite alpha as the universal hit test. The 43 recovered profiles include source row endpoints and `null` rows for empty collision rows; the mechanics section defines how to apply them. [Collision data][asset-colliders] [Frame selection][asset-frame-select]

### Eggs, flames, troll, pterodactyl and death

| Effect/state | Exact recovered frame rule |
|---|---|
| Upright egg | `EGGUP`, 8 × 7, offset `(0,−6)` |
| Egg left/right | `EGGLF`/`EGGRT`, 8 × 6, offset `(0,−5)` |
| First hatch | `EGGB1`, 10 × 6, offset `(0,−5)` |
| Second hatch | `EGGB2`, 16 × 11, offset `(−2,−10)` |
| Third hatch | `EGGB3`, 20 × 12, offset `(−4,−11)` |
| Standing hatched rider | Appropriate `PLY3S`/`PLY4S`/`PLY5S`, 10 × 12, offset `(0,−11)` |
| Troll hand | `GRAB1`…`GRAB6`; exact offsets and dimensions in manifest |
| Fire | `FLAME1`…`FLAME4`; heights 15,16,16,12; offsets y=−14,−15,−15,−11 |
| Pterodactyl flight | Poses 1,2,3,2; hold each pose for eight game ticks |
| Player death | `FL1` at `(2,−24)` for 5 ticks; `FL2` at `(2,−30)` for 8 ticks; `FL3` at `(0,−35)` for 12 ticks |

These are discrete source states. The egg cracking images expand to the left of the original egg anchor; centering every frame by image width would shift the hatch incorrectly. The pterodactyl's middle left-facing frame is 28 pixels wide while its middle right-facing frame is 26 pixels wide. Its other pose sizes and offsets are in the manifest. [Eggs and troll][asset-egg-troll] [Flame/poof/pterodactyl frames][asset-effects] [Pterodactyl sequence][asset-ptero-seq]

The death routine clamps its x anchor to 4…288 so the poof remains visible. Its comment says “1 sec total,” but the executable sequence explicitly sleeps for 5, 8 and 12 ticks. Use those values, not the prose comment. The three images total 25 nominal hold ticks, approximately 416 ms, before surrounding scheduling overhead. [Death routine][asset-death]

When a pterodactyl dies, the death dance alternates its second/third poses at four-tick sleeps, changes facing during the dance, then shows three dissolve phases. Each dissolve phase is written, held for two ticks, written again to clear/redraw as coded, followed by six ticks before advancing. Preserve this discrete effect rather than adding generic particle explosions. The exact state-transition count belongs to the pterodactyl mechanics section. [Pterodactyl death animation][asset-ptero-death]

`special-effects.json` includes a `write_mask_rows` grid for every dissolve/debris frame. A zero pixel *inside that mask* clears the destination; it is not ordinary sprite transparency. Pixels outside the mask are untouched. This matters when overlaying the effect over the prior image. Each packed span writes an odd-length colored run followed by a cleared trailing pixel; that deliberate one-pixel termination is retained in the recovered data. [Special-effect plot semantics][asset-special-plot] [Dissolve source][asset-ashes]

## 4. Arena and platform artwork

The following positions are in source pixel space. Crop only when presenting the finished scene. The bottom platform's ordinary image contains only its top two rows; the full 186 × 33 image is recovered from its compressed source. Using only the small top image would omit nearly all bottom-platform artwork. [Terrain data][asset-terrain] [Bottom-cliff image][asset-bottom]

| Image | Source position (x,y) | Native size (w×h) |
|---|---|---|
| `_CLIF1L` | (2,69) | 34 × 7 |
| `_CLIF1R` | (252,69) | 48 × 7 |
| `_CLIF2` | (86,81) | 88 × 9 |
| `_CLIF3L` | (2,138) | 64 × 8 |
| `_CLIF3U` | (202,129) | 58 × 11 |
| `_CLIF3R` | (254,138) | 48 × 7 |
| `_CLIF4` | (106,163) | 64 × 8 |
| `_CLIF5` | (54,211) | 186 × 2 |
| `_TRANS1` | (106,81) | 28 × 3 |
| `_TRANS2` | (224,129) | 28 × 3 |
| `_TRANS3` | (16,138) | 28 × 3 |
| `_TRANS4` | (120,211) | 28 × 3 |
| `_COMCL5` full bottom cliff | (54,211) | 186 × 33 |

The four transporters share the same 28 × 3 source pad image but have different positions. Their appearance is recolored during spawn effects, so retain their pixel indices. Background visibility changes by wave; the wave system chooses which original cliff pieces remain, disappear, or reappear. Do not redraw the islands procedurally as generic rounded platforms.

Cliff destruction alternates a normal redraw and a redraw shifted one pixel right, with ten-tick holds; repeat that pair five times. Then clear the cliff, wait two ticks, and play five debris frames with eight-tick holds. The leftmost upper cliff uses debris position `(−30,69)` and the upper central cliff uses `(98,81)`; other cliffs use their original top-left position. Clip the negative/offscreen portion without recentering the debris. Keep the wave's collision/visibility transition timing tied to the destruction state machine. [Cliff destruction][asset-cliff-destroy]

For exact raster correspondence, the shifted redraw retains the original image rectangle width and shifts the flattened sequence of source pixels right by one: prepend one zero pixel, discard the final pixel, then split back into original-width rows. Consequently a row's last source pixel can become the first pixel of the following row. Draw nonzero shifted pixels over the existing image; zero pixels leave existing pixels unchanged. The same shift rule applies to ordinary object drawing at odd x positions, placing the shifted rectangle one pixel left of the requested image origin. Every ordinary sprite row ends in zero except one row of `FL1`, so the distinction is mostly visible in cliff shaking and that death frame. This is a small pixel-level rule a native renderer can reproduce directly; it does not require a different game architecture. [Reference one-pixel shift behavior][asset-shift]

Lava is a procedural animated screen effect rather than a single PNG. The terrain illustration does not include it. Fill color is palette index 4. The fill cursor starts at source row 255, moves upward one row after each 20-tick wait, and fills inward from the screen edges until the existing cliff pixels stop it. The target surface begins at row 234, later rises to 229 and then 224 according to wave progression. The animated surface uses the current completed fill row, so it does not jump instantly when the target changes. Preserve the partial-pixel boundary writes in the source: two neighboring pixels can have different fill/terrain colors. [Lava animation][asset-lava]

Once the fill reaches its target, the process waits one tick and consumes a source random value. Its low four bits select an x coordinate from `[56,46,32,26,12,8,232,240,254,266,274,280,42,288,262,10]` source pixels. The random carry selects an interior bubble or a surface burst. Let `(x,y)` refer to the left pixel of a two-pixel cell and `surfaceY` be the completed fill surface:

- Interior bubble: choose `y = surfaceY + ((depthCounter & 15) | 2)`, using the source byte arithmetic. First set the two-pixel cell at `(x,y)` to `[4,0]` and request `SNBUB`; wait 8 ticks. Then set `(x,y)` and `(x+2,y)` to `[0,4]`, and `(x,y−1)` and `(x,y+1)` to `[4,0]`; wait 8 ticks. Restore all four cells to `[4,4]`, advance `depthCounter` by 5 with byte wrap, and recheck the fill target.
- Surface burst: use `y=surfaceY`. Clear `(x,y+1)` to `[0,0]`; wait 8 ticks. Restore that cell, clear `(x,y)`, and fill `(x,y−1)` with `[4,4]`; wait 8 ticks. Restore `(x,y)`, fill `(x−2,y−1)` and `(x+2,y−1)`, and clear `(x,y−1)`; wait 8 ticks. Clear those two side cells and recheck the target. This branch does not make the interior bubble sound request.

These are pixel writes with discrete holds, not smooth circles, alpha particles or randomly sampled positions across the entire screen. The original source's two-pixel cell operations can be translated directly into Kotlin writes to the indexed canvas. [Lava/bubble algorithms][asset-lava]

## 5. Spawning and visual protection state

Player materialization is a visible gameplay state, not a modern alpha fade. The source begins with a thirty-tick creation countdown and a filled/recolored transporter. During the later portion it reveals horizontal bands of the actor, constrained by the pad's vertical position. It then enters a colored/blinking protected idle phase. The reveal and protected phase are tied to spawn state and control input; pressing a control ends temporary safety. [Creation reveal][asset-spawn] [Protected spawn state][asset-protect]

For the reveal, the countdown runs from 30 to 1. At values above 20, only the recolored pad is visible. At countdown `t≤20`, calculate `h = ((~t) >> 1) & 15` and `shiftY = (2−h) & 15`. Draw the first `h` rows of the standing mount and the first `min(h,7)` rows of its rider, each moved down by `shiftY` from its normal top. Clamp the mount's bottom to the actor anchor row inclusive. Replace each nontransparent source pixel with the character's constant creation color. This deliberately reveals/repositions source rows; it is not alpha blending. Erase the temporary 18-pixel-wide actor region between successive reveal steps. [Reveal arithmetic][asset-spawn]

During protected idle, the source's blink-period state begins at 32 but halves to 16 on the first no-input update. It halves again every 75 ticks: 8,4,2,1,0. Reaching zero ends protection after 375 nominal protected-idle ticks, following the 30-step reveal. Any nonzero control input ends the state earlier. Maintain this separately from the 450-tick sound-event hold group.

The color-index sequence for player one is `[5,5,1,5,5,D,5,5]`, for player two `[7,7,1,7,7,D,7,7]`, and for enemies `[1,1,D,1,1,D,1,1]`. The rider, mount and pad select consecutive decreasing positions in that sequence. Start the phase at 2; reduce each index modulo 8, treating zero as phase 2 and resetting the global phase accordingly. Increment the phase when the current blink-period counter expires. During the final period-one phase, the source begins allowing normal source colors on a component when its selected color equals the player's own color, while white/gray recoloring continues. Preserve these discrete component changes. The decoded original table values are also straightforward to read at `TREPL1`…`TREPL3`. [Protection effect][asset-protect] [Color sequences][asset-transporter-colors]

The documented `SNPCR1` and `SNPCR2` sound sequences are part of the same effect, with distinct player timing. `SNPTREF` terminates the sound when the player aborts the protected phase. Avoid an extra fade that changes when control or safety begins.

## 6. Original text and title screen

Use the original bitmap fonts for in-game scores, lives, wave messages, instructions and attract text. `MESSAGE.ASM` supplies two variable-width families commonly described as 5 × 7 and 3 × 5. “5 × 7” is not a fixed five-pixel advance: encoded cells are generally six pixels wide and range from two to eight pixels in the recovered set. The small family generally advances four pixels. Each glyph's exact dimensions are in the manifest; advance by its encoded width, including its source spacing. The original small-font table aliases the letter O to its zero glyph and S to its five glyph. [Font tables and glyphs][asset-fonts] [Text plotting/advance][asset-text]

`font_tables.FONT57` and `.FONT35` preserve the source character-index order, with 53 and 49 table entries respectively. There are 99 unique extracted glyph images. Select glyphs through these tables; sorting PNG filenames does not reconstruct the correct character map.

Use the actual source message sequences from `PHRASE.ASM`. Phrases include `PREPARE TO JOUST`, `BUZZARD BAIT!`, `EGG WAVE`, `SURVIVAL WAVE`, `TEAM WAVE`, `GLADIATOR WAVE`, temporary-safety instructions, player-conflict/cooperation outcomes and the pterodactyl warning. The character/control tokens, spacing and text positions are part of the presentation. Avoid substituting an Android system font or reflowing these messages as modern UI. [Original phrases][asset-phrases]

The title is constructed from 68 authored line/fill segments, not a stored sprite and not the score font. `title-line-data.json` supplies all geometry, original palette blocks and the source-derived 304 × 256 indexed result. The provided `title-logo.png` and `title-logo-4x.png` are useful previews. The extractor follows the original integer connected-line stepping and downward fill, including excluded segment endpoints. Palette index 1 is initially black but still acts as a nonzero fill boundary. Do not replace the logo with a typeface or smooth vector approximation. [Title geometry][asset-title] [Title line/fill rules][asset-title-draw]

The complete marquee also adds its animated striped border, rotating palette, operator/copyright messages, credits and extra-life text. Those are timed procedures in `ATT.ASM`; the static logo is not a substitute for the full attract sequence. The source then transitions to its instructional/gameplay demonstration. [Marquee flow][asset-marquee-flow]

## 7. Sound assets and exact game event rules

### Recovered audio, not placeholder sounds

The archive includes the original generated sounds as 48 kHz mono 16-bit PCM WAVs. They were produced by building the supplied sound source and running it through an offline timing-aware extractor. The rebuilt 4096-byte input matches official reference CRC32 `f1835bdd` and SHA1 `af7c066d2949d36b87ea8c425ca7d12f82b5c653`. No external ROM download or approximate instrument replacement was used. The Kotlin game can play the resulting assets; the extraction machinery is not part of the app. [Sound source/build instructions][asset-sound-source] [Official checksum][mame-sound-hash]

There are 38 audible individual command assets plus the reset/control probe, and 37 audible complete event sequences. All audible routines reached natural termination in the extraction run. Three control-only gameplay events are represented only by event metadata. All 76 captures have raw sample/timestamp traces, file hashes, duration, command sequence, first/last write information and completion metadata. The archive preserves unused source sounds as well as those referenced during normal play.

The provided PCM preserves the source waveform and instruction-loop timing at reference output gain. A documented residual remains: individual sample writes are placed at instruction completion rather than modeled at their exact internal bus phase. The files do not model a physical cabinet's analogue filtering or speaker response. Captures begin from deterministic reset state; noise/random-generator history and the held sample preceding a new command may differ after an arbitrary live sequence. State is preserved within each multi-command event capture. These are source-derived reference sounds, not a claim of independently verified bit-identical recordings of a cabinet. `audio/README.md` and raw traces make the limits explicit.

### Single-voice arbitration

The original soundscape has a single voice with priority-driven interruption. Reproducing the same effects with unrestricted polyphony changes the game substantially: wing flaps and footsteps must be suppressed or interrupted by the same priority rules as the original. [Sound request arbitration][asset-sound-priority]

For a request `(priority, sequence)`:

1. If priority is below 128 and gameplay is not active, discard it.
2. If the current hold timer is nonzero and the incoming priority is lower than the active priority, discard it.
3. Otherwise accept it, replace the pending sequence, set its priority, and set the timer to one so the first command is dispatched on the next sound update.

At each nominal 16.64 ms game tick, decrement a nonzero hold timer. When it reaches zero and a sequence remains, dispatch the next command and load that pair's hold count. If that pair is the sequence's last pair, clear the pending sequence pointer while retaining its hold timer. **Ending a hold does not automatically stop the waveform.** It only permits a later lower-priority request or advances to the next command. Equal priority is allowed to replace an active sound. Priorities 128…255 bypass the game-over gate, but still obey active priority arbitration. [Sound dispatcher][asset-sound-dispatch]

Two semantic control commands must remain distinct:

- `00`: no new trigger; leave the current waveform running while extending scheduling time.
- `3f`: interrupt the current generator and hold its last sample, ending the changing sound.

The event table's inverted/flagged source representation has already been converted to semantic command numbers in JSON. Kotlin should consume the decoded table, not repeat the historical encoding.

### Complete gameplay sound-event table

Commands below are hexadecimal; each `/n` is a hold of n game ticks, **not the sound's duration**. Priorities are decimal. The full table is also in `sound-events.json`; `SNHIGH` is expanded there into all 33 command/hold pairs. [Event source table][asset-sound-events] [Bubble event][asset-bubble]

| Event | Priority | Command/hold sequence | Source purpose |
|---|---:|---|---|
| `SNCRED` | 200 | 0a/40 | CREDIT CHANGE SOUND |
| `SNGS` | 200 | 1b/1 | GAME START |
| `SNHI2` | 190 | 1c/6 | HIGH SCORE TO DATE END SOUND |
| `SNHIGH` | 190 | 33 original pairs in JSON; complete sequence WAV supplied | HIGH SCORE TO DATE |
| `SNREPL` | 100 | 0b/90 | EXTRA MAN |
| `SNCLIF` | 67 | 19/90 | CLIFF DESTROYER |
| `SNPTED` | 66 | 16/15 → 16/15 → 16/7 → 16/7 → 16/90 | PTERODACTYL DYING SOUND |
| `SNPTEI` | 65 | 24/30 → 25/30 | PTERODACTYL INTRODUCTION SCREAM |
| `SNPTE` | 65 | 24/120 | PTERODACTYL SCREAM |
| `SNBOUN` | 50 | 1c/60 | COLLECT BOUNTY |
| `SNTROL` | 50 | 09/30 | CAPTURED BY LAVA TROLL SOUND |
| `SNEGG` | 45 | 03/30 | PLAYER HITS EGG SOUND |
| `SNEGGH` | 45 | 02/30 | EGG HATCHING SOUND |
| `SNMOUN` | 45 | 0c/30 | ENEMY MOUNTING THE BIRD |
| `SNCTHD` | 10 | 06/10 | CLIFF THUD |
| `SNECRE` | 40 | 07/90 → 3f/1 | ENEMY RE-CREATED (TRANSPORTER) |
| `SNEDIE` | 40 | 16/20 | ENEMY DIES |
| `SNELAV` | 40 | 0d/30 | ENEMY IN LAVA |
| `SNETHD` | 9 | 08/30 → 3f/1 | ENEMIES THUD |
| `SNELWU` | 6 | 21/60 | ENEMIES WING UP SOUND |
| `SNELWD` | 6 | 20/60 | ENEMIES WING DOWN SOUND |
| `SNERU1` | 6 | 22/60 | ENEMY RUNS |
| `SNERU2` | 6 | 23/60 | ENEMY RUNS |
| `SNEMSK` | 6 | 26/60 | ENEMIES SKIDDING SOUND |
| `SNEMS2` | 6 | 27/30 | END OF ENEMIES SKIDDING SOUND |
| `SNEFAL` | 6 | 3f/1 | ENEMY STARTING TO FALL (SKID STOP) |
| `SNPDIE` | 80 | 16/20 | PLAYER DIES |
| `SNPCR1` | 70 | 12/30 → 14/255 → 00/165 | PLAYER 1 RE-CREATED (TRANSPORTER) |
| `SNPCR2` | 70 | 12/43 → 15/255 → 00/152 | PLAYER 2 RE-CREATED (TRANSPORTER) |
| `SNPTREF` | 70 | 3f/1 | PLAYER ABORTED FADING IN  (TRANSPORTER) |
| `SNPLAV` | 60 | 0d/30 | PLAYER IN LAVA |
| `SNPTHD` | 20 | 08/30 → 3f/1 | AT LEAST 1 PERSON THUD'ED |
| `SNPLWU` | 10 | 21/90 | PLAYERS WING UP SOUND |
| `SNPLWD` | 10 | 20/90 | PLAYERS WING DOWN SOUND |
| `SNPRU1` | 10 | 22/70 | PLAYER RUNS |
| `SNPRU2` | 10 | 23/70 | PLAYER RUNS |
| `SNPLSK` | 10 | 26/70 | PLAYERS SKIDDING SOUND |
| `SNPLS2` | 10 | 27/40 | END OF PLAYERS SKIDDING SOUND |
| `SNPFAL` | 10 | 3f/60 | PLAYER STARTING TO FALL (SKID STOP) |
| `SNBUB` | 1 | 0d/1 | THE SOUND OF A BUBBLE |

Running sounds alternate `22` and `23` on the source footstep transitions, rather than triggering every motion update. Wing-up and wing-down are separate effects. Skid start and skid ending are separate Walsh-generated sounds. Pterodactyl appearance uses a start scream followed thirty ticks later by its ending variant; the ordinary call requests the start sound alone. [Footstep timing][asset-footsteps] [Wing/noise/Walsh sound selection][asset-sound-walsh]

The introduction and fade of the two players intentionally differ: player one switches from command `12` to `14` after 30 ticks; player two switches from `12` to `15` after 43 ticks. Both complete a 450-tick scheduled group, with a final `00` hold that leaves the generator undisturbed. The pterodactyl dying sound retriggers command `16` at 15,15,7,7 tick intervals before its final 90-tick hold. Preserve these sequences rather than using a single arbitrary “spawn” or “enemy death” sample.

### Individual command references

This table reports measured complete extraction durations, including the small trailing interval until return-to-idle was observed. Event captures can be longer because they include source hold intervals. Command `1f` is a reset probe with no varying signal. Command `04` is a long original sound and is included even though the normal gameplay table does not use it.

| Command | Complete capture (seconds) | Used by game events |
|---|---:|---|
| `01` | 0.168521 | Included source sound; no normal event reference |
| `02` | 1.085771 | SNEGGH |
| `03` | 0.228000 | SNEGG |
| `04` | 17.119563 | Included source sound; no normal event reference |
| `05` | 0.830667 | Included source sound; no normal event reference |
| `06` | 0.151354 | SNCTHD |
| `07` | 2.722104 | SNECRE |
| `08` | 3.041792 | SNETHD, SNPTHD |
| `09` | 0.370063 | SNTROL |
| `0a` | 0.561396 | SNCRED |
| `0b` | 1.256917 | SNREPL |
| `0c` | 0.252063 | SNMOUN |
| `0d` | 0.607187 | SNELAV, SNPLAV, SNBUB |
| `0e` | 0.316500 | Included source sound; no normal event reference |
| `0f` | 1.078208 | SNHIGH |
| `10` | 0.939521 | SNHIGH |
| `11` | 0.891521 | SNHIGH |
| `12` | 2.083271 | SNPCR1, SNPCR2 |
| `13` | 0.214396 | Included source sound; no normal event reference |
| `14` | 6.313813 | SNPCR1 |
| `15` | 6.040792 | SNPCR2 |
| `16` | 0.697833 | SNPTED, SNEDIE, SNPDIE |
| `17` | 0.224521 | Included source sound; no normal event reference |
| `18` | 0.533062 | SNHIGH |
| `19` | 5.294271 | SNCLIF |
| `1a` | 0.360000 | SNHIGH |
| `1b` | 1.838667 | SNGS |
| `1c` | 3.125896 | SNHI2, SNBOUN |
| `1d` | 0.111167 | Included source sound; no normal event reference |
| `1e` | 2.145500 | Included source sound; no normal event reference |
| `1f` | 0.001146 | Reset/control probe |
| `20` | 0.271667 | SNELWD, SNPLWD |
| `21` | 0.130688 | SNELWU, SNPLWU |
| `22` | 0.042458 | SNERU1, SNPRU1 |
| `23` | 0.033271 | SNERU2, SNPRU2 |
| `24` | 0.749708 | SNPTEI, SNPTE |
| `25` | 0.406938 | SNPTEI |
| `26` | 0.922771 | SNEMSK, SNPLSK |
| `27` | 0.448250 | SNEMS2, SNPLS2 |

`SNGS` illustrates why event holds and audio duration must stay separate: it holds priority for one game tick, while the generated start waveform lasts approximately 1.84 seconds. Likewise a 30-tick `SNEGGH` hold does not shorten its approximately 1.09-second hatch sound unless an accepted request preempts it. A faithful native audio player should schedule original commands against a single output stream and allow interruption at the source event tick.

### Native playback recommendation

Use `sound-events.json` as the behavior specification, and command WAVs or the raw timestamped sample sequences as the sound content. Implement one active output stream. A new accepted sound replaces the previous generator/output; discarded requests do not queue for later playback. Preserve the old held output during the new command's startup interval when using raw traces. Do not add a zero prefix or a fade on every accepted command, because those additions create transitions the original did not make.

For the first playable native build, the supplied command/event WAVs give recovered original waveforms and rhythm. For the highest fidelity across arbitrary interruption histories, a small native sound sequencer can play source sample traces and reproduce the relevant noise-state evolution. The explicit remaining work is state-dependent variation and exact within-instruction sample-edge placement, not finding or inventing replacement sounds. No CPU or device emulation is required in the Kotlin game's design.

## 8. Reproduction and checks performed

Graphics extraction validates every ordinary image's declared dimensions against its literal byte count. All source grids are retained before RGB conversion. The full bottom cliff decompresses to 186 × 33 pixels from 871 source bytes, consuming 6,965 bits and 965 runs. Its source top rows and the ordinary top-cliff image align in the arena reconstruction. Title geometry, dissolve and debris follow their source plotting rules. The sprite, font, special-effect, terrain and title previews were visually inspected.

Sound extraction rebuilt the sound source without warnings/errors, verified its reference checksum, exercised all 39 command values plus the stop/control case, and verified that all audible routines ended. Targeted tests cover interrupt/preemption behavior, no-trigger command `00`, stopping command `3f`, restart behavior and the arithmetic/flag operations used by the original generators. The included helper is standalone source with its upstream BSD license; the archive does not require a full reference-emulator checkout or embed a compiler. It is only an asset-extraction tool. The audio manifest records the known timing/filter/state limits, rather than presenting partial verification as proof of physical-cabinet identity.

Regenerate graphics with Python and Pillow:

```sh
python3 extract_assets.py /path/to/joust-source /path/to/joust-assets
python3 extract_effects_title.py /path/to/joust-source /path/to/joust-assets
```

The audio folder's README provides the offline helper build and `render_audio.py` commands. It also provides `rebuild_sound_image.py` to reproduce the verified source-built input without altering the source checkout. Runtime imports require only the decoded PNG/JSON/WAV data selected for the Kotlin implementation.

[asset-start]: https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTI.ASM#L1-L47
[asset-palette]: https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/SYSTEM.ASM#L908-L923
[asset-high-colors]: https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L705-L779
[asset-marquee]: https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/ATT.ASM#L145-L195
[asset-riders]: https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTI.ASM#L2046-L2254
[asset-compose]: https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L6041-L6117
[asset-mounts]: https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTI.ASM#L759-L801
[asset-buzzard]: https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTI.ASM#L1668-L1687
[asset-colliders]: https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTI.ASM#L804-L988
[asset-frame-select]: https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L7176-L7262
[asset-egg-troll]: https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTI.ASM#L2258-L2480
[asset-effects]: https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTI.ASM#L2481-L2774
[asset-ptero-seq]: https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L1578-L1583
[asset-death]: https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L4522-L4557
[asset-ptero-death]: https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L1361-L1420
[asset-special-plot]: https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L4604-L4632
[asset-ashes]: https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTI.ASM#L2778-L2815
[asset-terrain]: https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTI.ASM#L54-L592
[asset-bottom]: https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTI.ASM#L594-L752
[asset-cliff-destroy]: https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L4558-L4601
[asset-lava]: https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/SYSTEM.ASM#L777-L904
[asset-spawn]: https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L5727-L5803
[asset-protect]: https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L5807-L5896
[asset-fonts]: https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/MESSAGE.ASM#L241-L1144
[asset-text]: https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/MESSAGE.ASM#L82-L97
[asset-phrases]: https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/PHRASE.ASM
[asset-title]: https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/ATT.ASM#L423-L533
[asset-title-draw]: https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/ATT.ASM#L296-L414
[asset-marquee-flow]: https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/ATT.ASM#L50-L143
[asset-sound-source]: https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/VSNDRM4.ASM
[asset-sound-priority]: https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/SYSTEM.ASM#L754-L773
[asset-sound-dispatch]: https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/SYSTEM.ASM#L174-L188
[asset-sound-events]: https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L8053-L8133
[asset-bubble]: https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/SYSTEM.ASM#L1027
[asset-footsteps]: https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L7193-L7219
[asset-sound-walsh]: https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/VSNDRM4.ASM#L2579-L2625
[mame-display]: https://github.com/mamedev/mame/blob/b7163a8d5ef30a8f0e72e4808ada8f904367311f/src/mame/williams/williams.cpp#L1531-L1559
[mame-palette]: https://github.com/mamedev/mame/blob/b7163a8d5ef30a8f0e72e4808ada8f904367311f/src/mame/williams/williams_v.cpp#L340-L362
[mame-sound-hash]: https://github.com/mamedev/mame/blob/b7163a8d5ef30a8f0e72e4808ada8f904367311f/src/mame/williams/williams.cpp#L3007-L3024

[asset-transporter-colors]: https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L5581-L5589

[asset-shift]: https://github.com/mamedev/mame/blob/b7163a8d5ef30a8f0e72e4808ada8f904367311f/src/mame/williams/williamsblitter.cpp#L200-L239
