# UltimateUI 1.4.3: network traffic analysis

**Author:** r0ine  
**Environment:** Paper 1.21.4 build 232, JDK 21, UltimateUI 1.4.3, localhost, mineflayer 4.39 bots

## What UltimateUI sends

With the hub page open, one client receives roughly 20,000 packets in 30 seconds. The part that
belongs to UltimateUI comes from 118 fake entities (ids from 2,000,000,000) and a sound reset loop:

| Packet | Rate per player | Notes |
|--------|-----------------|-------|
| `set_entity_data` | ~48/s | over 40% repeats values the client already has |
| `stop_sound` | ~24/s with 15 players, ~80/s with one | the same few sounds, over and over |

## How the packets leave the server

UltimateUI sends them with PacketEvents' `PlayerManager.sendPacketSilently`, which ends in
`ChannelHelper.writeAndFlushInContext(channel, "pe-encoder-ultimateui", buf)`. In the bundled
implementation that is `pipeline.context(name).writeAndFlush(buf)`.

That has two consequences:

- The write starts at PacketEvents' encoder and travels toward the head of the pipeline, so
  handlers on the tail side of it (ProtocolLib, anything added before `packet_handler`) never
  see these packets. This is why a normal packet interceptor reports nothing for UltimateUI's
  entities.
- Handlers on the head side, between that encoder and the compression/framing handlers, do see
  them, as encoded `ByteBuf`s.

An earlier revision of this report claimed PacketEvents bypassed the pipeline through
`channel.unsafe().write()`. That was wrong: the bytecode shows a plain context write, and the
packets are reachable from the head side. UUIOptimizer takes advantage of that.

## Results

15 bots, 35 s each run, hub page open, same server session, runs alternated (off, on, off... as
`kapali`, `acik`, `kapali2`, `acik2` in `lab/`). Bytes are the length of the decoded packet as JSON
on the bot, so they are a relative measure only.

| | filtering off (2 runs, mean) | filtering on (2 runs, mean) | change |
|---|---:|---:|---:|
| all packets | 111,564 | 81,686 | **-26.8%** |
| bytes | 18.6 MB | 12.1 MB | **-35.2%** |
| `stop_sound` | 12,799 | 1,787 | **-86.0%** |
| `set_entity_data` | 25,706 | 14,639 | **-43.1%** |

The rest of the traffic is vanilla player entities (the bots see each other) and metadata that
really changes (UltimateUI animates the translation of its entities), so it is not droppable.

### Correctness check

Dropping packets must not change what the client ends up with. Three bots recorded, for every fake
entity, the final value of each metadata field plus accumulated movement, rotation, velocity,
equipment and attributes. `lab/compare.mjs` compares those states between runs:

- Every field of every one of the 354 tracked entities is identical with filtering on and off,
  except `meta.11` (the animated translation) and `meta.9`.
- Those two fields also disagree between two runs with filtering **off**, so they are animation
  noise, not an effect of the filter.

### Not measured

The filter's own CPU cost was not profiled. It does a varint read and a hash lookup per packet and
a byte comparison for the kinds it deduplicates. Server-side MSPT figures from earlier spark
sessions (roughly 3.2 ms to 5.5 ms median with the hub open) were not repeated for this version.

## Recommendations for the plugin author

Sending less in the first place is better than filtering it afterwards.

1. **Deduplicate entity metadata before sending.** Keep the last list of `EntityData` per entity
   and viewer and skip the packet when the new list is equal. Filtering confirms that over 40% of
   `set_entity_data` is repeats.
2. **Throttle `stop_sound`.** A given sound needs stopping once, not dozens of times a second.
3. **Batch per tick.** Collect entity updates for the tick and send them in one bundle.
4. **Freeze entities of closed menus.** Nothing should be updating while no page is open.
