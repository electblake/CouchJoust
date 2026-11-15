## Movement collision and player lifecycle

All behavior in this section is derived from commit `87e22aab945709fa107df609c0efcb839a17c1f9`. The target is a native Kotlin game. Source labels make each mechanic traceable.

### Gameplay state and units

Use the deterministic fixed gameplay tick specified in the timing section. Keep the order of collision resolution and actor updates stable, including newly spawned actors, because the first resolved contact can kill a participant and cancel its remaining contacts. Human movement updates once per ordinary gameplay tick; source comments conventionally call 60 ticks one second. Model grounded, flying with flap held, flying with flap released, protected spawn, riderless departure, and sinking as explicit gameplay states. [P01][P04]

| Gameplay value | Required meaning |
| --- | --- |
| Position | Integer source pixels plus vertical and horizontal fractions quantized to 1/256 pixel. Y increases downward. |
| Vertical velocity | Integer units of 1/256 pixel per update. Negative rises, positive falls. |
| Horizontal speed index | One of `-8,-6,-4,-2,0,2,4,6,8`, selecting the speed table below. |
| Facing | Left or right, independent of current horizontal momentum while flying. |
| Flap input | Held/released state, with a new press only on released-to-held transition. |
| Flap timer | Number of sampled updates in the current wing-input phase, capped at 255. |
| Ground transition timer | Eight updates between changing ground states. |
| Ground stride timer | Delay until the next animation-dependent horizontal stride. |
| Wing display timer | Minimum down-wing display time, separate from whether a flap impulse is allowed. |
| Horizontal bump remainder | Residual pixel displacement, consumed by at most three pixels per movement update. |
| Vertical bump | Whole-pixel displacement consumed on the next airborne movement update. |
| Lance offset | Zero normally, +2 while skidding; pterodactyl uses -128. |
| Collision enabled | False while protected, before materialization finishes, and after death. |

Preserve 1/256-pixel quantization and floor rounding with Kotlin integers. One subtle transition detail affects the phase of fractional horizontal movement: flight starts with the old ground stride-countdown value as its horizontal remainder. Track this deliberately in a native port instead of assuming the remainder always starts at zero. [P03][P04][P07]

### Original controls

Each player has three game inputs: left, right, and flap. Start is separate. The horizontal conversion is:

| Left | Right | Direction |
| --- | --- | --- |
| 0 | 0 | 0 |
| 1 | 0 | -1 |
| 0 | 1 | +1 |
| 1 | 1 | 0 |

Both horizontal directions together are neutral. Holding flap does not produce repeated flaps. For Android, keep the current held state from callbacks and sample it on gameplay updates. Ignore key-repeat for flap impulses. An analog stick supplies direction after a dead zone, not proportional movement speed. [P05]

### Initial placement and ground state machine

Player 1 starts at `(100,210)` facing right on the ostrich. Player 2 starts at `(200,210)` facing left on the stork. Each player initially waits 60 ticks before consuming its current life, enabling enemy targeting, and initializing movement. Human movement then updates once per tick. [P06]

`PLYINT` initializes ground state `PLYBR`, zeroes vertical velocity, horizontal index, animation and movement counters, and bump accumulators, and then enables collisions. Ground movement follows this 13-state table. Inputs in its last three columns are relative to the current facing, not absolute left/right. `PACCX` becomes 8 whenever a transition changes state; a further state transition is normally deferred until this counter expires. Remaining in the same state does not reset that counter. Each state supplies a frame delay, stride behavior, three input-dependent successors, and a flight-speed index. [P04][P08]

| State | Frame wait | Routine | Reverse input | Neutral input | Forward input | Flight index |
| --- | ---: | --- | --- | --- | --- | ---: |
| `PLYAR` | 8 | `REVR` | `PLYBR` | `PLYBR` | `PLYBR` | 0 |
| `PLYBR` | 0 | `STANDR` | `PLYAR` | `PLYBR` | `PLYCR` | 0 |
| `PLYCR` | 8 | `RUNR` | `PLYGR` | `PLYCR` | `PLYDR` | 2 |
| `PLYDR` | 4 | `RUNR` | `PLYCR` | `PLYDR` | `PLYER` | 4 |
| `PLYER` | 2 | `RUNR` | `PLYHR` | `PLYER` | `PLYFR` | 6 |
| `PLYFR` | 1 | `RUNR` | `PLYIR` | `PLYFR` | `PLYFR` | 8 |
| `PLYGR` | 2 | `RUNSR` | `PLYBR` | `PLYBR` | `PLYCR` | 2 |
| `PLYHR` | 2 | `SKIDR` | `PLYKR` | `PLYKR` | `PLYDR` | 4 |
| `PLYIR` | 2 | `SKIDR` | `PLYJR` | `PLYJR` | `PLYER` | 6 |
| `PLYJR` | 2 | `SKIDR` | `PLYKR` | `PLYKR` | `PLYDR` | 4 |
| `PLYKR` | 2 | `SKIDR` | `PLYLR` | `PLYLR` | `PLYDR` | 4 |
| `PLYLR` | 4 | `SKIDR` | `PLYMR` | `PLYMR` | `PLYCR` | 2 |
| `PLYMR` | 4 | `SKIDR` | `PLYBR` | `PLYBR` | `PLYCR` | 2 |

