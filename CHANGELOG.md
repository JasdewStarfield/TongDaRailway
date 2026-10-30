# Changelog / 更新记录

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
