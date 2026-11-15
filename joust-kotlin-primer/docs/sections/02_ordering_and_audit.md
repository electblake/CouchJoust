## Native gameplay ordering randomness and fidelity audit

This audit checks `physics_collision.md`, `ai_waves_scoring.md`, and the gameplay-table extractor against source commit `87e22aab945709fa107df609c0efcb839a17c1f9`. It supplies the update-order contract and shared random algorithm needed by a native Kotlin implementation. It does not prescribe an instruction interpreter or a hardware model.

### Update order is gameplay behavior

Keep an ordered, mutable list of gameplay work items. A work item has a category, a continuation state, and a sleep countdown. A bird, an egg, a collision pass, the wave director, and a transient effect are separate work items where the source treats them separately. Drawing can be implemented normally in Kotlin; the required ordering concerns gameplay state, timers, collision eligibility, spawning, and random draws.

The source runs **all primary work before secondary work**, while keeping one common list. Primary traversal starts at the head each tick. Secondary traversal resumes from its saved predecessor, so a deferred secondary item is not silently skipped or restarted. Within each category, traversal follows the current list order. A sleep countdown is decremented when that item is visited in its category; if it becomes zero, run its continuation until it sleeps or deletes itself. It is not a global timer decremented independently of that item's visits. A newly created item starts at countdown 1 unless the caller replaces that value. An explicit zero countdown wraps to 255 on the next decrement and wakes on the 256th visit; do not interpret zero as an immediate wake. [A01][A02]

The native ordering contract is:

```text
begin gameplay tick
    advance queued sound-event timers
    visit primary work from list head in current list order
    resume secondary work from saved predecessor in current list order
    commit this tick's render state

visit(item, category)
    if item.category differs: advance without changing its countdown
    item.countdown = unsignedByte(item.countdown - 1)
    if item.countdown == 0:
        run item.continuation until sleep or removal

sleep(item, count, continuation)
    item.countdown = unsignedByte(count)
    item.continuation = continuation
    continue traversal after item

insertAfter(anchor, newItem)
    newItem.next = anchor.next
    anchor.next = newItem
    newItem.countdown = 1  // unless immediately overridden
```

This is a contract for a native task/state model, not a requirement to reproduce the source's storage layout. Use an explicit linked list or an equivalent structure whose mutations have the same ordering. A snapshot array of all actors taken at tick start changes same-tick spawning. [A01][A02]

#### Normal play order

The relevant initial list positions are:

| Relative position | Category | Observable effect |
| --- | --- | --- |
| Player collision pass `PLYCOL` | Primary | Decrement target-immunity delays; resolve player 2/player 1 and player/opponent contacts against positions available before either player's movement this tick. |
| Player 2, when present | Primary | Sample player 2 input and perform its movement/state continuation. |
| Player 1 | Primary | Sample player 1 input and perform its movement/state continuation. |
| Opponent collision pass `OPPCOL` | Secondary | Resolve mounted-enemy/pterodactyl pairs before their movement continuations. |
| Wave director `CIA` | Secondary | Advance wave introduction/cleanup/monitoring; create enemies, eggs, or special-wave controllers at its own list position. |
| Director's following chain | Mostly secondary | Run enemies, eggs, pterodactyls, and their inserted continuations in current list order. |

Background effects, score-display work, and temporary effects can interleave with these positions; the listed relative gameplay ordering is the important contract. Player 2 precedes player 1 because both are inserted after the same initialization anchor. The later insertion becomes the earlier list node. Player collision detection is created afterward and inserted ahead of both players. Opponent collision detection is inserted before the wave director. These are consequences of creation rules, not player-number priority chosen by the port. Target slots are populated in activation order, so slot 1 is not guaranteed to be player 1; initial simultaneous activation can register player 2 first. [A02][A03]

Do not replace this with “move every actor, then collide,” or “move and collide each actor individually.” In particular, a player collision resolved this tick may change an opponent's velocity, kill its rider, or create an egg before that opponent's secondary movement service. A target-delay timer reaching zero in the primary contact pass can make that target eligible for AI later in the same tick. [A03][A04]