Consequences are deliberate requirements: releasing the stick while running preserves a running state and momentum; reversing must pass through the source's deceleration/skid states; `REVR` reverses facing; there is no generic acceleration-times-delta-time calculation. [P08]

On each grounded update, the program first checks a new flap press, then evaluates the state transition if allowed, then decrements `PTIMX`. A countdown result <=0 runs the state's stride routine and reloads its wait. A positive countdown normally leaves position unchanged, but a nonzero `PBUMPX` still forces a horizontal bump move. A state change zeroes `PTIMX` and therefore immediately runs the new stride routine. It updates the nominal `PVELX` from the state, negating it when facing left. [P04]

`RUNR` is coupled to the animation and footfalls:

| Situation | New `PFRAME` | Sprite | Positive-direction pixel stride |
| --- | ---: | --- | ---: |
| Enter running from standing/skid | 4 | `RUN1` | 0 |
| Continue from 4 | 3 | `RUN2` | 1 |
| Continue from 3 | 2 | `RUN3` | 2 |
| Continue from 2 | 1 | `RUN4` | 3 |
| Continue from 1 | 4 | `RUN1` | 2 |

Thus a sustained four-stride cycle advances eight pixels. At waits 8, 4, 2, and 1 it averages 0.25, 0.5, 1, and 2 pixels per player update. These averages do not replace the nonuniform strides. When the internal frame becomes 1, `PFEET` alternates clip/clop sounds. `RUNSR` uses a frame-dependent final stride: for `PFRAME` values -1,0,1,2,3,4 the strides are 0,0,2,4,1,1; it then switches to the standing picture and frame zero. `SKIDR` sets frame -1 and moves two pixels each time it is called. Entering skid starts its sound; entering `PLYLR` starts the end-of-skid sound. [P08]

After a ground move, the standing-support test is `LNDXTB[x] & LNDYTB[y+1] & 0x7f`. If zero, the actor begins falling immediately. An additional `CLIF3U` test uses `BCKXTB[x+6] & BCKYTB[y]`; it forces right-facing and sets the horizontal bump remainder to `abs(oldRemainder)+3`. [P04]

### Flight transition and wing logic

A new ground flap press only starts flight when the previous-flap latch is zero. `STFLY` sets `PVELY=-128` (-0.5 pixel/update), decrements integer y by one, clears the vertical fraction and vertical bump, enters the airborne state with normal gravity, and starts wing-down setup. This first lift is not an ordinary `ADDFLP` impulse. Landing sets the flap latch to one, so releasing before the next takeoff is required. [P04]

Walking off a ledge invokes `STFALL`: y velocity becomes zero, the vertical fraction and bump are cleared, normal gravity is selected, wing display hold is set to ten, and the flap timer becomes zero. The game chooses held/released wing logic from the current flap switch. It does not add an initial lift simply because the switch remains held. [P04]

Airborne updates first sample input, face the current horizontal input if nonzero, increment the flap timer up to 255, and then select held/released behavior. Horizontal input changes facing even when it does not yet change horizontal speed. [P07]

| Current flight state | Current flap | Effect |
| --- | --- | --- |
| Held/wing-down | Held | Integrate with gravity addition 0. No new impulse. |
| Held/wing-down | Released | Wing-up sound, reset `PTIMUP`, integrate with gravity addition 4. |
| Released/wing-up | Released | Integrate with gravity addition 4. |
| Released/wing-up | Pressed | Apply `ADDFLP`, integrate with gravity addition 0, reset `PTIMUP`, start wing-down setup. |

The wing-down display hold is five updates. A quick release may continue to display a down-wing picture while gravity is already using released behavior. A press while the display hold is still positive may show a fake wing-up image for one step before continuing. This display constraint is not a five-frame cooldown on flap impulses. Literal `FLY2` data exist, but the player chiefly displays `FLY1` and `FLY3`; do not substitute a generic interpolated three-frame wing cycle. [P04]

