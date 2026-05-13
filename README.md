# voxyserver

A fork to improve VoxyServer performance and some other random changes for my personal server.

content below were taken from the upstream repo, might be outdated.

---

> **⚠️ UNOFFICIAL 1.21.1 BACKPORT ⚠️**
>
> This is an unofficial backport of VoxyServer for Minecraft 1.21.1. 
>
> All credits for the original mod go to the original author [PooSmacker](https://github.com/PooSmacker/VoxyServer).

A fabric server side mod that voxelizes chunks into LODs using [voxy](https://github.com/MCRcortex/voxy) and streams them to connected clients. players with voxy installed will receive LOD data from the server automatically, no client side world scanning/loading needed.

**minecraft 1.21.1 | fabric (or neoforge with Sinytra Connector)**

## building

due to voxy's license, the source and binary can't be included in this repo. you'll need to clone and build it yourself.

Uses the [backport voxy for 1.21.1](https://github.com/m3t4f1v3/voxy/tree/mc_1211).

### windows

Use command prompt (cmd) for these commands, not powershell.

1. clone voxy into the root of this project:
   ```cmd
   git clone -b mc_1211 --single-branch https://github.com/m3t4f1v3/voxy
   ```

2. build the voxy jar:
   ```cmd
   cd voxy
   .\gradlew build
   ```

3. go back to the project root, make the `libs` directory, and copy the built jar:
   ```cmd
   cd ..
   mkdir libs
   for %f in (voxy\build\libs\voxy-*.jar) do copy "%f" libs\voxy.jar
   ```

4. build voxyserver:
   ```cmd
   .\gradlew build
   ```

the output jar will be in `build\libs\`.

## installation

drop the voxyserver, voxy & sodium jar's into the server's `mods/` folder. no other server side dependencies are needed.

clients just need voxy installed as normal along with voxyserver.

## config

config file is generated at `config/voxyserver.json` on first run.

| option | default | description |
|--------|---------|-------------|
| `lodStreamRadius` | `256` | radius in chunks to stream LODs around each player |
| `maxSectionsPerTickPerPlayer` | `10` | max LOD sections sent per player per tick cycle |
| `sectionsPerPacket` | `50` | max LOD sections bundled into a single network packet |
| `tickInterval` | `5` | server ticks between each streaming cycle |
| `workerThreads` | `3` | number of worker threads for voxy to use |
| `generateOnChunkLoad` | `true` | voxelize chunks as they load on the server |
| `dirtyTrackingEnabled` | `true` | revoxelize and push LODs when blocks change |
| `dirtyTrackingInterval` | `40` | ticks between dirty chunk flushes (40 = 2 seconds) |
| `debugTrackingEnabled` | `false` | output internal server stats to the console periodically |
| `debugTrackingInterval` | `200` | ticks between dumping tracking stats (200 = 10 seconds) |

### example config

```json
{
  "lodStreamRadius": 256,
  "maxSectionsPerTickPerPlayer": 10,
  "sectionsPerPacket": 50,
  "generateOnChunkLoad": true,
  "tickInterval": 5,
  "workerThreads": 3,
  "dirtyTrackingEnabled": true,
  "dirtyTrackingInterval": 40,
  "debugTrackingEnabled": false,
  "debugTrackingInterval": 200
}
```

higher `lodStreamRadius` means more LOD coverage but more storage and bandwidth. `maxSectionsPerTickPerPlayer` controls how fast LODs are sent to new players or when moving into unexplored areas. `sectionsPerPacket` controls how many sections are packed into each network packet (higher = fewer packets but larger each). lower `tickInterval` = more frequent streaming checks.

### client config

> Note: In this backport, the in-game config UI (ModMenu/ClothConfig integration) was removed for compatibility with Sinytra Connector. Client preferences must be edited manually in the config file.

Client preferences are stored in `config/voxyserver-client.json` with a default profile plus per-server overrides keyed by their `host:port`. if a server has no saved override yet, the default profile is used.

| option | description |
|--------|-------------|
| **Enable LOD Streaming** | toggle whether the server sends LOD data to you |
| **LOD Stream Radius** | personal LOD radius in blocks, 0 = use server default |
| **Max Sections Per Tick** | personal rate limit for sections sent per tick, 0 = use server default |

these values are capped at the server's configured maximums and sent to the server automatically when saved.

## commands

server admins can import existing generated chunks into voxy storage with the following commands:

- `/voxyserver import existing all`  
  imports all loaded dimensions that have a `region` folder
- `/voxyserver import existing current`  
  imports the current dimension of the executing player
- `/voxyserver import existing dimension <dimension>`  
  imports a specific loaded dimension, for example `minecraft:overworld`
- `/voxyserver import existing status`  
  shows the current import status
- `/voxyserver import existing cancel`  
  cancels the active import job

imports run sequentially and read existing `region/*.mca` files directly, which is much faster than waiting for chunks to be loaded normally. imported dimensions are refreshed for connected voxy clients automatically once the import completes.

## storage

LOD data is stored per world at `<world>/voxyserver/`. this can be safely deleted to regenerate all LOD data.

## license

This mod is licensed under GNU GPLv3.
