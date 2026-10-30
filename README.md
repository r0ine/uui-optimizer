# UUIOptimizer

A Paper plugin that cuts the network traffic UltimateUI generates, without touching
UltimateUI itself. It drops packets that carry no new information for the client.

In a 15-bot test with the UltimateUI hub page open it removed **27% of all packets and 35% of the
bytes**, 86% of `stop_sound` and 43% of `set_entity_data`, with the final entity state on the
client unchanged. Numbers and method are in [REPORT.md](REPORT.md).

## How it works

UltimateUI renders its menus with fake entities (ids from 2,000,000,000) and sends them through
its bundled PacketEvents using `sendPacketSilently`. That call writes already-encoded bytes starting
at PacketEvents' own encoder, so handlers sitting before `packet_handler` (ProtocolLib and friends)
never see them.

UUIOptimizer puts one handler on the other side of that encoder, between it and the compression
and framing handlers. There it sees every outgoing packet as raw bytes: a packet id followed by the
payload. Packet ids are not hard-coded; they are read from the running server's protocol table at
startup, so the plugin follows Paper across versions as long as the packet names exist.

A packet is dropped only when the client already has the information:

| Rule | Dropped when |
|------|--------------|
| `entity-metadata`, `head-rotation`, `entity-velocity`, `equipment`, `attributes`, `position-sync`, `teleport` | the payload is byte-identical to the last one sent for that entity |
| `relative-move` | all deltas are zero and rotation / ground flag match what the client already has |
| `stop-sound` | the same sound was stopped within `stop-sound-interval-ms` |

State is tracked per connection and per entity, and thrown away when the entity is spawned or
removed, on respawn, and on any config change, so a fresh entity always receives its full state.

## Limits

- Only entities with id >= `min-entity-id` are touched by default (`entity-scope: uui`). Vanilla
  entities are left alone because an identical velocity packet can be a genuine second knockback.
- Clients translated by ViaVersion or ProtocolSupport are skipped: their packet ids differ.
- It cannot make UltimateUI send less in the first place. Fixing that belongs in UltimateUI;
  see the recommendations in [REPORT.md](REPORT.md).

## Install

1. Put `UUIOptimizer.jar` in `plugins/`.
2. Restart the server.
3. Adjust `plugins/UUIOptimizer/config.yml` if needed.

Requires Paper 1.20+ and Java 21. Developed and tested on Paper 1.21.4.

## Commands

Permission `uuiopt.admin` (default: op).

| Command | |
|---------|-|
| `/uuiopt stats` | per-packet counters: inspected, dropped, bytes saved |
| `/uuiopt ids` | packet ids resolved from the server |
| `/uuiopt pipeline [player]` | handler order of a player's connection, filter highlighted |
| `/uuiopt toggle` | pause or resume filtering |
| `/uuiopt reload` | reload `config.yml` |
| `/uuiopt reset` | zero the counters |

## Build

```bash
mvn clean package
```

Runs 34 unit tests and writes `target/UUIOptimizer.jar`. The lab used for the measurements is in
[lab/](lab/README.md).

## License

MIT
