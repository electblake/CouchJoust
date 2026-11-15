## Enemy behavior waves scoring and hazards

This section specifies the final version 4 rules of the **1982** arcade game for a native Kotlin implementation. Preserve the revision 3 and 4 gameplay changes, especially pterodactyl targeting and lava-troll pull. Unless explicitly marked as derived, constants and transitions come from the cited source at commit `87e22aab945709fa107df609c0efcb839a17c1f9`. Durations are simulation visits, independent of Android display frames; primary and secondary update order and sleep visitation rules are specified in the scheduling audit. Parenthetical seconds use the game's nominal 60 ticks per second; tick counts are authoritative.

The associated `gameplay_tables.json` contains all 90 wave records, all 28 dynamic adjustment records, all 69 egg wave positions, the baiter timing table, and derived default difficulty snapshots. `waves.csv` provides the complete human-readable wave inventory. These decoded data can directly initialize the native game and serve as regression oracles.

### Essential behavioral invariants

- Enemy types use the same movement rules as the player. Their AI produces a horizontal command −1, 0 or +1 and a flap command 0 or 1. Movement then applies the ordinary acceleration and flap mechanics.
- Each bird has a current AI state, a state timer, and persistent targeting fields. A species is a collection of these states; one generic chase loop is insufficient.
- Bounders, hunters, and shadow lords initially follow horizontal patrol lines. A rider switches to its species-specific pursuing states when the wave grants a pursuing slot. A shadow lord can initially be patrolling.
- `NSMART` counts currently pursuing riders and `WSMART` sets the requested number. When a patrolling enemy sees fewer pursuers than the requested quota, it claims a slot, marks itself pursuing, and runs its pursuing decision immediately. Which enemy claims the slot depends on entity update order. A remount promotes the rider independently of this quota.
- `NRIDER` counts unresolved enemy riders, including eggs and standing riders. `NENEMY` counts active and reserved birds. A wave ends when `NRIDER == 0`, not when there are no flying mounted enemies.
- Enemy defeats promote the resulting egg's intelligence from bounder to hunter to shadow lord. An individual rider starts with an egg budget of 4. The fourth defeat automatically collects its last egg when a victor exists; a lava death has no victor and retains the ordinary egg path.
- Pterodactyls spawned by special waves and pterodactyls spawned by the elapsed-time baiter mechanism are different behaviors. Store an explicit pterodactyl category: special-wave or baiter. Do not apply all V4 patches to both.

Sources: [decision records](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L5542-L5577), [line tracking and promotion](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L3690-L3750), [enemy defeat](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L2952-L3008), [wave monitor](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L2094-L2130).

### Factory settings and initial conditions

| Setting | Exact source value | Behavior |
|---|---:|---|
| Lives per credit | `$05` packed BCD | 5 lives |
| Extra life interval | `$20` packed BCD | Every 20,000 points |
| Master difficulty | `$05` | Difficulty 5, middle starting band |
| Initial gravity | 4 | Shared normal gravity increment |
| Bridge countdown | 3 | Bridges start burning at entry to wave 3 |
| Troll countdown after bridges | 1 | Troll becomes eligible at entry to wave 4 |
| Initial enemy movement wait | 1 | Later set to 2 during wave 1 and 2 enemy creation |
| Player target delay | 90 ticks | Newly active players are not immediately valid AI targets |
| Initial lava display level | `$EA` = 234 | Per-wave target rises by 5 until `$E0` = 224 |
| Transporter safety narrowing countdown | 13 | Decremented on inter-wave entry; narrow safety table is used when it reaches 0 |
| Dynamic adjustment countdowns | 2 | The first scheduled adjustment occurs entering wave 3 |

`TB12REV3.ASM` factory settings, rather than a remembered arcade configuration, define the reference configuration. The live on-screen lives icon count is capped at five visible icons, although internal lives can exceed five. `INCLIV` saturates the auxiliary life total at 255. Distinguish reserve lives from the total lives including the active bird; their decrements happen at different lifecycle stages.