#### Insertions and removals

There are two operations: create after the currently executing work item, and create after an explicitly chosen actor. Preserve the chosen anchor:

| Event | Exact insertion anchor | Consequence |
| --- | --- | --- |
| Ordinary enemy introduction | Wave director | Newest introduced enemy is earlier in the director's chain than older surviving enemies. |
| Enemy defeated inside a player collision pass | Defeated enemy | The new egg follows that enemy, although the currently executing work item is the collision pass. |
| Resting egg begins hatching | Egg/rider | Its approaching replacement bird follows the egg. |
| Troll reaches for a bird | Victim's predecessor | Rear hand precedes the victim. |
| Troll grips a bird | Victim | Front gripping hand follows the victim. |
| Egg air-bonus popup | Ordinary egg-score popup | The extra 500 popup follows the main popup. |

Repeated insertion at a fixed anchor reverses creation order. If a new same-category item lies ahead of the active traversal cursor, its countdown can be decremented and its initial continuation run **during this pass**. Its initial continuation may itself immediately sleep; “runs this pass” does not mean it necessarily performs a motion update. An item inserted behind a cursor waits for that traversal to return. A primary item created during the secondary pass waits for the next primary pass. A secondary item created during primary work becomes eligible when the secondary cursor reaches it. [A02][A05]

Remove a work item immediately from the live order. If it is the saved secondary predecessor, replace that saved pointer with the surviving predecessor. The current traversal resumes through the surviving predecessor's next link, so deleting the current item does not skip its successor. Disabling collisions is separate from deleting an actor: riderless mounts, collected eggs, dead pterodactyls, and popups can remain in the list after their contact eligibility is gone. [A01][A02]

#### Pair traversal and simultaneous outcomes

`PLYCOL` searches forward from its own position for a live player, then pairs that player with each later collision-enabled item. With both players initially active, player 2 is the first outer participant. It resolves player 2 versus player 1 once, then player 2 versus following opponents; later it resolves player 1 versus following opponents. `OPPCOL` similarly examines later pairs but filters them to enemy/pterodactyl participants. Eggs are collected through the player pass, not opponent-versus-opponent contact. [A04]

Apply each outcome immediately. If the outer participant dies, stop its remaining pairs and resume with the next outer participant. The equal-lance-height player tie also takes this stop-current-participant route despite neither rider dying. A defeated inner participant can still have an animation work item, but its contact-enabled state is already clear. The next pair must see all preceding score, life, rider-count, and eligibility changes. These details affect multiple simultaneous contacts and cannot be replaced by gathering a set of contacts and resolving them in arbitrary order. [A04]

### Cadence boundary

Primary ticks have the project cadence defined in the main specification. Individual actor delays still matter: early enemies and falling eggs can use delay 2; the director's monitor uses delay 8; resting eggs use delay 12. These are counts of eligible service visits, not Android presentation frames. AI decisions occur when the bird's movement continuation calls its controller. A change to an AI continuation may take effect immediately by dispatching into another decision, or on the next controller call; do not impose one automatic transition per tick on every AI state. [A01][A06]

The source can pause the secondary traversal at a deadline and resume it next tick. Thus a secondary sleep count may span extra primary ticks during load. The exact pattern of those deferrals has **not** been established by gameplay traces in this review. A native implementation can start with one complete ordered secondary pass per fixed tick, but that is an explicit timing baseline requiring comparison, rather than a demonstrated match to every overloaded original frame. Retain a secondary-resume cursor in the design so traced scheduling can be matched without changing game rules. Do not let the Kotlin renderer's variable frame time decide which AI actors receive service. [A01]

### Shared random sequence

The game uses one shared two-byte sequence with two outputs per draw: a value `r` in 0–127 and a one-bit output `b`. Some callers use only the bit; some use `r`; some combine them as `2*r+b`. A Kotlin `Random.nextInt(...)` substitution changes both distributions and later draw order. Use the included `JoustRandom.kt`, which is the following pure arithmetic reconstruction. [A07]

