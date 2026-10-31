# Changelog / 更新记录

## 1.1.5-eow.33 — 2026-11-01

- Reject overlapping mod surface buildings, including Structory raw-generation towers, while preserving deep raw ruins.
- 排除与铁路重叠的模组地表建筑，包含 Structory 原始生成阶段塔楼，并保留深埋遗迹。

## 1.1.5-eow.32 — 2026-10-31

- Place radar-linked stations near the actual rotated gate, considering native station rotation together with proximity. Preserve dry footing, bounded cut/fill and structure clearance. Pedestrian road generation is not included.
- 雷达站关联车站优先靠近实际旋转后的院门，结合原生车站朝向选择近距离位置，保留干燥地基、填挖量和建筑避让限制。本版不包含接驳道路生成。

## 1.1.5-eow.30 — 2026-10-31

- Clear ice cuts from original terrain across wall slots and trim narrow exposed edge remnants, retaining broad ice banks and the railway floor.
- 冰雪切口按原始地形清顶，覆盖侧墙位置并修整边缘细柱，保留较宽冰体和铁路路基。

## 1.1.5-eow.29 — 2026-10-31

- Make ice railway cuts open to the sky even when an overhead ice arch remains connected to its banks. Retain the excavation width, roadbed and ordinary rock tunnels.
- 冰雪铁路切口统一清理上方顶盖，不再保留与两侧相连的冰拱；沿用开挖宽度，保留路基与普通岩石隧道。

## 1.1.5-eow.28 — 2026-10-31

- Break abandoned bridge decks, railings and supports across their full width with staggered edges tied to the saved damage intervals. Preserve natural ice walls and roof when cutting railway corridors through ice, while retaining the roadbed and ordinary rock tunnel lining. Clear exposed frozen caps above narrow cuts without side supports.
- 废弃断桥的桥面、两侧栏杆与支撑一并断开，断口按已保存损毁区间形成错落边缘。铁路穿过冰体时保留天然冰壁和顶部，保留路基及普通岩石隧道内衬；缺少侧向支撑的薄冰切口会清理上方冰雪顶盖。

## 1.1.5-eow.27 — 2026-10-31

- Refresh cached visual track snow after skylight/block-light updates, and combine the rail-cell and exposed-air light samples while preserving dark tunnels and normal day/night lighting.
- 补雪网格响应天空光和方块光更新，合并轨道格与上方空气格的采样，保留洞内明暗和正常昼夜变化。

## 1.1.5-eow.26 — 2026-10-31

- Add saved per-world surface-building policies: avoid footprints or cancel whole overlapping structures before placement. Initial coverage includes vanilla land buildings; underground mines and strategic landmarks retain their existing rules.
- 增加按世界保存的地表建筑处理模式：铁路避让建筑范围，或在放置前取消整座冲突结构。首版覆盖原版陆地建筑；地下矿井与战略节点沿用原规则。

## 1.1.5-eow.25 — 2026-10-31

- Allow worlds to start when a configured strategic site has no plan or its integration query fails. Skip its station, retain ordinary regional density, and log the site, world, seed, configuration state and exception details.
- 雷达站等战略节点缺少规划或接口查询失败时，跳过关联站点并继续启动世界；普通区域保持原有概率，日志记录节点、世界、种子、配置状态及异常详情。

## 1.1.5-eow.24 — 2026-10-31

- Add seed-driven abandoned railway systems with terrain-guided main lines, optional return branches and loops, and stations near strategic landmarks or suitable mine entrances.
- Queue planning for the 3×3 large planning areas around players, reuse saved routes, and avoid loading remote chunks during planning and native track-graph discovery.
- Place rails during chunk generation, shorten long native curves, and repair graph connections when both endpoints become available.
- Ground stations and adapt their native entrances to route bearings. Add damaged bridges, tunnel collapses and surface failures, with missing rails aligned to physical damage.
- Age the bundled station templates with broken glazing, roof damage and cobwebs, while removing selected high-value blocks and train controls.
- Add client-only visual track snow and curve sleeper clearance, preserving track blocks, collision and train behavior.

- 增加由种子确定的废弃铁路系统：按地形选择主线走廊，可生成回接支线及环线，并在战略地标和适宜矿井入口附近设置站点。
- 提前排队规划玩家周围 3×3 个大规划区域，复用已保存线路；规划及原生铁路图查询不主动加载远端区块。
- 在区块生成阶段放置轨道，将过长原生曲线拆短，并在端点可用后补接铁路网络。
- 车站贴地落位，原生出入口按线路方向分配；断桥、隧道塌方和地表损毁与缺轨范围对应。
- 为车站模板增加破窗、屋顶破损和蛛网，移除部分高价值方块及列车驾驶台。
- 增加纯客户端轨道覆雪和曲线枕木避雪显示，保持轨道方块、碰撞和列车功能。
