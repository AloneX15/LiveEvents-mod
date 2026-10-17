# Giant meteors

Creado por **TakumiStudios**.

A giant has a physical radius of 30 blocks (diameter 60) and a crater radius of 100. It replaces 1% of random planned impacts except the configured first large impact. Remaining sizes retain their distribution.

Use `/le meteor strike <id> at <x> <y> <z> size=giant power=1`. Coordinates must identify a loaded surface in the event dimension and pass existing targeting permissions. `size=normal` is the default; other sizes are rejected. Example: `/le meteor strike 1 at 0 100 0 size=giant power=1`.

The crater reaches a nominal central depth of 35 blocks at destructive power 1 (scaled by destructive power), with an irregular edge contained within radius 100. Geometry is prepared off-thread, applied from the center outward under the existing per-tick block/time budget, and journaled for reversal. Excavation is progressive; it can outlast the event. Protected regions, immune blocks, block entities and unloaded chunks remain untouched; chunks are never force-loaded. A complete crater requires loaded, modifiable terrain. Water can refill excavated space.

A spherical wave expands at 80 blocks/second to radius 300. Each eligible entity receives at most one hit: up to 40 health points times destructive power, distance falloff and profile (safe 0.5, gameplay 0.75, cinematic 1). Knockback follows the same factors. Existing creative/spectator immunity, API factors, exclusions and nonlethal rules apply. The giant wave replaces instant blast damage. Zero destructive power retains visuals without excavation, damage or knockback.

The post-impact plume grows over eight seconds, with a roughly 120-block cap radius and 180-block height. Its duration follows smoke_seconds, capped at 180 seconds. Smoke reads terrain depth without writing it and obeys rendering budgets.

White overexposure is strongest inside 60 blocks, decreases to zero at 300, and recovers over two seconds nearby or 0.25 seconds at the boundary. Existing flash limits, client maximum intensity and profiles apply; overlapping flashes use maximum intensity. No blindness status or persistent tint is added. The sound arrives with propagation delay; physical wave arrival controls camera shake.

Network protocol is now **3**. Update both server and clients. No old protocol payload is accepted.