### Flight math

Let `vy` be vertical velocity in units of 1/256 pixel per update; `t` the released-phase timer at a new press; `j` horizontal input in {-1,0,+1}; and `q` the horizontal speed index. A flap applies:

```text
impulseQ8 = floor(96 * t / 256) - 96
vy += impulseQ8
candidate = q + 2*j
if -8 <= candidate <= 8:
    q = candidate
```

The coefficient is exactly 96 and the divisor exactly 256. The timer stops at 255. These constants preserve the measured strength of fast versus slow flapping. [P07]

| Release timer t | Impulse Q8 | Pixel/update delta |
| ---: | ---: | ---: |
| 0 | -96 | -0.375 |
| 1 | -96 | -0.375 |
| 2 | -96 | -0.375 |
| 4 | -95 | -0.37109375 |
| 8 | -93 | -0.36328125 |
| 16 | -90 | -0.3515625 |
| 32 | -84 | -0.328125 |
| 64 | -72 | -0.28125 |
| 128 | -48 | -0.1875 |
| 255 | -1 | -0.00390625 |

`t=0` is included as an arithmetic test vector; normal sampled airborne press transitions have incremented the timer. A single released sample followed by a pressed sample has `t=1` because the release transition resets it after `AIROVR`. Do not compute impulse from milliseconds. [P04][P07]

The `FLYX` table is:

| q | Signed Q8 dx | Pixels per update |
| ---: | ---: | ---: |
| -8 | -512 | -2 |
| -6 | -256 | -1 |
| -4 | -128 | -0.5 |
| -2 | -64 | -0.25 |
| 0 | 0 | 0 |
| 2 | 64 | 0.25 |
| 4 | 128 | 0.5 |
| 6 | 256 | 1 |
| 8 | 512 | 2 |

Only an ordinary airborne press changes `q` through joystick acceleration. Facing can therefore point opposite the current motion. Enemy decisions use the same movement engine with different input-generating routines. [P07][P08]

Normal gravity and integration use these steps in order:

```text
gravityQ8 = 4 + (flapReleased ? 4 : 0)
vy += gravityQ8
candidateYQ8 = yQ8 + vy + 256*bumpY
bumpY = 0
if floor(candidateYQ8 / 256) <= 32:
    bumpY = 1
    if vy < 0: vy = -vy
    candidateYQ8 = 32*256
if floor(candidateYQ8 / 256) >= 230:
    enterLavaState()       // Retain previous y for the first sinking frame.
else:
    yQ8 = candidateYQ8
    horizontalTotal = horizontalRemainder + flightSpeedQ8[q]
    dx = floor(horizontalTotal / 256)
    horizontalRemainder = horizontalTotal - 256*dx
    applyBumpAndWrap(dx)
```

The ceiling condition includes equality. It clamps y to exactly 32, reflects only rising velocity, and schedules a +1-pixel vertical bump for the following movement update. Ordinary player flight has no explicit maximum-rise or maximum-fall velocity clamp. [P07][P09]

### Horizontal bump and wrapping

`WRAPX` consumes `PBUMPX` as a residual displacement, not velocity with friction. If residual >3, add +3 and subtract 3 from the residual; if residual <-3, add -3 and add 3 to the residual; otherwise add the remaining residual and clear it. Add the resulting displacement to x. [P05]

The inclusive x domain is `[-10,292]`. If the result is >292, subtract 303. If it is <-10, add 303. This is not width 304, 302, or the physical framebuffer width. Boundary examples: x=292 plus 1 becomes -10; x=-10 minus 1 becomes 292. The collision routine itself uses ordinary coordinate comparisons and does not introduce virtual cross-seam copies or shortest-wrapped distance. [P05][P11]

### Landing and exact terrain candidate maps

`CKGND` checks landing before solid collision. It calculates `landingMask=LNDXTB[x] & LNDYTB[y]`. A nonzero value selects a landing/lava-troll handler. It does not test the sign of `vy`, so the exact candidate maps and ordering determine when a rising or falling actor can land. It snaps the integer y while retaining its fractional remainder; `STLAN` zeroes vertical velocity and chooses a ground state from `FRCONV[abs(q)]`. A zero horizontal speed retains facing; a nonzero speed determines facing. [P10]

