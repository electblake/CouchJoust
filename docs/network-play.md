# Two-TV match protocol

Version 2.1.0 uses `couchjoust/match/1` as its QUIC ALPN and `com.couchjoust/match/1` as its Wormhole application ID. This is separate from the former connection diagnostic. Install the same game version on both TVs; changes to simulation rules must also change the match protocol identifier.

The host sends a Wormhole invitation containing an endpoint ticket and a random 256-bit admission secret. The joining TV opens a bidirectional QUIC stream and sends the same AES-GCM possession proof used by the connection diagnostic. The host stops accepting further peers after the first connection. There is one player per endpoint.

After authentication, every message is exactly 16 bytes: four signed, big-endian 32-bit integers `(type, a, b, c)`. All gameplay and menu commands use one reliable, ordered stream in each direction. There are no snapshot deltas or unreliable input messages.

| Type | Message | Fields |
| --- | --- | --- |
| 1 | Ready | Guest request: `a=boolean`; host broadcast: `a=player`, `b=boolean` |
| 2 | Start | Host starts a fresh deterministic two-player game; resets readiness and acknowledgement tick |
| 3 | Input | Guest's new held input bits in `a`: left=1, right=2, flap=4 |
| 4 | Tick | Host-chosen player inputs in `a,b`; simulation tick in `c` |
| 5 | Acknowledgement | Guest's last applied tick in `a` |
| 6 | Pause | Guest request or host broadcast; broadcast `a` identifies the pausing player |
| 7 | Resume | Guest request or host broadcast |
| 8 | Leave | Initiator stops gameplay and requests session exit |
| 9 | Left | Peer acknowledges exit before the initiator closes the connection |
| 10 / 11 | Ping / Pong | Five-second heartbeat keeps idle lobbies, pause screens, and results connected |

Only the host starts games and selects simulation ticks. Input transitions are queued and consumed at most once per simulation tick, retaining fast flap press/release pairs. A held input is reused until its next transition. Both simulations start from a fresh `Game`, including the same RNG state, and execute exactly the inputs chosen by the host. Rendering does not advance the simulation. Both simulations derive collisions, spawns, deaths, scores, terrain changes, waves, and final results from this timeline.

The host waits when twelve ticks are outstanding. This bounds client lag and transport backlog; there is no prediction, rollback, or alternate transport. QUIC's reliable delivery can stall simulation on loss or high latency. The guest sends acknowledgement only after applying a tick. Pause clears queued and held inputs on both TVs; resume does not replay old inputs. Results stop ticking after the game-over presentation, and a rematch requires both players to ready again.

Leaving is an explicit handshake. Application backgrounding ends the match, focus loss or active-controller removal requests shared pause. Unexpected failures propagate under the repository's fail-fast policy. There is no reconnect or host migration.

`tools/network_smoke.py` runs both real Android processes against the public pairing service and iroh, then compares their recorded game-state snapshots by match and tick. It covers shared gameplay, pause, results, fresh rematch, and peer exit. The instrumentation speeds up only the test host's clock after the first 600 gameplay ticks to reach game over sooner; the production default remains 16 ms between host pump iterations.
