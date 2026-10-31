# TongDaRailway - EOW fork

This independent GPL-3.0 fork is based on [Hello-LuckyHuang/TongDaRailway](https://github.com/Hello-LuckyHuang/TongDaRailway), upstream commit `2df7dd5`. Original authors and license are retained. EOW-specific modifications by Jasdew Starfield, 2026-10-29.

The fork supports seed-driven regional railway systems for abandoned infrastructure, alongside explicit seed-bound routes. It retains the original route, vertical-profile, roadbed, bridge, tunnel and Create track implementations. It does not include EOWCore source code.

- Set `fixedNodeMode = true`. To plan a new world automatically, create `config/tongdarailway-auto-network.json` with `{"enabled":true,"regional":true,"region_probability":0.28,"critical_sites":["eowcore:radar_station"]}`. When disabled or absent, `config/tongdarailway-fixed-nodes.json` supplies explicit seed-bound nodes.
- Automatic mode reads Core strategic sites after initialization. With `regional: true`, deterministic 3-by-3 region planning areas (12,288 blocks per side) participate with probability 0.28; areas containing critical landmarks always participate. A 4-5 stop corridor has a seed-selected bearing and an initial length of 8,600-10,000 blocks. A bounded seven-option lateral search follows coarse 256-block terrain columns, balancing distance, relief, low surfaces and changes of direction. With a 40% candidate chance, two side stops may reconnect two existing main-line stops to form an irregular return branch. Failed siting or connections may leave an open branch. Landmark stations and suitable mine mouths attach to the main corridor. Once per second, overworld player positions queue the surrounding 3-by-3 large planning areas, with center-first deduplication and at most 64 pending requests. One low-priority worker preplans active systems without generating chunks. Demand planning bypasses the speculative queue, and new speculative jobs pause while demand work is active. Saved plans are reused. Each system retains an eight-node budget and the 4096-block per-link search limit. Region boundaries still bound placement to avoid collisions between independent systems. Use fresh worlds for this layout; `regional: false` retains the finite starter mode.
- Each node supplies `id`, `rail` XYZ, horizontal `facing`, `protected_center` XYZ and `protected_radius`. Links supply `from` and `to` node IDs. The root has `format: 1`, a world `seed`, `nodes`, and `links`.
- Node ports must be outside protected building/shaft footprints. Nodes without a station template receive a small loading pad. The bounded network accepts at most eight nodes, twelve explicit links and 4096-block links.
- Repair intervals are fixed by route identity and seed. Bridge failures omit the central deck and supports; tunnel failures contain rubble, and surface failures contain uneven landslide piles. Planned damage avoids the first and last 80 blocks. Repair sites are shifted away from long native curves. A removed short curve expands the physical scar to its footprint, capped at 40 blocks; the same saved intervals drive track omission and roadbed damage.
- With `placeTracksUsingTrackSpawner = false` (default), each chunk places its own rails and curve endpoints during generation. Native curve NBT is saved immediately, including during DH preview generation. Only Create graph activation waits for loaded endpoint neighbourhoods, with at most two bootstrap batches and normally 32 rail positions per server tick. Planned damage remains absent. The legacy spawner option remains available.
- Client-only visual snow is enabled in `tongdarailway-client.toml`. It reads nearby vanilla/Snow! Real Magic snow at rail height, adds a thin world-aligned square only to supported track/fake-track block cells, and leaves native blocks, collisions and train logic unchanged. The visual surface matches native one-layer snow height. The independent `curveSleeperSnowClearance` client option raises native curve sleepers by 1/64 block to avoid intersecting real thin snow, including with the overlay disabled; re-enter the world after changing it. Rails and train geometry stay unchanged. Clearing nearby snow removes the appearance. It uses the active resource pack snow texture and does not add a DH rendering layer.
- Format `2` adds optional `role`, `station` (a bundled station descriptor name), and `rotation` (0/90/180/270) to each node. With `auto_connect: true`, omit `links`: a deterministic, distance-based tree connects the supplied candidates, with at most three links per node. Named native station exits and interior tracks join the same per-chunk Create graph bootstrap. Station anchors are search preferences: a bounded 128/192-block search chooses a dry, flatter footprint and derives rail height from median terrain height. The shortlist uses exact footprint samples, limits relief to eight blocks and fill/cut to five, and reserves POI/approach space. Small remaining foundation gaps receive at most six blocks of fill. Grounded stations try alternative native exits and at most two 512-block wider approaches when a direct connection cannot pass the original grade/protection checks. Candidate discovery remains external; this does not scan the world for newly generated structures.
- Station rotation is a preference: native orientations and all neighbouring link bearings are evaluated together, and every station assigns its mouths globally. A failed route retries its path while retaining the assigned mouth. Fixed mode rejects forced/folded curves and excessively tight horizontal bends. Native graph discovery reads loaded chunks only; deferred frontiers retry when both sides become available. This avoids blocking the server while remote rails generate.
- The three EOW station templates are static edited NBT assets: sparse surviving lanterns, broken original glazing, coherent roof/sidewall collapses and sheltered cobwebs outside the track clearance. Original decorative blocks, iron finishes, casings, copycats, empty tanks and storage banks remain. Alloy blocks, advanced display boards and the factory station's two train controls are removed. Track blocks and both native exit markers are preserved.
- Regional density and critical sites are frozen in `world/data/tongdarailway-regions/network.json`; individual system files store nodes, links, exact placements and resolved route/track geometry. All nine member regions share one planning job and snapshot. Full route/endpoint working indices use a 32-system cache. Finite starter mode continues to use `world/data/tongdarailway-auto-network.json`. Restarts reuse saved site decisions. Selection uses base terrain and at most twelve structure-start previews, without requesting remote chunks. Mine discovery may find no suitable surface entrance. Small stations use an editable static NBT platform; required stations can fall back to this smaller footprint and search up to 512 blocks, retaining the same dry-ground limits. Changing anchors is intended for new worlds. Upstream and fork share the `tongdarailway` mod ID and must not be installed together.

Build with Java 21: `./gradlew build --no-daemon --no-configuration-cache`. This fork targets NeoForge 21.1.262 and Create 6.0.10. Upstream Java files retain their GBK encoding.

New EOW routes prefer compact native corners and real cardinal/diagonal track blocks. Wide S bends use two short 45-degree bends with a straight diagonal/cardinal middle; long grades use several grid-aligned connectors. Native curve endpoint spans are capped at 96 blocks, and unsuitable routes retry their layout. Existing generated track geometry is retained; use a new world to inspect this generation change.

Long native curves in the fallback renderer retain Create's 192-block distance limit measured from their cached world-space bounds. Passing beyond 192 blocks from the owning endpoint no longer hides a nearby curve. This is a client-only rendering change; existing tracks and train data are retained.

## EOW 分支

本分支独立维护，保留上游作者与 GPL-3.0 授权。支持按世界种子自动规划有边界的起始区域路网，也可使用带种子校验的固定节点文件。节点附近保留建筑／矿井保护范围，并生成小型装卸平台。线路沿用上游路基、桥隧和 Create 曲线轨道，加入固定的维修路段：桥梁断面缺失桥面和支撑，隧道内生成塌方碎石，地面路段生成不规则滑坡堆积。损毁会避开长曲线；短曲线被移除时，实际损毁范围同步覆盖该曲线，最长 40 格，缺轨与路基损毁使用同一份存档范围。

格式 2 支持候选节点的 `role`、原生站型 `station` 与旋转 `rotation`。设置 `auto_connect: true` 并省略 `links` 后，系统按距离生成有连接数限制的树状路网，再通过原生车站出口接线，站内轨道参与同一区块的安全网络初始化。站点坐标作为搜索参考，在附近 128／192 格内分级选择较平坦的陆地，以地形中位高度落位；精查高差不超过 8 格，填挖各不超过 5 格，并避开地标及进站通道。小范围基础缺口最多填补 6 格。若贴地后的直连无法通过原有坡度与保护校验，会改试其它原生出口，必要时最多尝试两侧各 512 格的迂回接线。车站会根据所有相邻线路的方向选择原生朝向并统一分配出入口；寻路失败时保留分配的出口改试路径，拒绝强行连接、回勾和过紧弯道。铁路图遍历仅访问已加载区块，边界在区块加载后重试接网，避免主线程等待远端区块生成。开启 `tongdarailway-auto-network.json` 的 `enabled` 后，通过 `critical_sites` 读取雷达站等必要节点，并在首个关键节点附近考虑至多四处独立小站和两处真实规划的矿井地表入口。矿井最多预览十二个结构起点，允许没有合适结果；普通候选可因选址或连接失败被跳过。独立小站使用静态 NBT 小站台。必要站点选址困难时改用小站台，并将有限搜索范围扩到 512 格，保持原有贴地标准。最终节点、连线与模板位置保存在世界的 `data/tongdarailway-auto-network.json` 中，重新进入沿用原决定。设置 `regional: true` 后，按 3×3 个区域（边长 12,288 格）共享规划，默认 28% 的规划区域参与，关键地标所在区域必定参与。主线尝试 4-5 座站，初始长度 8,600-10,000 格，方向由种子决定。粗规划在每站七个侧向候选之间搜索，读取 256 格网格地形高度，综合距离、起伏、低地和转向成本选择走廊。以 40% 的候选概率增加两个侧向站点，尝试回接主线两站形成不规则环线；选址或接线失败可留下开口。关键地标及合适矿井入口就近接入。每秒按主世界玩家位置更新周围 3×3 个大规划区域的预规划队列，中心优先、多人去重，最多保留 64 个待办。单个低优先级线程只计算线路，不请求新区块；实际生成需求绕过预规划队列，有实际需求时暂停启动下一项预规划。已有保存结果直接复用。各系统最多八个节点，单段寻路上限 4096 格。当前仍以规划区域为放置边界，避免独立系统相互侵入。成员区域共享保存结果，实际轨道几何使用最多 32 个系统的工作缓存。设置 `regional: false` 可继续使用有限起始路网模式。本布局请使用新世界测试。默认在世界生成阶段按区块放置轨道并保存曲线连接，DH 预生成也能取得轨道方块。Create 铁路网络初始化只访问已加载区块，损毁段保持缺失。旧生成器模式仍可通过配置选择。

目前使用的三种车站模板已直接修改为静态 NBT：减少灯具，在原有玻璃材质上制作缺损，增加成片坍塌、落地碎料与避开通路的蛛网；保留装饰铁块、机壳、伪装装饰、空储罐及仓储设施，移除合金块、高级显示板与工业站的两个列车驾驶台，保留轨道和原生出口标记。

新生成的线路优先采用短原生转弯与实方块直轨／斜轨组合：较宽的 S 弯拆成两个短 45 度弯和中间的直线段，长变坡分段连接；单条原生曲线端点跨度最多 96 格，无法安全转换的布局会重新寻路。已生成铁路保持原样，此项生成调整请在新世界观察。

原生回退渲染路径中的长曲线按完整曲线的缓存包围范围判断距离，保留 192 格的距离限制，避免经过曲线中段时因远离负责绘制的端点而整段消失。此项只调整客户端显示，可直接用于已生成铁路。

客户端默认启用薄雪外观，可在 `tongdarailway-client.toml` 切换。外观依据轨面附近的原版或 Snow! Real Magic 积雪生成，清除周围积雪后会消失；不修改轨道方块、碰撞、曲线数据或列车逻辑。雪面使用当前资源包的雪纹理，只覆盖下方有支撑的轨道或曲线辅助方块格子，并与世界方块网格对齐，不沿曲线额外延伸。雪面与原版一层积雪高度一致。独立客户端选项 `curveSleeperSnowClearance` 将原生曲线轨枕显示位置抬高 1/64 格，避免与真实薄雪相交，关闭补雪时也生效；修改后需重新进入世界，钢轨及列车数据保持原样；不额外绘制 DH 远景。


### Experimental surface-structure policy / 地表结构对照实验

For fresh regional test worlds, `surface_structure_policy` in `config/tongdarailway-auto-network.json` accepts `off` (default), `avoid`, or `remove`. The choice is saved in the world manifest. The first trial covers vanilla villages, outposts, mansions, temples, huts, igloos and land ruined portals; ocean structures and custom mod placement are excluded. Radar landmarks and underground mines retain their existing rules.

`avoid` previews complete structure layouts without loading remote chunks, reserves their footprints plus construction margins, and routes stations/tracks outside them. `remove` keeps railway paths and rejects an overlapping whole native structure before placement. It does not run a remote footprint survey. The EOW pack selects `remove` for new plans after fresh-world comparison; `avoid` remains an experimental alternative. The standalone fallback is `off`.

新区域测试世界可在 `config/tongdarailway-auto-network.json` 设置 `surface_structure_policy`：`off`（默认）、`avoid`（铁路避让）或 `remove`（取消冲突结构）。选择保存在世界规划清单中。首版覆盖原版村庄、前哨站、林地府邸、神殿、小屋、雪屋和陆地废弃传送门，排除海底建筑；模组自定义选址另行适配；雷达站和地下矿井沿用现有规则。

避让模式预览完整结构布局、预留施工范围并调整线路和车站，不加载远端区块；取消模式保留线路，在原生建筑放置前取消整座冲突结构，无需远方建筑预览。EOW 整合包经新世界对照后为新规划选用 `remove`，`avoid` 保留为实验选项；独立使用时缺省为 `off`。

---

# TongDaRailway Mod 通达铁路

这是一个添加自动生成机械动力铁路的模组

**此模组只维护1.21.1 NeoForge版本，无其他版本适配计划！**

**模组更新修改地形生成时，使用老版本模组的世界在删除已生成的非必要区块前，请勿更新新版本模组**

## 1.1.0 更新路线生成算法

* 路线图大致生成步骤：
* 使用低差异序列在区域内随机均匀的选择站点
* 去除不合法的选点（如海洋上的点）
* 将站点连接成为德劳内三角网
* 删边，使得圆周上的相邻边在特定角度以上
* 删边，使得节点的最大度为4。根据节点度数分配对应出口的车站
* 返回节点和它所连接的所有节点
* 通过匈牙利匹配为每个车站出口匹配一条边
* 根据匹配结果生成连接
* 现在只有某些区域会生成铁路。有些区域则有概率不生成铁路