| Landing bit | Candidate x ranges in template | Candidate integer y | Snapped y | Ground feature |
| ---: | --- | --- | ---: | --- |
| 0 | -32…26; 244…319 | 69…72 | 68 | Top left and top right ledges |
| 1 | 77…165 | 81…84 | 80 | Upper central ledge `CLIF2` |
| 2 | 193…251 | 129…132 | 128 | Upper part of right-middle ledge `CLIF3U` |
| 3 | -32…56; 241…319 | 138…141 | 137 | Left/right middle ledges |
| 4 | 97…160 | 163…166 | 162 | Lower middle ledge `CLIF4` |
| 5 | 45…230 | 211…227 | 210 | Bottom island `CLIF5` after bridge burn |
| 7 | -32…38; 239…319 | 203…255 | No landing | Lava-troll trigger region |

These are ranges of actor reference coordinates, not visible platform bounds. At game start the code ORs `0x20` into **every** x-table entry, making the bottom bridge support continuous. Burning restores bit 5 from `LNDXS3` as it advances. Cliff destruction removes the relevant bits from runtime x maps. Bit 6 is unused in the landing data, despite the comment describing it as lava trolls; the actual data and selection use bit 7. [P10][P13][P14]

Landing speed conversion is `|q|=0→PLYBR`, `2→PLYCR`, `4→PLYDR`, `6→PLYER`, `8→PLYFR`. The source zeroes ground frame/acceleration counters to force immediate ground service and sets `PTIMUP=1`, requiring a release before takeoff. [P04]

If there is no landing candidate, broad-phase solid collision uses `BCKXTB[x] & BCKYTB[y]`. Exact source ranges:

| Bit | Solid feature | Candidate x | Candidate y | Collision profile origin x,y |
| ---: | --- | --- | --- | --- |
| 0 | `CLIF1L` | -32…35 | 75…94 | -32,69 |
| 1 | `CLIF1R` | 235…319 | 75…94 | 252,69 |
| 2 | `CLIF2` | 69…173 | 87…108 | 86,81 |
| 3 | `CLIF3U` | 185…259 | 135…158 | 202,129, using `CLIF3R` profile |
| 4 | `CLIF3L` | -32…65 | 144…164 | -32,138 |
| 5 | `CLIF3R` | 238…319 | 144…163 | 202,129 |
| 6 | `CLIF4` | 89…169 | 169…189 | 106,163 |
| 7 | `CLIF5` | 37…239 | 217…255 | 54,211 |

The full source arrays, bit ranges, ground states, and flight constants are machine-readable in the accompanying `physics_tables.json`. These arrays contain 352 x entries covering -32 through 319 and 256 y entries. Only the 256 gameplay rows belong in the y map. [P12][P13]

### Silhouette collision model

Collision uses an authored horizontal interval on each silhouette scanline. Preserve these source profiles separately from the sprite artwork. Some rows are empty; the profile ends after its last defined row. All three flying ostrich frames share one silhouette for each facing direction; the stork shares the corresponding ostrich profiles. Using every visible nontransparent pixel as the collision mask changes gameplay. [P15]

The actor's broad-phase box is:

```text
x range = [x, x + 16]
ground y range = [integerY - 19, integerY]
air y range = [integerY - 19, integerY - 6]
```

A detailed silhouette may contain endpoint x+17 even though the broad-phase right edge is x+16. Keep both the box and the row intervals exactly as authored. Skidding changes lance height by +2. [P04][P15]

After broad-phase overlap, align the two profiles by their top-row difference. Let first-profile interval be `[a,b]`, second be `[c,d]`, and horizontal displacement `dx=x2-x1`. Endpoint equality counts as contact. Preserve this scan algorithm, including its directional mode:

```text
mode = BOTH
for each aligned row from top to bottom:
    if either profile has ended: return NO_CONTACT
    if mode == BOTH:
        if either row is empty: continue
        if b < c + dx:
            mode = CHECK_RIGHT_AGAINST_LEFT
            continue
        if a > d + dx:
            mode = CHECK_LEFT_AGAINST_RIGHT
            continue
        return CONTACT_AT_THIS_ROW
    if either row is empty: return NO_CONTACT
    if mode == CHECK_RIGHT_AGAINST_LEFT and b >= c + dx:
        return CONTACT_AT_THIS_ROW
    if mode == CHECK_LEFT_AGAINST_RIGHT and a <= d + dx:
        return CONTACT_AT_THIS_ROW
return NO_CONTACT
```

Once the first horizontal gap is found, subsequent rows check only that boundary. Keep the first contact row for platform-response classification. This source-specific scan is cheap in Kotlin and removes the need to guess whether a generic polygon or bitmap collision routine is equivalent. [P11][P12]