```text
oldHigh = highByte
oldLow = lowByte
mixed = (oldLow unsignedShiftRight 3) XOR oldLow
r = mixed unsignedShiftRight 1
b = oldLow AND 1
highByte = (oldHigh unsignedShiftRight 1) OR ((mixed AND 1) << 7)
lowByte = (oldLow unsignedShiftRight 1) OR ((oldHigh AND 1) << 7)
return (r, b)
```

A separate time event performs `highByte=(highByte+1)&255` and `lowByte=(lowByte-1)&255`. The byte operations wrap independently; this is not addition of 255 to the combined 16-bit state. The time event does not reset at wave transitions or new credits. The seed and the ordering of these events relative to draws must be recorded for a repeatable native replay. The exact original phase of those time events relative to every random call is not established here. The primitive arithmetic is exact; a seed alone does not establish an identical whole-game sequence. [A07]

For a simple source check, starting from high=`0x12`, low=`0x34`, one draw returns `(25,0)` and changes state to `(0x09,0x1A)`. Applying the time event first changes the initial state to `(0x13,0x33)`; the next draw returns `(26,1)` and leaves `(0x89,0x99)`.

#### Draw consumers

| Consumer | Exact use |
| --- | --- |
| Pterodactyl creation | One draw; its one-bit output selects left or right entry. |
| Transporter selection attempt | One draw after evaluating occupancy; the bit and the low bit of `r` select the first transporter in a fixed fallback search. Retry attempts consume additional draws. |
| First six egg-wave positions | One draw selects a ledge group via `floor(r*12/256)`; search backward with wrap for an unused group. Another draw selects a slot within that group's width using `floor((2*r+b)*width/256)+groupStart`. |
| Last six egg-wave positions | One draw selects `floor(r*138/256)` in 0–68; search forward with wrap for an unused slot. |
| First two egg-wave hatch times | Each creation consumes an additional draw. Replace base timer `t` with `t-floor(r*t/256)`. These draws are interleaved with placement, not performed afterward as a batch. |
| Lava bubbles | Consume the same generator. The selected bubble or surface-burst path has different subsequent waits, changing when the next draw occurs. Cosmetic bubble choices therefore influence later gameplay draws. |

The first six egg creations occur one at a time, with a sleep after each. The extra early-hatching draw happens inside creation before that sleep. Preserve this draw sequence and avoid shuffling a precomputed list with a library shuffle. Standard enemy pursuit is state-driven; it is not implemented as random turns. A separate `RADDR` consumer exists only on the failed integrity-check path and is not part of a valid-source normal game. [A08]

### Wave completion and score event ordering

The two end-of-wave conditions are separate:

1. The 8-visit monitor sees `NRIDER==0` and enters next-wave preparation. This happens before baiter countdown or aggression processing in that iteration.
2. Before actual introduction, `WATWAV` waits until the wave director's following chain is empty. Departing mounts, pterodactyls, score popups, and other work can delay this even after there are no unresolved riders.

The director resets both egg-chain counters, resets default caps/delay, increments the displayed wave number, and calls the previous wave's award callback **before** advancing to the next wave-table entry. Previous special-wave bonus flags therefore must survive until that callback, then the gladiator flags are cleared. New cliff changes and introductions follow. Do not perform all cleanup at the instant the last enemy mount loses its rider. [A09]

`EGGSCR` awards base egg score and the optional air bonus immediately, then decrements `NRIDER`, cancels a reserved approaching bird if present, disables the egg's contacts, and schedules the later visual popup path with delay 4. The displayed score popup is not the time at which points are earned. That remaining popup can still hold up `WATWAV`. [A10]

For a player joust death, disable victim contacts and rider; execute the victim death callback; then award the normal victim value to the victor. Inside the player death callback, reset egg-chain and survival flags, process the first gladiator bounty when applicable, award the victim 50 points, and decrement the auxiliary life total. The 50 can cross an extra-life threshold and prevent a last-life game over. On a gladiator kill, the bounty and normal 2,000 kill score are distinct successive awards. Preserve each threshold check rather than combining all participants' score changes into a post-tick summary. [A11]

### Corrections from this audit

The following corrections were applied to `ai_waves_scoring.md` or sent to the physics author for integration:

| Item | Correct native behavior | Source |
| --- | --- | --- |
| Seeking versus movement wrap | Replacement-bird seeking compares with period 302; actual bird movement wraps across 303 integer positions. | [A12] |
| Last egg auto-collection | The fourth defeat auto-collects the freshly created egg only when a victor exists. A lava death has no victor. | [A10] |
| Pterodactyl attack exit window | End the attack for `-13 <= savedX-ownX < 13`; the left endpoint is included and the right endpoint excluded. | [A13] |
| Troll gravity | Ordinary fresh-press flap impulse happens first. Captured integration adds `trollPull + (flapReleased?4:0)` rather than ordinary gravity. | [A14] |
| Troll escape position | Shared release always integrates `y+=vy`. Speed escape reaches it before integration and moves once; range escape reaches it after integration and moves twice that update. | [A14] |
| Score rendering | Seven score digits through millions are visible; the next high decimal digit is an overflow marker, not an eighth normal visible digit. | [A15] |

The troll double-integration case is especially easy to smooth away inadvertently. It comes directly from the branch to common release after the range test. The strict escape threshold remains `vy<-384`, and the rising pull endpoint remains `0x501`. [A14]

The following detailed physics descriptions were independently checked and agree with the source: ordinary 303-position wrapping; sampled flap edges and held/released gravity; ground takeoff's distinct initial lift; profile-row collision rather than visible-alpha collision; one-sided row scanning after the first horizontal separation; player versus opponent tie branch asymmetry; normal vertical reflection; top-of-cliff `floor((-vy-1)/2)` including `vy=0` becoming -1; enemy reserved-slot counting during hatching; and immediate egg scoring before delayed popup. These are source checks, not executed full-game replay results. [A04][A06][A10][A12][A14]

### Extraction reproducibility

`extract_gameplay_tables.py` is portable and uses only Python's standard library. Run it from any directory with explicit input/output paths:

```sh
python extract_gameplay_tables.py --source-dir reference/source --output-dir data
```

It now checks the exact source-file SHA-256 before producing pinned-commit citations. A changed or newline-normalized source file fails with a clear mismatch error, preventing altered data from being mislabeled as the frozen revision. The known original `JOUSTRV4.ASM` SHA-256 is `bc8b177d44e5bba5e5f9f4687c32c007309d9f476ca0e7f1b67cc413fe409ec3`.

Verification copied the script and original input into a fresh temporary directory, ran using relative input/output paths from that different working directory, and compared outputs byte-for-byte against the delivered files. Both matched. A deliberately modified input was rejected. Results and output hashes are in `extraction_verification.json`.

Assertions cover 90 wave records, repeat entry 81, 28 dynamic parameters with ten difficulty periods each, 69 egg positions in eight physical ledge ranges, six placement groups, and the fourteen live V4 baiter timings. Numeric expressions are parsed through a restricted AST evaluator rather than executed. CSV values and JSON tables are extracted; difficulty snapshots are explicitly derived by the source's signed per-wave update/clamp. On egg-wave rows the encoded enemy counts are hatch-species/capacity selectors, not extra initially spawned mounted enemies; that distinction is now stated in JSON metadata.

### Remaining fidelity boundaries and priority

| Priority | Specific remaining work for the native implementation | Why it matters |
| --- | --- | --- |
| P0 | Implement the live-list primary/secondary ordering and same-pass insertion rules above before tuning movement. | Changes simultaneous jousts, pursuit-slot acquisition, hatch timing, and new-wave cleanup. |
| P0 | Preserve each AI state's immediate dispatch versus next-call continuation. Use the detailed state tables together with the bundled named source routines; the prose summaries are not an executable AI implementation. | A one-call delay at each transition changes flap pulse lengths and enemy behavior even with correct constants. |
| P1 | Validate the unusual two-target chooser with source-reference traces at both wrap sides and after a target slot is removed/compacted. | The reconstructed chooser intentionally retains asymmetry; a conventional nearest-target implementation will differ. |
| P1 | Compare secondary-service deferral and actor visit counts in crowded waves to the initial complete-pass native timing baseline. | Original sleep counts are visitation-based and can stretch relative to primary ticks. |
| P1 | Record random state, random calls, and time-perturbation events together; include bubble draws. | Fixed input plus a nominal seed alone is insufficient for an exact original sequence. |
| P1 | Exercise standing-rider collection during the replacement bird's close-range window, a fourth defeat in lava, and egg collection during same-tick player death. | These combine immediate counters, cancellation, score thresholds, and delayed cleanup. |
| P1 | Exercise the two troll escape routes separately, including a range escape that moves twice. | Generic gravity/integration cleanup can remove this verified behavior. |
| P2 | Compare simultaneous popup overlap shortening and late-wave cleanup visuals after multiple egg catches. | Popup work affects both presentation and the following-chain drain condition. |