Sources: [factory values](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/TB12REV3.ASM#L132-L150), [game initialization](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L865-L976), [life increment](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L5340-L5383).

### Dynamic difficulty arithmetic

Each dynamic parameter specifies three starting values, one final value, an increment, and ten wave intervals. Starting values apply to difficulty bands 0–3, 4–6, and 7–9. The wave interval is selected by the exact difficulty 0–9. Keep a value and an independent countdown for each parameter.

Initialization selects the difficulty band and sets every countdown to 2. At each transition after wave 1, decrement a nonzero countdown. If the result is zero, reload the selected difficulty nibble and apply the signed increment with the original signed clamp:

```text
if increment >= 0:
    candidate = signed16(value + signed8(increment))
    value = candidate if candidate < signed16(endpoint) else endpoint
else:
    candidate = signed16(value + signed8(increment))
    value = candidate if candidate > signed16(endpoint) else endpoint
```

Do not reinterpret this as a distance-to-endpoint step. For example, `HUUPVY` and `SHUPVY` use increment `$20` with endpoints more negative than their initial values. The positive-increment clamp jumps them to the endpoint on the first adjustment. That is the V4 gameplay behavior. A zero period, if supplied by modified data, would cause an update at every following wave; all shipped nibbles here are nonzero.

In the table below, values are signed integers in each parameter's native units. Velocity and excursion values use 1/256-pixel units; ranges use pixels, and delays use the AI or timer calls described in their respective sections. Period columns list difficulties 0–9 in order. Source symbols are retained only to identify the corresponding constants.

| Parameter | Start 0–3 | Start 4–6 | Start 7–9 | Increment | Final value | Wave intervals at difficulties 0–9 |
|---|---:|---:|---:|---:|---:|---|
| `LAVTIM` | 11 | 6 | 4 | -1 | 1 | 15, 10, 8, 6, 4, 4, 3, 3, 2, 1 |
| `LAVGRA` | 4 | 6 | 8 | +2 | 45 | 15, 10, 8, 6, 4, 3, 3, 3, 2, 1 |
| `LNTLAV` | 16 | 8 | 4 | -1 | 1 | 15, 10, 8, 6, 4, 4, 3, 3, 2, 1 |
| `EGGWT` | 96 | 64 | 32 | -2 | 16 | 15, 15, 12, 10, 8, 6, 4, 3, 2, 1 |
| `EGGWT2` | 94 | 56 | 34 | -4 | 8 | 12, 10, 5, 3, 10, 5, 2, 5, 2, 1 |
| `BODNRG` | 14 | 14 | 12 | -1 | 11 | 15, 15, 15, 15, 15, 15, 15, 8, 4, 2 |
| `BODNDI` | -4096 | -3584 | -2048 | +32 | -256 | 4, 2, 1, 1, 4, 2, 1, 4, 2, 1 |
| `BODNVY` | 128 | 256 | 512 | +32 | 768 | 4, 2, 1, 1, 15, 8, 1, 3, 2, 1 |
| `BOUPRG` | -15 | -14 | -12 | +1 | -11 | 15, 15, 15, 15, 15, 15, 15, 8, 4, 2 |
| `BOUPDI` | 4096 | 3584 | 2048 | -32 | 256 | 4, 2, 1, 1, 4, 2, 1, 4, 2, 1 |
| `BOUPWD` | 3 | 2 | 1 | -1 | 1 | 15, 10, 8, 6, 4, 4, 3, 3, 2, 1 |
| `BOUPWU` | 10 | 8 | 6 | -1 | 2 | 15, 10, 8, 6, 4, 4, 3, 3, 2, 1 |
| `BOLETM` | 32 | 21 | 16 | -1 | 2 | 7, 7, 6, 6, 4, 4, 3, 3, 2, 1 |
| `HUDNRG` | 15 | 14 | 12 | -1 | 11 | 15, 15, 15, 15, 15, 15, 15, 10, 2, 2 |
| `HUDNDI` | -4096 | -3584 | -2048 | +32 | -256 | 15, 15, 15, 15, 15, 15, 8, 4, 2, 2 |
| `HUDNVY` | 256 | 512 | 768 | +32 | 896 | 15, 15, 15, 15, 15, 15, 8, 4, 8, 4 |
| `HUUPRG` | -15 | -14 | -12 | +1 | -11 | 15, 15, 15, 15, 15, 15, 15, 8, 2, 2 |
| `HUUPDI` | 4096 | 3584 | 2048 | -32 | 256 | 15, 15, 15, 8, 15, 15, 8, 4, 2, 2 |
| `HUUPWD` | 3 | 2 | 1 | -1 | 1 | 15, 15, 15, 8, 15, 15, 8, 3, 2, 2 |
| `HUUPWU` | 10 | 8 | 6 | -1 | 2 | 15, 15, 15, 8, 15, 15, 8, 3, 2, 2 |
| `HUUPVY` | -128 | -256 | -512 | +32 | -512 | 15, 15, 15, 15, 15, 15, 8, 4, 8, 4 |
| `HULETM` | 32 | 21 | 16 | -1 | 2 | 15, 15, 15, 8, 8, 4, 3, 3, 2, 2 |
| `SHDNRG` | 20 | 16 | 14 | -1 | 12 | 15, 15, 15, 15, 15, 15, 15, 10, 8, 2 |
| `SHUPRG` | -20 | -32 | -14 | +1 | -12 | 15, 15, 15, 15, 15, 15, 15, 10, 8, 2 |
| `SHUPVY` | -16 | -512 | -768 | +32 | -1024 | 15, 15, 15, 15, 15, 15, 15, 10, 8, 4 |
| `SHUPTM` | 20 | 10 | 8 | -1 | 2 | 15, 15, 15, 15, 15, 15, 15, 10, 8, 2 |
| `SHLETM` | 32 | 21 | 16 | -1 | 2 | 15, 15, 15, 15, 15, 15, 15, 10, 8, 2 |
| `SHCLTM` | 10 | 8 | 6 | -1 | 2 | 15, 15, 15, 15, 15, 15, 15, 10, 6, 1 |

Initial difficulty-5 semantics: troll animation 6 ticks/frame, pull increment 6, AI troll check interval 8 calls, normal egg wait 64 × 12 ticks, egg-wave wait 56 × 12 ticks (halved before assignment with two living players), bounder/hunter long ascent/descent excursion magnitude 14 pixels in 8.8 units, bounder level reconsideration 21 calls, hunter level reconsideration 21 calls, shadow lord short-range target reconsideration 10 calls. The `PJOY` state and its counter ordering determine the exact number of calls; do not turn these descriptive values into new real-time timers.

Sources: [record layout and macro](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L199-L214), [initialization](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L927-L950), [per-wave adjustment](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L1887-L1933), [all dynamic values](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L7305-L7334), [RAM layout](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/RAMDEF.ASM#L368-L427).

### Wave configuration and progression

Each wave specifies bounder, hunter and shadow-lord counts, a pursuing-enemy quota, whether pursuit begins during introduction, a special-wave mode, a special-wave pterodactyl count, and which cliffs are removed. The complete decoded configuration is in `gameplay_tables.json` and `waves.csv`.

For egg waves, the species counts instead select the hatch species and concurrent hatch limit. If hunters are listed, use hunters; otherwise use bounders if listed; otherwise use shadow lords. The chosen count is the maximum simultaneous active/reserved birds. Every egg wave initially places **12 eggs**, regardless of this limit.

The six modes are ordinary, first-wave introduction, team-or-survival, gladiator, egg, and pterodactyl. Wave-specific removed cliffs can be `CLIF1L`, `CLIF1R`, `CLIF2`, and `CLIF4`. `CLIF3U`, `CLIF3L`, `CLIF3R`, and the central part of `CLIF5` remain outside that removal configuration.

The main wave sequence is:

1. Enter `IWAVE`: decrement transporter narrowing countdown, update all dynamic difficulty records, and raise the lava target if permitted. Initial wave 1 starts at `IWAVE2` instead.
2. Decrement the bridge countdown or, after it is zero, the troll countdown. Wave 3 creates two bridge-burning processes; wave 4 enables trolls and creates the outer flame processes.
3. Reset both players' egg-chain counters, `WSMART`, `WENEMY=255`, and `EMYTIM=1`.
4. Increment the displayed wave number modulo 100. Run the previous special wave's completion callback and clear gladiator triggers.
5. Advance the wave table pointer. After entry 90, jump to entry 81. Display number and table entry are separate; after 90, the screen can show 91 while executing table entry 81.
6. Compare the previous and new cliff-status bits and start the corresponding cliff creation/destruction. This also changes collision and landing tables.
7. If early pursuit is enabled, set the pursuing quota immediately. Invoke the special-wave setup.
8. Standard introductions wait through `WAVDEL`, then `WATWAV` waits for previous wave processes to drain. It is not sufficient to observe `NRIDER==0` and spawn immediately.
9. Introduce all bounders, then all hunters, then all shadow lords. `WCREATE` sleeps **61 ticks before each spawn**, increments `NENEMY` and `NRIDER`, sets the species and buzzard image, sets egg budget 4, and starts nonpursuing. It sets `EMYTIM=2` if `TBRIDGE!=0`.
10. `WBEGIN` installs the wave's pursuing quota for a running game even when early-pursuit status was clear. Thus early pursuit controls pursuing behavior **during introduction**, not whether that wave ever pursues. Initialize baiter timers and begin the 8-tick wave monitor.
11. Every monitor iteration checks `NRIDER==0` first. Only with at least one `NENEMY` does it decrement the 112-iteration aggression counter. At expiry, increase `WSMART`, saturating at 255, then reset 112. This is 896 ticks, approximately 14.93 seconds of counted iterations, rather than exactly 15 seconds.

The complete wave table is in `waves.csv` and `gameplay_tables.json`. The final row is the 90th entry even though its source comment incorrectly repeats `WAVE 80`.

Sources: [wave schema](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L172-L197), [controller and spawning](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L1875-L2229), [all 90 entries](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L2436-L2546), [special dispatch](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L2586-L2617).

### Special-wave rules

| Wave type | Entry conditions and behavior | Award |
|---|---|---|
| Team | The type-4 routine finds both auxiliary life totals nonzero. It clears `PLYG1/2`, tracks player-on-player kills, and evaluates both flags at completion. Enemy-inflicted deaths alone do not cancel this team award. | 3,000 to each player who still has lives if neither player killed the other during the wave. |
| Survival | Type-4 routine finds either player out of lives. It clears `PLYD1/2`. At completion the remaining player must still have lives and must have had no deaths. | 3,000 to the surviving qualifying player. |
| Gladiator | Type-6 routine runs only if both players have lives at entry. It sets both `PLYG` flags to `$FF`. The first player-on-player kill wraps the winner's flag to zero, triggering the bounty; then both triggers are cleared and the winner is marked `$80`. | 3,000 bounty **in addition to** the normal 2,000 player kill score, for 5,000 on the first qualifying kill. Later player kills score the normal 2,000. |
| Egg | Type-8 routine creates 12 eggs at source-defined legal positions. It uses the wave record for hatch species, concurrent hatch cap, and pursuing quota. | Ordinary egg-chain scoring, including collection of standing riders. |
| Pterodactyl | Type-10 routine creates a secondary `PTERWV` controller after the introductory delay and prior-process drain. It sleeps 65 ticks before each of the entry's 1, 2, or 3 pterodactyls; the final controller itself becomes the last pterodactyl. Normal riders are also introduced. | 1,000 per killed pterodactyl. |

A player death resets that player's egg chain and increments their survival death counter before other death processing. Player-on-player kill counters follow the victor's `DGLAD` pointer; they are not indexed by the victim. At the next wave the previous wave's award callback runs before the old gladiator flags are unconditionally cleared.

Sources: [team and survival](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L2628-L2693), [gladiator setup and completion](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L2697-L2733), [bounty award inside death](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L4669-L4719), [normal kill award after death callback](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L5057-L5079).

### Line tracking and targeting

`LINET` selects one of three target bottom-coordinate lines: 69 (`$45`), 129 (`$81`), or 208 (`$D0`). The branch boundaries are 101 and 161 because the line selection adds `$20`. It always commands its facing direction. If below the selected line and not already rising, it produces a one-call flap and installs `LNTOFP` to release flap on the next call. At `LNTLAV`-spaced checks it also notices a troll immediately preceding it in the linked process list and starts an upward flap.

A player becomes a target at `STPLY1/STPLY2`, with a 90-tick target delay. `PLYCOL` decrements the target delays once per tick. On death, the first target slot is compacted from the second slot and the corresponding remaining delay is copied. These are transient active-bird pointers, not fixed player numbers.

The original two-player target selector is not a conventional Euclidean, Manhattan, or wrapped-distance nearest-player test. Its integer comparisons contain asymmetries. For fidelity use the reconstructed selection function below and verify it against two-player replays, especially across the wrap seam. Do not silently substitute a nearest-distance calculation.

```kotlin
// p1/p2 are the active target slots, in registration order.
// Ineligible targets have a nonzero target-delay timer.
fun chooseTarget(self: Bird, p1: Bird?, p2: Bird?): Bird? {
    if (p1 == null) return null
    if (p1.targetDelay != 0) {
        return p2?.takeIf { it.targetDelay == 0 }
    }
    if (p2 == null || p2.targetDelay != 0) return p1
    fun u8(v: Int) = v and 255
    fun u16(v: Int) = v and 65535
    fun xComparison(player: Bird): Int {
        val px = u16(player.x)
        val sx = u16(self.x)
        val delta = u16(px - sx)
        return if (px >= sx) u16(-delta - 2) else delta
    }
    var firstMetric = u8(-kotlin.math.abs(p1.bottomY - self.bottomY))
    val firstX = xComparison(p1)
    if (firstX < 256 && firstX <= firstMetric) firstMetric = firstX
    return if (u8(xComparison(p2)) < firstMetric) p1 else p2
}
```

This function preserves the effective original selection result, including its asymmetric x comparison. It is a source-derived reconstruction requiring differential confirmation, rather than a newly designed targeting rule.

Source: [line tracking](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L3690-L3750), [target selection](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L4462-L4532), [target registration and removal](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L4655-L4665), [target countdown](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L4857-L4863).

### Bounder pursuing state machine

The exact routines occupy `BOUNDR` through `BOLAV4`. `DYLEN=14`. `PPOSY` is the integer bottom position used for target separation; `PVELY` and long excursion `PDIST` are 8.8 signed quantities. Default difficulty-5 initial values are range +14/−14 pixels, excursion −3584/+3584 raw (−14/+14 pixels), downward limiting velocity +256 raw (1 pixel per movement), wings down 2 calls, wings up 8 calls, and level reconsideration 21 calls.

| Routine group | Exact control role |
|---|---|
| `BOUNDR` | Check for troll on `LNTLAV` cadence, select target, compare vertical integer separation. No valid target or separation inside `[BOUPRG,BODNRG)` goes to level behavior. Larger positive separation descends; more-negative separation ascends. |
| `BODN/BODN1` | Set negative excursion budget `BODNDI`. While descending, add positive `PVELY` to it until it reaches or crosses zero; landing also returns to the brain. If fall velocity reaches `BODNVY`, begin a 2-call flap pulse. |
| `BODN2` | Continue accounting for descending displacement, hold flap while the timer is positive, then release and return to `BODN1`. |
| `BOUNUP/BOUP` | Check `BCKXTB[x] & BCKYTB[y−14]`; if blocked, use level behavior. Otherwise set positive excursion `BOUPDI` and begin ascent pulses. |
| `BOUP1/BOUP2` | Account for negative `PVELY` until the positive excursion goes negative. Alternate `BOUPWD` calls flap-held and `BOUPWU` calls flap-released. Return to the brain when the excursion completes. |
| `BOLEV/BOLEV1/BOLEV2` | Save own bottom line and sampled target velocity; reconsider after `BOLETM`. Use brief flaps according to current vertical direction and saved line. Preserve the exact `CMP/BLO` branch ordering; the comments “above” and “below” are inconsistent in places. |
| `BOLEVB` | If sampled target `PVELX` equals own `PVELX`, decrement `PRDIR`; on the source's signed branch conditions it may complement `PFACE`. Do not replace this with continuous pursuit. |
| `BODIRL` | At or below y=211 (`$D3`), while not rising, switch into repeated emergency ascent. |
| `BOLAVA/BOLAV1/BOLAV2` | Alternate flap/release until above y=211 or already rising, then dispatch through the species `DSMART`. This is shared by hunter and shadow-lord paths. |

During level behavior below the bottom cliff, a bounder can be lured downward if the selected player's unwrapped x coordinate is within **±63 pixels**. Otherwise it attempts an upward flap. Bounder horizontal commands generally continue its facing direction; this is not a shortest-route direct pursuit of player x.

Source: [entire bounder state machine](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L3753-L3972).

### Hunter pursuing state machine

Hunter is called “advanced bounder” and uses `B2UNDR` and its `B2*` state family. Its initial difficulty-5 values are vertical range +14/−14, excursion magnitude 14 pixels, downward velocity threshold 2 pixels per move, upward threshold −1 pixel per move, wings down 2 calls, wings up 8 calls, and 21-call level reconsideration. Use the dynamic table after adjustments, not these initial descriptions.

| Routine group | Exact difference from bounder |
|---|---|
| `B2UNDR/B2DNST/B2DN1/B2DN2` | Species-specific descent excursion and fall threshold. The initial descent uses the release path; preserve state/timer ordering. |
| `B2UNUP/B2UPST/B2UP1/B2UP2` | Waits for `HUUPWU`; when the timer expires it keeps timer at 1 until `PVELY >= HUUPVY`, then starts a `HUUPWD` pulse. |
| `B2UP3/B2UP3A/B2UP4` | If the cliff probe 14 pixels above is blocked, remember own y and wait up to 21 calls, repeatedly checking for clearance. It uses `$0040` as the falling-speed comparison in this avoidance state. |
| `B2DIR` | Predict the vertical probe offset from the high byte of the 16-bit value `PVELY << 3`. Probe 31 pixels left or right (`B2XLEN=31`) against the background tables. If blocked, face away, set an 8-call `B2AV` avoidance interval, and begin with a flap. |
| `B2DIRA/B2FDIR` | A nonzero horizontal bump overrides facing direction. Return ±1 from the resulting facing sign. |
| `B2LEV*` | Uses hunter level timer and sampled target velocity; level behavior can lure it toward lava only within **±45 pixels** horizontally. |
| `B2AV` | Releases flap and continues facing until its timer expires, then returns to `B2UNDR`. |

The predicted y probe is `signedHighByte(wrap16(vyRaw * 8))`, preserving the original truncation and sign. Collision and landing tables, including dynamically removed cliffs, are the environment representation consumed by this AI.

Source: [entire hunter state machine](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L3973-L4225).

### Shadow-lord pursuing state machine

Shadow lord uses `SHADOW` and `SH*` states. Default initial difficulty 5 is downward long-range threshold 16 pixels, upward long-range threshold −32 pixels, upward velocity threshold −2 pixels per move, short target-tracking decision interval 10 calls, own-level reconsideration 21 calls, and cliff avoidance 8 calls. Note that `SHUPRG` is `$FFE0` (−32) for the middle band, not −14; the highest starting band is `$FFF2` (−14). Preserve the table.

| Routine group | Behavior |
|---|---|
| `SHADOW` | Troll check, player selection, vertical range decision. No target goes to own-level tracking; a nearby target goes to `SHLEP`. |
| `SHDN/SHDN2` | A short sequence releasing flap while descending, then returning to the brain. The below-cliff emergency check uses horizontal speed sign at this point; do not replace it with a vertical-speed sign check merely because that would seem more intuitive. |
| `SHUNUP/SHUPST/SHUP1` | Check for a cliff 14 pixels above. Alternate a released flap call with a conditional flap when `PVELY >= SHUPVY`, returning to the main brain quickly. |
| `SHLEP/SHLEP1/SHLEP2` | Track the sampled **player's** y, not own y. Below the player or near lava, emit a pulse; release on the following state. Reconsider after `SHUPTM`. |
| `SHLEV/SHLEV1/SHLEV2` | With no valid player, preserve own y and use `SHLETM`. |
| `SHDIR` | Avoid lava below `$D0` while falling; otherwise perform the same 31-pixel, shifted-velocity cliff probe as hunter. |
| `SHDICL/SHAV` | Reverse facing and use dynamic `SHCLTM`, followed by the main brain. |
| `SHDIRB` | If horizontal velocity is nonzero, issue horizontal command 0 to preserve speed; only when stopped does it use facing-command logic. |
| `SHUP3/SHUP3A/SHUP4` | Wait for overhead cliff clearance, with a fixed 21-call reconsideration interval and `$0040` falling comparison, analogous to hunter. |

Source: [entire shadow-lord state machine](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L4226-L4455).

### Egg life cycle and remounting

An enemy rider has these gameplay states:

```text
mounted enemy
    → defeated
    → falling egg of next enemy type
    → bouncing egg
    → resting egg
    → hatching egg plus arriving riderless buzzard
    → standing rider plus arriving buzzard
    → remounting
    → mounted pursuing enemy
```

Collection can terminate any egg or standing-rider stage. Falling into lava also terminates the rider. A newly defeated mounted enemy keeps its original bird briefly as a departing riderless bird; that bird is a different object from the replacement buzzard that later arrives to collect the hatched rider.

On a mounted enemy defeat:

1. Decrement the mounted-enemy count and, if it was pursuing, the pursuing count.
2. Spawn an egg at `(enemy.x + 4, enemy.bottomY − 8)`. Copy the enemy's vertical velocity and discrete horizontal speed. Clear its bump impulses, animation counters, and first-bounce flag.
3. Promote its future mounted type, capped at shadow lord.
4. Copy the rider's remaining egg budget and subtract one. Its unresolved-rider count stays unchanged while it is an egg.
5. If the new budget is zero **and a victor exists**, immediately run the normal egg-collection routine for the victor. Because the fresh egg has not bounced, this also qualifies for the +500 air bonus. The score display is delayed through the ordinary collected-egg animation path.

Source: [defeat and promotion](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L2952-L3008).

#### Falling and bouncing

The egg uses the shared gravity increment and horizontal speed lookup. Vertical state is retained at 1/256-pixel precision:

```text
vy = wrapSigned16(vy + gravity)
yFixed = wrap16(yFixed + vy + (verticalBump << 8))
verticalBump = 0
```

At the ceiling y=32, an upward velocity is negated, position is reset to y=32 with zero fraction, and a one-pixel downward bump is scheduled. After the ordinary screen wrapping step, eggs have an additional x wrap: values above 288 subtract 284; values below 4 add 284. This maintains their 4–288 wrap interval without imposing a generic full-screen width.

Eggs update after the current enemy movement delay, which can be 2 ticks during early slowdown. Their rough collision footprint uses bottom y, top `y−6`, and right edge `x+6`. Landing and cliff-side checks use the same source-derived terrain occupancy tables as birds, with egg-specific probe offsets; a new implementation must preserve those probes rather than reusing the player's box.

On landing:

- Mark the egg as having bounced; it can no longer earn the +500 air-catch bonus, even if it later leaves the ledge.
- Align to the ledge's top.
- Move the discrete horizontal speed two units toward zero (`vIndex−=2` if positive, `vIndex+=2` if negative).
- If `vy >= 0`, change it to `−arithmeticShiftRight(vy,2)`, retaining exact integer rounding.
- Continue bouncing if the resulting `vy < −32` raw or horizontal speed is nonzero. Otherwise enter the resting timer.

A falling egg reaching bottom y≥227 (`FLOOR+4`) is lost and decrements the unresolved-rider count.

Sources: [egg motion and wrapping](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L3111-L3151), [collision and bounce](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L3154-L3223).

#### Resting, hatching, and standing

A normally landed egg starts `EGGWT`. Each resting iteration waits 12 ticks, verifies that the supporting ledge still exists, then decrements the timer. If support disappeared because a bridge or cliff changed, the egg falls again. At timer expiry, hatching is allowed only if the active/reserved enemy count is below `WENEMY`; otherwise the countdown is held at 1 and checked again after 12 ticks.

At hatch start, reserve one enemy slot and spawn the replacement riderless buzzard. It initially has maximum horizontal speed index ±8, zero vertical velocity, the promoted species, and the egg's remaining budget. If egg x>151 it arrives from x=−9 heading right; otherwise it arrives from x=291 heading left. Its initial bottom y is:

| Egg bottom y | Incoming buzzard bottom y |
|---:|---:|
| >187 | 179 |
| >105 through 187 | 104 |
| ≤105 | 69 |

The hatching animation rows are `(frame offset, collision height, duration ticks)`:

| Stage | Frame offset | Height | Duration |
|---|---:|---:|---:|
| Wiggle left | 6 | 6 | 7 |
| Upright | 0 | 6 | 3 |
| Wiggle right | 12 | 6 | 7 |
| Upright pause | 0 | 6 | 67 |
| Hatch 1 | 18 | 6 | 7 |
| Hatch 2 | 24 | 11 | 7 |
| Hatch 3 | 30 | 11 | 7 |
| Hatch 4 | 36 | 11 | Terminal marker |

After the last table row, wait another 7 ticks, display the standing rider in the promoted species' color, and refresh it every 8 ticks until collected or remounted. The terminal table value 0 is a state transition marker, not an indefinitely displayed zero-duration frame.

Sources: [resting and replacement bird setup](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L3224-L3293), [hatch playback](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L3294-L3320), [animation data](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L3535-L3545).

#### Replacement buzzard navigation

The replacement bird's goal is the egg/rider object, not a player. It flaps when below the target or falling too quickly, caps its ascent decisions using a −1 pixel-per-update threshold, and probes the upper-right middle cliff. A seeking flap is held for four AI calls and then released. It starts at maximum horizontal speed; when within the short horizontal window `[riderX−11, riderX+8]`, it slows to speed index magnitude 2 in air and seeks a stop on the ground. Once it has entered this window, later long-range steering uses a 302-pixel seeking comparison period. Ordinary bird movement wraps across 303 positions; keep these two constants distinct.

At a qualifying stopped ground state, remounting sets a six-call countdown. During that countdown the bird runs in its current facing direction. When complete, the egg/rider object loses collision eligibility and is removed, the bird acquires the rider sprite, the pursuing count increases, and the newly mounted enemy immediately uses its promoted species' pursuing behavior. In attract mode it uses the initial line-tracking behavior instead.

If the player collects the egg/rider while this bird is on its way, decrement the reserved enemy count and make the empty bird depart. Do not spawn a replacement rider or leave a hidden wave-completion blocker.

The short-range vertical admissibility branch in `SEEKE` is not a generic “same platform” test; it uses the short-range intermediate value in a way that can differ from the nearby comment. For exact native behavior, implement the source branch literally and differential-test remounts around stacked cliffs. Do not add a new platform-equality constraint that the original does not enforce.

Sources: [seeking and remount state machine](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L3547-L3680), [collection cancels incoming bird](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L3070-L3093).

### Egg-wave placement

Every egg wave creates exactly 12 eggs. First select one unused group from six ledge groups, choose one legal position within that group, and repeat until each group has one egg. Then select six more positions from the full 69-slot table, moving forward with wrap to find the next unused slot when the sampled slot is occupied. The exact generator and call order must match the game; replacing it with a shuffled list changes all later randomness.

| Ledge | Slot indices | Count | X positions | Bottom y |
|---|---|---:|---|---:|
| `CLIF1L` | 0–2 | 3 | 9,17,25 | 68 |
| `CLIF1R` | 3–7 | 5 | 253,261,269,277,285 | 68 |
| `CLIF2` | 8–18 | 11 | 86 through 166, step 8 | 80 |
| `CLIF3U` | 19–25 | 7 | 202 through 250, step 8 | 128 |
| `CLIF3L` | 26–32 | 7 | 9 through 57, step 8 | 137 |
| `CLIF3R` | 33–37 | 5 | 257 through 289, step 8 | 137 |
| `CLIF4` | 38–45 | 8 | 106 through 162, step 8 | 162 |
| `CLIF5` | 46–68 | 23 | 54 through 230, step 8 | 210 |

The six groups are `[0..7]`, `[8..18]`, `[19..25]`, `[26..37]`, `[38..45]`, and `[46..68]`. The two screen-edge pieces of the top and middle ledges are grouped together. Slot x=289 exists in the original table; do not “correct” it to 288.

The base hatch timer is `EGGWT2`, initially 56 at difficulty 5. If both players have nonzero auxiliary lives, halve this with integer right shift before assigning it. The first two created eggs receive an early-hatch reduction based on the source random value: subtract the high byte of `random0To127 × baseTimer`. Thus they retain approximately one-half to all of the base time; the remaining ten use the base timer. Each egg creation yields one tick, deliberately staggering the objects. Egg-wave eggs start with their bounce flag already set, so they never receive the airborne +500 collection bonus merely for being placed high on a ledge.

Sources: [egg-wave construction](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L2737-L2823), [egg initialization](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L2863-L2905), [all legal positions](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L2909-L2937).

### Pterodactyl behavior

#### Spawn categories and appearance

Special-wave pterodactyls are created at 65-tick intervals using the wave's count. Time-pressure pterodactyls, called baiters in the source, are created by the wave monitor and limited to three simultaneously. The latter receive all baiter-only V4 changes below.

A new pterodactyl randomly enters at x=−9 with positive speed index 2 or x=291 with negative index −2. It chooses the first vertically empty player zone in bottom-to-top order. Player zones are bottom y≥169, middle 81≤y<169, and top y<81. Candidate spawn y values are 209, 128, and 61, respectively. It starts collision-active, has an 11-pixel rough vertical footprint and 28-pixel right-edge offset (`right = x+27`), and is clamped to bottom y≤210.

Unlike birds, its vertical motion has no normal gravity. It uses a separate horizontal speed lookup:

| Speed index | −8 | −6 | −4 | −2 | 0 | 2 | 4 | 6 | 8 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Delta x raw 8.8 | −768 | −384 | −192 | −96 | 0 | 96 | 192 | 384 | 768 |
| Pixels per update | −3 | −1.5 | −0.75 | −0.375 | 0 | 0.375 | 0.75 | 1.5 | 3 |

Its ordinary patrol forces magnitude 4; directed attack forces magnitude 8. Horizontal bump direction can reverse it. The standard animation cycles four entries at eight ticks each, in the source sequence order; entry initialization begins by taking the last entry. Attack can hold the attack frame by repeatedly resetting its frame timer to 10.

Sources: [spawn and movement](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L1423-L1560), [speed and animation tables](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L1580-L1595), [zone classification](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L6413-L6426).

#### Target acquisition and attack

The next-target interval starts at 120 control calls. A special-wave pterodactyl first seeks after 60; a baiter first seeks after 138, deliberately allowing its introductory pass to miss the player. On acquisition, reload the interval from the current value, then reduce the value by one if it is above the minimum 15. Save the target's x and y; clamp the target bottom y to 210, then subtract 5. A baiter adds 2 back, making its aim two pixels lower.

Before attack, a pterodactyl adjusts toward the saved y window:

- If saved target y is above own y, accelerate upward by 64 raw (`0.25`) while `vy > −192` raw; then wait in the two-call seek substate.
- If saved target y minus 15 is below own y, accelerate downward by 16 raw (`0.0625`) while `vy < 256`; then use the same two-call wait.
- Inside the vertical attack window, set an attack timeout of 128 calls, play the attack sound, and enter attack. Baiters divide their current `vy` by four with arithmetic rounding at this transition.
- End the attack when the timeout expires or the signed difference `savedTargetX - ownX` satisfies `-13 <= dx < 13`. The -13 boundary is included and the +13 boundary is excluded. Return to patrol targeting, clear the saved target y, and continue directed flight.
- Otherwise, keep attacking horizontally at speed index ±8. Adjust vertical velocity by 16 raw toward saved y only when the current vertical sign/whole-pixel byte permits it. At exact y equality the source clears only the whole-pixel portion, retaining the fractional byte; this small behavior should be preserved in a native fixed-point port.

Baiters continue their next-target countdown during attack and saturate it at 1. Special-wave pterodactyls do not receive that update. These distinctions prevent the original safe pterodactyl-hunting strategy and are part of V4 behavior.

Sources: [targeting and attack](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L1142-L1220), [vertical seek](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L1290-L1312), [V4 patches](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L6283-L6360).

#### Patrol and exit

Patrol target lines are 67,127,206. The selection boundaries are y=101 and y=161. Baiters on the middle line use y=136 when x≤165 or x>267; between these x limits they retain y=127 to clear the raised middle-right cliff. Patrol accelerates toward the line by 32 raw (`0.125`) up to the branch limits ±256 raw. At line equality divide `vy` by four arithmetically.

All pterodactyls leave when the unresolved rider count reaches zero. Baiters also leave while the post-player-death removal delay is nonzero. Leaving pterodactyls use patrol-line control at attack horizontal speed and are removed once x<−7 or x>289; their process then waits 60 ticks before final cleanup. A baiter decrements the active baiter count when it switches into departure.

When a baiter is killed, decrement the baiter count and force `EMYTIM=1`, ending early-wave slowdown. Its death animation alternates flight frames at four-tick intervals, changes facing partway through, plays a three-frame ash animation, then displays the 1,000-point popup. This animation must not remain collision-active.

Sources: [patrol](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L1222-L1287), [exit and death](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L1316-L1408), [wave and baiter removal](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L1540-L1560), [middle-line V4 patch](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L6325-L6345).

#### Baiter timing

The timing table stores counts of 8-tick monitor iterations, using truncating integer division. Its ascending indices are:

```text
index:  0  1  2  3  4  5   6   7   8    9   10   11   12   13
count:  7  7  7  7  7  7  22  37  52  112  112  225  337  450
```

Initial wave delays are 450 iterations for wave 1 (3,600 ticks), 337 for wave 2 (2,696 ticks), and 225 for subsequent waves (1,800 ticks). Each successful spawn reads the next lower index, eventually reaching index 0 and remaining there. There are at most three active baiters. At the cap, the timer stays ready to spawn, so a new baiter can appear on the first monitor check after a vacancy. Do not reset the entire escalation sequence when one is killed.

Player death sets a 37-iteration delay (`5*60/8`, truncated). During it, existing baiters leave and the spawn timer is held until the delay reaches zero. Aggression's 112-iteration counter and baiter escalation are separate mechanisms.

Source: [monitor and table](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L2072-L2170), [death delay](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L4677-L4680).

### Lava troll and bridges

Trolls become eligible from wave 4. At most one troll is active. Only an active player or mounted enemy is a valid target; eggs, riderless birds, and pterodactyls are not selected. A target must remain airborne, bottom y≥198, and within reach of either outer lava area. The validity check tracks `x−2`, accepting `x≤42` or `x≥242`. Entering this area activates a hand at bottom y=214 and x=`targetX−2` through the landing/hazard probe.

The hand rises through five visible reach frames, using dynamic `LAVTIM` between frame advances. It tracks the target x every update. Once fully extended, it moves one vertical pixel each tick until its bottom y equals the target's bottom y+3, then grips. A target that becomes invalid causes the hand to retract through the prior frames at five-tick intervals.

While gripped:

```text
// A fresh flap impulse has already been applied by ordinary flight control.
horizontalSpeed = 0
horizontalBump = 0
verticalBump = 0
vy = wrapSigned16(vy + trollPull + (flapReleased ? 4 : 0))
if vy < -384:
    releaseTroll() // restores normal gravity and integrates yFixed += vy
else:
    yFixed += vy
    if integerY >= 230:
        terminateTrollAndSink()
    else:
        x = clamp(x, 8, 278)
        if victimOutsideTrollRegion:
            releaseTroll() // integrates yFixed += vy a second time
```

The normal base gravity increment is replaced by `trollPull`; released flap retains its extra +4 raw increment, and a fresh flap still contributes its normal signed vertical impulse. The common release routine always integrates y once. A speed-triggered escape therefore moves once; a range-triggered escape has already moved and moves a second time on release. Escape is **strictly faster upward than 1.5 pixels per update**, not greater-than-or-equal. Position is clamped into visible x `[8,278]`. Escaping the reach region also releases the target. A live player receives 50 points on escape.

On grip, the troll pull starts from dynamic `LAVGRA`, and a 1,800-tick hold timer starts. At expiry, add one raw unit of pull every tick. The actual saturation test allows the pull to reach `$0501` = 1281 raw, one above `$0500`; preserve that endpoint. If the victim reaches bottom y≥230, it is sent through ordinary lava death and both hand components terminate. The hand's displayed gripping position follows target bottom y+10, separate from the initial y+3 reach alignment.

Wave 3 creates two bridge-burning fronts, initially scanning from x=−32 rightward and x=319 leftward. Each front modifies the bottom landing region one column at a time until it reaches the central surviving cliff. The lava must first reach the required display level; each visible progress step advances the four-frame flame sequence with two six-tick waits. Ledge removal is gradual, so standing eggs can lose support while burning proceeds. When a front reaches surviving land it drops beneath the lava and refreshes the background. From wave 4, the two outer flame effects continue on six-tick frame cadence, with a four-tick phase offset between them.

Sources: [troll activation](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L6764-L6793), [hand reach and grip](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L1605-L1695), [target validity and retraction](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L1702-L1762), [hold and escape math](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L6608-L6699), [increasing pull](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L6374-L6398), [burning bridges and flames](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L5250-L5336).

### Scoring and extra lives

| Event | Score recipient | Points |
|---|---|---:|
| Defeat bounder | Victor player | 500 |
| Defeat hunter | Victor player | 750 |
| Defeat shadow lord | Victor player | 1,500 |
| Kill pterodactyl | Victor player | 1,000 |
| Kill other player | Victor player | 2,000 |
| First qualifying gladiator kill | Victor player | +3,000 bounty, on top of 2,000 |
| First collected egg/rider in chain | Collector | 250 |
| Second collected egg/rider | Collector | 500 |
| Third collected egg/rider | Collector | 750 |
| Fourth and subsequent collected egg/rider | Collector | 1,000 |
| Egg caught before first terrain bounce | Collector | +500 |
| Escape lava troll while alive | Escaping player | 50 |
| Player dies | Dying player | 50 |
| Team wave completed without player-on-player kill | Each remaining eligible player | 3,000 |
| Survival wave completed without death | Remaining eligible player | 3,000 |

Egg-chain counters are separate per player, cap at 4, reset at every wave, and reset when that player dies. A collected standing rider uses the same chain, not the mounted enemy's kill value. A fourth defeat with a victor automatically scores the final egg, advances the chain, and adds the airborne bonus because the new egg is unbounced.

Score awards can cross several extra-life thresholds; continue awarding until the next threshold is greater than the new score. The default interval is 20,000; an interval of zero disables extra lives. Retain the original score rollover and display conventions instead of allowing unrestricted display growth. The visible score has seven decimal digits, through millions; the game also retains a higher-order overflow marker. On full internal overflow the marker is kept nonzero for leading-zero display behavior. A native implementation must preserve rolling visible scores and extra-life thresholds, rather than simply saturating the displayed value.

The 50-point death award occurs **before** decrementing the player's auxiliary life total. Consequently it may cross an extra-life threshold at the death itself. This ordering can change whether the game ends; it is not cosmetic. The player kill award is applied by the collision winner routine after the victim's death callback, which is why the gladiator bounty adds to the ordinary 2,000.

Sources: [species score records](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L5550-L5577), [egg award and chain](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L3030-L3104), [death award and ordering](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L4725-L4775), [score arithmetic and extra-life thresholds](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L7348-L7415).

### Acceptance checks for a native implementation

Use these as targeted replay/assertion scenarios. They are source-derived expectations, not claims that a playable build has been tested.

1. **Factory initialization:** 5 lives, 20,000 interval, difficulty 5, gravity 4, normal egg wait 64, egg-wave wait 56. No initial difficulty adjustment at wave 1 or 2; first adjustment at entry to wave 3.
2. **Introduction order:** wave 1 produces three bounders, spaced by the 61-tick creation wait. They start line-tracking; one pursuer becomes requested after the introduction finishes. Enemy movement uses the early slowdown.
3. **Wave table:** wave 5 has 12 eggs, bounder hatch type, cap 6. Wave 8 has six hunters plus one special-wave pterodactyl and removes both top-edge cliffs plus `CLIF2`. Wave 60 has 12 shadow-lord eggs and hatch cap 6. After entry 90, execute entry 81 while incrementing the independently displayed wave number.
4. **No premature completion:** killing the last mounted enemy leaves its egg and does not finish the wave. Collecting or losing the last unresolved rider triggers completion, but departing visual objects still pass through the original inter-wave drain.
5. **Egg chain:** on successive same-wave grounded collections award 250,500,750,1000,1000. An otherwise identical airborne collection adds 500. Death resets only that player's chain.
6. **Reincarnation:** starting with one bounder and never collecting its egg, verify mounted types bounder→hunter→shadow lord→shadow lord across defeats. On defeat four, automatically score the last egg instead of allowing another remount.
7. **Hatch capacity:** let 12 egg-wave eggs time out with cap 6. No seventh replacement bird is reserved until the active/reserved count drops; blocked eggs recheck after 12 ticks.
8. **Two-player timers:** egg-wave initial base timer is integer-halved only while both players still have lives. Team wave bonuses survive enemy-caused deaths but fail after a player-on-player kill.
9. **Gladiator accounting:** first player kill grants 5,000 to the victor and 50 to the victim; later player kills in that wave grant 2,000 to the victor. Only one bounty is paid.
10. **Last-life edge:** arrange a score within 50 of an extra-life threshold, then die. Award the 50 and new life before deciding game over.
11. **Baiter classification:** first special-wave seek is 60 calls; first baiter seek is 138. Only baiters receive lowered aiming, attack-entry vertical damping, attack countdown continuation, and modified middle patrol line.
12. **Baiter timing:** initial waits are 3,600 / 2,696 / 1,800 ticks for waves 1 / 2 / 3+. Preserve the three-active cap and the ready-to-spawn timer at the cap.
13. **Troll:** unavailable before wave 4; one active hand only; equality at raw upward speed −384 does not escape, while −385 does; an escape awards 50. Test the eventual pull cap 1281.
14. **Egg terrain:** burn support out from under a resting egg; it resumes falling. After one bounce it never regains the airborne collection bonus.
15. **Target selection:** use a fixed two-player replay with swapped horizontal and vertical separations. Compare to the actual source selector, including near-wrap coordinates; a conventional nearest-distance implementation is not an acceptable oracle.
16. **Difficulty endpoints:** verify the first eligible `HUUPVY` and `SHUPVY` update matches the signed-clamp rule, including the immediate endpoint jump, rather than an intuitive gradual interpolation.

### Remaining verification boundary

All table values and the described transitions above were extracted from the repository. The native implementation still needs deterministic differential replay against the rebuilt source to confirm off-by-one timer behavior, the unusual two-player target selector, replacement-bird remount decisions near stacked ledges, and simultaneous collision/death/bonus ordering. These are specific behaviors to validate; they are not permission to invent replacement mechanics. The source-derived data and named control states give the next coding agent concrete implementation and comparison targets.