For a platform hit, let `actorRow` and `cliffRow` be the first contacted profile rows. Let `nearBirdBottom` mean at least one of the next four actor rows is empty or beyond the silhouette, and `cliffEndsNext` mean the next cliff row is empty or beyond the profile. Let `M=(cliffLeft+cliffRight)-(birdLeft+birdRight)+2*dx` at the contact row. Use these platform classifiers:

| Platform | First test | Next test | Remaining side decision |
| --- | --- | --- | --- |
| `CLIF1L`, `CLIF3L` | If `cliffRow==0 && nearBirdBottom`, top bounce. | If `cliffEndsNext`, underside bounce. | M<0: rightward side bounce; otherwise underside bounce. |
| `CLIF1R`, `CLIF3U` | If `cliffRow==0 && nearBirdBottom`, top bounce. | If `cliffEndsNext`, underside bounce. | M>=0: leftward side bounce; otherwise underside bounce. |
| `CLIF3R` | If actor integerY<=147 and `nearBirdBottom`, top bounce. | If `cliffEndsNext`, underside bounce. | M>=0: leftward side bounce; otherwise underside bounce. |
| `CLIF2`, `CLIF4`, `CLIF5` | If `cliffRow==0 && nearBirdBottom`, top bounce. | If `cliffEndsNext`, underside bounce. | M>=0: leftward side bounce; otherwise rightward side bounce. |

Evaluate these tests in the listed order. When a nonzero candidate mask contains more than one bit, preserve these exact dispatch rules; do not simply iterate every set bit:

```text
selectLanding(mask):
    if mask < 8:
        if mask == 2: return landingBit1
        if mask > 2:  return landingBit2
        return landingBit0
    if mask == 8: return landingBit3
    if mask < 32: return landingBit4
    if (mask & 32) != 0: return landingBit5
    return trollTrigger

selectSolid(mask):
    if (mask & 128) != 0: return solidBit7
    if mask < 8:
        if mask == 2: return solidBit1
        if mask > 2:  return solidBit2
        return solidBit0
    if mask == 8: return solidBit3
    middle = mask & 112
    if middle > 32: return solidBit6
    if middle < 32: return solidBit4
    return solidBit5
```

Runtime platform removal changes the candidate maps, not the authored profile. A rectangle's minimum-penetration normal is not equivalent to these classifiers. [P10][P12]

### Joust outcome

Only collision-enabled actors enter contact resolution. Preserve the original stable actor ordering. If the current outer participant dies, skip its remaining pairs. The player tie path also ends that participant’s remaining pair checks for the current pass, despite neither rider dying. [P11]

Resolve pairs as follows:

| Pair | Outcome family |
| --- | --- |
| Player and egg | Collect egg; no height contest. |
| Player and player | Lance-height contest, including normal friendly fire. |
| Player and ridden buzzard | Lance-height contest. |
| Player and pterodactyl | First test narrow mouth kill; otherwise lance-height contest with pterodactyl's artificial high lance. |
| Opponent and opponent, including buzzard/pterodactyl | Bounce without killing one another. |

For an ordinary joust, compare `integerY + lanceOffset`. The smaller value wins. Fractional y is not part of this comparison. Equal values cause a thud and a bounce without death. Skid lowers the lance by two pixels. The pterodactyl uses a lance offset of -128, so it usually wins the ordinary height comparison. [P11][P16]

The pterodactyl-mouth exception occurs only after silhouette contact. For the player U and pterodactyl X:

```text
relative = player.integerY + player.lanceOffset - ptero.integerY
if ptero.pose == 2:            // Third of its three poses
    require abs(relative - 8) <= 3
else:
    require abs(relative - 10) <= 2
require player.facing != ptero.facing
if ptero.x - player.x < 0:
    require player faces left
else:
    require player faces right
```

Passing kills the pterodactyl and bounces the player away. Failure falls through to the ordinary lance comparison. The three-pixel tolerance makes a seven-row window; the two-pixel tolerance makes a five-row window. Do not implement “any frontal hit kills pterodactyl.” [P11]

On a joust kill, disable the victim’s collisions, remove its rider, start riderless departure, invoke its death effects, and award its specified value to the victor. The mount remains and departs under its movement logic; an immediate whole-sprite deletion loses original behavior. [P11]

### Collision response math and quirks

For ordinary bird separation, the selected top actor receives `PBUMPY=-2`; if it was falling (`vy>0`), its new velocity is `floor(-vy/2)`. The selected bottom actor receives `PBUMPY=+2`; if it was rising (`vy<0`), the same negation-and-halving converts it downward. Existing velocities already going outward are retained. [P11]