These are concrete implementation and validation targets. The extracted static data are complete for the tables they cover, and the arithmetic statements above are source-derived. This review has not executed a complete native game or established frame-identical end-to-end replays; the bundle should not be described as an already validated native clone.

### Audit evidence

- [A01] [Update traversal, sleeps and secondary resume, SYSTEM.ASM 158–255](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/SYSTEM.ASM#L158-L255).
- [A02] [Removal and insert-after semantics, SYSTEM.ASM 320–408](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/SYSTEM.ASM#L320-L408), [category and sleep macros, RAMDEF.ASM 8–40](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/RAMDEF.ASM#L8-L40).
- [A03] [Gameplay work creation order, JOUSTRV4.ASM 1014–1078](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L1014-L1078).
- [A04] [Collision traversal, joust and bounce branches, JOUSTRV4.ASM 4811–5216](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L4811-L5216).
- [A05] [Enemy introduction, JOUSTRV4.ASM 2186–2212](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L2186-L2212), [egg-after-victim creation 2952–3008](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L2952-L3008), [replacement bird 3224–3292](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L3224-L3292), [troll anchors 1642–1666 and 6764–6793](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L1642-L1666), [troll activation anchor](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L6764-L6793), [popup insertion 3383–3412](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L3383-L3412).
- [A06] [Player and shared bird movement, JOUSTRV4.ASM 5910–6257](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L5910-L6257), [AI state continuations 3723–4455](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L3723-L4455).
- [A07] [Shared random draw, SYSTEM.ASM 741–754](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/SYSTEM.ASM#L741-L754), [independent time perturbation 578–589](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/SYSTEM.ASM#L578-L589).
- [A08] [Pter entry, JOUSTRV4.ASM 1423–1444](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L1423-L1444), [transporter selection 5663–5716](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L5663-L5716), [egg placement 2777–2824](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L2777-L2824), [early hatch draws 2862–2905](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L2862-L2905), [bubbles, SYSTEM.ASM 829–902](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/SYSTEM.ASM#L829-L902), [integrity path, JOUSTRV4.ASM 7883–7898](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L7883-L7898).
- [A09] [Inter-wave ordering and live-chain drain, JOUSTRV4.ASM 1889–2248](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L1889-L2248).
- [A10] [Enemy final egg and immediate collection, JOUSTRV4.ASM 2952–3094](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L2952-L3094), [popup delay and overlap handling 3329–3444](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L3329-L3444).
- [A11] [Player death callbacks and bonuses, JOUSTRV4.ASM 4669–4787](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L4669-L4787), [winner callback order 5057–5079](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L5057-L5079).
- [A12] [Replacement-bird seeking 3549–3706](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L3549-L3706), [movement wrapping 7272–7310](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L7272-L7310).
- [A13] [Attack x window, JOUSTRV4.ASM 1178–1220](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L1178-L1220).
- [A14] [Captured gravity and release integration, JOUSTRV4.ASM 6608–6700](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L6608-L6700), [pull increase cap 6374–6397](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L6374-L6397), [profile collision and cliff responses 6919–7145](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L6919-L7145).
- [A15] [Score digit display, JOUSTRV4.ASM 7419–7449](https://github.com/synamaxmusic/joust/blob/87e22aab945709fa107df609c0efcb839a17c1f9/JOUSTRV4.ASM#L7419-L7449).
