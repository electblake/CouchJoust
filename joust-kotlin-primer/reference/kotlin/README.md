# Native Kotlin reference helpers

`GameMath.kt` translates individual recovered formulas and table lookups. `ControllerState.kt` reduces held host inputs to the three original switches. `JoustRandom.kt` supplies the shared original random draw and explicit time perturbation. They are intentionally small reference components, not a game skeleton or an APK project.

The Kotlin files have been inspected but were not compiled in this preparation environment. Their numeric formulas have source-derived vectors in `../../verification/math-vectors.json`; a build agent should run those vectors against the Kotlin implementation when adding it to the actual project.

Source mappings:

| Helper | Pinned source |
|---|---|
| Flap impulse, flight integration | `JOUSTRV4.ASM ADDFLP, ADDGRV, FLYX` |
| Direction and wrapping | `JOUSTRV4.ASM JOY, WRAPX` |
| Wave repeat | `JOUSTRV4.ASM WAVTBL, WTBRST, WTBEND` |
| Egg points | `JOUSTRV4.ASM EGGSCR` |
| Difficulty clamp | `JOUSTRV4.ASM IWAVE, DYTBL` |
| Random draw/perturb | `SYSTEM.ASM RANDSYS, IRQ` |

Keep state transition order in the full specification. Calling correct formulas in a different order still changes gameplay. In particular, ground takeoff has its own initial lift, airborne horizontal acceleration occurs on an accepted flap press, and ground stride countdown affects the initial horizontal remainder when entering flight.

The controller reducer merges d-pad, hat and stick into held left/right states; simultaneous opposite directions are neutral. Its 0.5 stick threshold is a stated port choice. Key-repeat must not produce new flap edges. Device assignment, Android callbacks, event-to-tick sampling, host pause and reconnection belong in the Android adapter.