For horizontally separated actors, identify physical left and right from `COLDX`:

```text
if left.q > 0:  left.q = -left.q + 2
if right.q < 0: right.q = -right.q - 2
right.bumpX = arithmeticShiftRight(-left.q + 2, 1)
left.bumpX  = arithmeticShiftRight(-(right.q + 2), 1)
left.faces = LEFT
right.faces = RIGHT
```

Keep the source’s update order when deriving each actor’s displacement from the other. If x positions are equal, no horizontal response is applied. Opponent-versus-opponent ties select the first actor as the upper participant; an ordinary player joust tie selects the second actor as the upper participant. Keep this deterministic tie rule rather than choosing randomly. [P11]

The pterodactyl/bird no-kill response moves only the bird: compare the sum of the two rough y bounds to determine up/down, use the same velocity reflection helpers, then overwrite vertical bump with ±5. Horizontal direction is chosen from the sum of the two x bounds; the bird is faced outward and an inward `q` is reflected with the source's two-index loss. Its residual horizontal bump is literally `-2*pterodactyl.q`. Preserve that expression even where a generic separation impulse would look more natural. [P11]

For background contact:

| Source route | Exact change |
| --- | --- |
| `BCRIT` | If q<0, q becomes -q-2. Set bumpX=q+6, then limit upward vy to at least -64 and set bumpY=+2. |
| `BCLFT` | If q>0, q becomes -q+2. Set bumpX=q-6, then limit upward vy to at least -64 and set bumpY=+2. |
| `BCBOT` | If vy<0, set `vy=floor(-vy/2)`. Set bumpY=+2. |
| `BCTOP` | If vy>=0, set `vy=floor((-vy-1)/2)`. Set bumpY=-2. If q==0, select q=-2 or +2 from the contact midpoint test. |

All routes produce the cliff-thud sound. Top contact at vy=0 produces -1 in 1/256-pixel velocity units. Keep this one-unit bias when implementing the bounce. [P12]

### Lava and the troll

Normal flight reaches the sinking state when the candidate integer y is >=`FLOOR+7=230`. This check precedes storing the candidate y and precedes x movement, so the first sinking frame starts from the previously stored y. The program writes the actor clipped at `FLOOR+1=224`, waits three ticks, erases it clipped, and checks whether the rider has begun to disappear. Only then does the rider die, with no victor. It increments integer y once per sinking cycle, continues until y>=244, waits 30 ticks, and invokes the recreate callback. [P07]

The troll trigger is table-driven and only creates a troll if `TTROLL==0`, no troll is already allocated, and the actor is a live player or enemy. A continuing troll target must remain airborne and at y>=198, and its x must satisfy x<=42 or x>=242. On capture, use the troll gravity rule below. [P10][P17]

While captured:

```text
bumpX = 0; bumpY = 0; q = 0
vy += trollGravity + (released ? 4 : 0)
if vy < -384: releaseTroll()
else:
    yQ8 += vy
    if integerY >= 230:
        terminateTrollAndSink()
        return
    x = clamp(x, 8, 278)
    if victimNoLongerInTrollRegion: releaseTroll()

releaseTroll():
    awardEscapePointsIfAlive()
    releaseHandAndRestoreNormalGravity()
    yQ8 += vy
```

The break-free test is strictly less than -384 (-1.5 pixels/update). Flaps still apply their normal impulse before this gravity path. Successful release while alive awards 50 points and restores normal gravity. The release path always adds the current `vy` to y once. A speed-triggered escape therefore integrates y once in total; an escape triggered by leaving the valid region integrates **twice in the same update**, because the first integration has already occurred. Preserve that visible displacement. A captured actor that sinks has also already stored its new y, unlike ordinary flight's sinking transition. Capture initializes a 1,800-tick timer and copies dynamic `LAVGRA` into `CLVGRA`. After expiration, the separate hand process increases `CLVGRA` by one Q8 unit each update until it has reached `0x501`: the update at `0x500` still increases it once. [P07][P17][P18]

### Death lives and respawn

Each human has a remaining-reserve counter and an auxiliary counter that includes the active life. The reserve count is decremented when starting/respawning; the auxiliary count is decremented when dying. Keeping only one counter risks early/late game-over and incorrect UI icons. The original icon row shows at most five reserve icons; the life count caps at 255. [P19]

A player death resets that player's egg-chain counter, marks death for survival-wave scoring, schedules baiter removal with integer `5*60/8=37`, awards the dying player 50 points, creates the death effect, plays its sound, and removes the player from the two AI-target slots. If both auxiliary life counters are zero, game-over begins. Otherwise the surviving player's game continues; a player-specific game-over message runs for integer `3*60/8=22` groups of eight ticks. If a player is killed by an opponent during the early waves while `TTROLL` is still nonzero, `EMYTIM` becomes 2, slowing enemy movement; player timing remains 1. [P20]

After a normal joust death, the riderless mount follows `AUTOFF`, selecting target flight lines y=69,129,208. It heads in its facing direction, alternates brief flap inputs as needed, and departs when x<-7 or x>289. It erases itself, waits 60 ticks, sets x=150, and invokes the recreate callback. This means the time to respawn depends on the mount's departure, not a universal fixed death timer. [P21]

`CREP1`/`CREP2` consumes a reserve life. If no reserve is available, that player is finished. Otherwise the player takes a queue ticket. Players have priority over enemy transporter requests. On each tick the front player first prefers an entirely empty vertical region: bottom transporter, then right/left middle, then top. If that fails, all active objects are tested against transporter-specific safety intervals after a speed-dependent predicted-x adjustment. Non-hatching eggs with no requested bird are ignored. Transporters disabled or in use are excluded. [P22]

| Transporter | Spawn x | Spawn y |
| --- | ---: | ---: |
| Top `TR1ID` | 113 | 80 |
| Right middle `TR2ID` | 231 | 128 |
| Left middle `TR3ID` | 23 | 137 |
| Bottom `TR4ID` | 127 | 210 |

Speed prediction uses q-indexed offsets `[-60,-30,-15,-8,0,8,15,30,60]`. Its predictor wrap adjusts by `ERIGHT-ELEFT=302`, unlike movement's period 303. Do not normalize those two constants. The player safety interval uses ±90 pixels around each transporter plus the stated 14-pixel actor allowance; enemies use asymmetric 66/39-pixel allowances. After the source's `TRSMALL` countdown reaches zero, players use the smaller enemy safety intervals. If no completely empty region exists, a random starting transporter is selected and a fixed fallback sequence searches the available transporters. [P22]

The transporter materializes for 30 one-tick steps, revealing the figure with the original clipped-height and solid-color effects. The actor remains noncollidable. Then it waits for either a nonzero direction or pressed flap or inactivity timeout. `PFEET` starts at 32 and is immediately halved to 16; successive 75-tick phases halve it 8,4,2,1,0, giving 375 more ticks before automatic release. Exact blinking/color sequencing comes from `TREPL1`/`TREPL2`, `PLANTZ`, `PACCX`, and `PTIMX`. A held input can end the protected wait immediately, because the old edge-only checks are commented out. On exit, the transporter is freed, targeting is enabled, `PLYINT` restores a live collision state, and the already-sampled input is processed. [P22][P23]

### Two player behavior

Both players occupy the same arena simultaneously, with independent controls, scores, reserve/auxiliary lives, egg-chain counters, and death flags. They collide and can kill one another in normal play. Ordinary player kills award 2,000 points through the player decision table's `DVALUE=0x20` and `SCRHUN`. The dead player also gets the normal 50-point death award. The stork uses distinct artwork but shares the ordinary physics and ostrich collision silhouettes. [P06][P15][P20][P22]

A gladiator wave arms one bounty for the first player-on-player kill. That kill awards an additional 3,000 points and records which player won. This is additional to normal kill scoring. Later kills in the same wave do not retrigger it. The final-release `PATC11` clears these triggers at game start so an old game's gladiator state does not leak. Cooperative/survival scoring must use these per-player kill/death flags and the exact wave callbacks, not disable friendly fire. [P20][P24]

### Required regression oracles

These are behavioral acceptance cases, not claimed runtime-tested recordings. Compare a Kotlin implementation against the original game using recorded inputs, screenshots, and per-tick movement expectations:

1. All four horizontal switch combinations, including both pressed, sampled with held/released flap.
2. Ground acceleration through B→C→D→E→F; release while running; reverse from each speed; skid sound and frame offsets.
3. Frame-accurate nonuniform running strides and the carried horizontal subpixel phase when entering air.
4. Ground takeoff's -128 initial velocity versus ordinary flap impulses after 1,2,4,8,16,32,64,128,255 release samples.
5. Held flap never repeating; facing reversing without an immediate speed change; rapid press/release during the five-frame animation hold.
6. Exact ceiling equality y=32, fractional motion accumulation, and absence of invented vertical clamps.
7. x=-10/292 boundaries, ±3 residual bump consumption, and collision behavior around the wrap seam.
8. Every pixel offset around each landing x/y boundary, including rising contact and the bridge's runtime bit-5 mutation.
9. Exhaustive row-profile collision offsets for all source bird, egg, pterodactyl, and terrain profiles; retain the first contact row.
10. Same-height tie in both linked-list orders; skid lance +2; ordinary player friendly fire; pterodactyl mouth windows and facing tests.
11. Terrain top collision at vy=0, proving the one-unit upward result -1; side collisions at each q.
12. Sinking starts from the last stored y, delayed death when the rider clips, and exact 3-tick/30-tick waits.
13. Troll escape at vy=-384 (still held) versus -385 (released), x=42/43/241/242, the extra y integration on range-triggered escape, 1,800-tick escalation, final `CLVGRA=0x501`.
14. Riderless departure and respawn queue order with two players; materialization, held-input exit, and 375-tick inactivity timeout.
15. Reserve versus active-life accounting; death awarding 50 points; initial early-wave enemy slowdown; one-time gladiator bounty and next-game reset.

### Evidence index for this section

- [P01] [Primary and secondary scheduler, sleep counts and continuation PCs, SYSTEM.ASM 158–255](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/SYSTEM.ASM#L158-L255).
- [P02] [Process macros and block fields, RAMDEF.ASM 6–43 and 165–214](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/RAMDEF.ASM#L165-L214).
- [P03] [Movement fields and aliases, RAMDEF.ASM 184–214](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/RAMDEF.ASM#L184-L214).
- [P04] [Player ground and flight continuations, JOUSTRV4.ASM 5910–6257](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L5910-L6257).
- [P05] [Control sampling and bump/wrap arithmetic, JOUSTRV4.ASM 7249–7301](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L7249-L7301).
- [P06] [Initial player placement, JOUSTRV4.ASM 1019–1049](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L1019-L1049), [initial delay and PLYTIM](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L5601-L5601).
- [P07] [Impulse, air overhead, gravity and lava integration, JOUSTRV4.ASM 6429–6696](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L6429-L6696).
- [P08] [Flight speed and ground-state tables, stride routines, JOUSTRV4.ASM 7150–7245](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L7150-L7245).
- [P09] [Geometry and unused velocity definitions, JOUSTRV4.ASM 35–48](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L35-L48), [gravity initialization](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L952-L965).
- [P10] [Landing and troll creation, JOUSTRV4.ASM 6703–6793](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L6703-L6793).
- [P11] [Collision lists, silhouette checks, jousting and bounces, JOUSTRV4.ASM 4811–5248](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L4811-L5248).
- [P12] [Terrain dispatch, collision normal tests, bounces and BPCOL, JOUSTRV4.ASM 6799–7145](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L6799-L7145).
- [P13] [Literal background and landing masks, JOUSTRV4.ASM 7541–7881](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L7541-L7881).
- [P14] [Runtime landing-map initialization, JOUSTRV4.ASM 977–1005](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L977-L1005), [bridge burn](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L5253-L5288).
- [P15] [COFF and silhouette data, JOUSTI.ASM 5–13](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTI.ASM#L5-L13), [ostrich/stork tables and profiles 759–857](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTI.ASM#L759-L857).
- [P16] [Pterodactyl artificial lance, JOUSTRV4.ASM 1469–1477](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L1469-L1477).
- [P17] [Troll capture and valid victim region, JOUSTRV4.ASM 1646–1727](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L1646-L1727).
- [P18] [Final-release troll patches, JOUSTRV4.ASM 6374–6397](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L6374-L6397).
- [P19] [Life counter increment/decrement, JOUSTRV4.ASM 5340–5420](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L5340-L5420), [score structure RAMDEF.ASM 267–272](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/RAMDEF.ASM#L267-L272).
- [P20] [Player death and game-over handling, JOUSTRV4.ASM 4669–4806](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L4669-L4806).
- [P21] [Automatic riderless departure, JOUSTRV4.ASM 7996–8044](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L7996-L8044).
- [P22] [Safe spawn selection, decision tables and transporter positions, JOUSTRV4.ASM 5443–5732](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L5443-L5732).
- [P23] [Materialization and protected idle sequence, JOUSTRV4.ASM 5733–5906](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L5733-L5906).
- [P24] [Gladiator setup/cleanup, JOUSTRV4.ASM 2697–2733](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L2697-L2733), [game-start reset patch 6282–6284](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L6282-L6284).
