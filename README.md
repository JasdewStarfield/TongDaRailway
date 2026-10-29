# TongDaRailway - EOW fork

This independent GPL-3.0 fork is based on [Hello-LuckyHuang/TongDaRailway](https://github.com/Hello-LuckyHuang/TongDaRailway), upstream commit `2df7dd5`. Original authors and license are retained. EOW-specific modifications by Jasdew Starfield, 2026-10-29.

The fork defaults to fixed, seed-bound routes for abandoned infrastructure. It retains the original route, vertical-profile, roadbed, bridge, tunnel and Create track implementations. It does not include EOWCore source code.

- Set `fixedNodeMode = true` in the common configuration and supply `config/tongdarailway-fixed-nodes.json` before creating a new world.
- With no matching node file or seed, no random railway network is generated.
- Each node supplies `id`, `rail` XYZ, horizontal `facing`, `protected_center` XYZ and `protected_radius`. Links supply `from` and `to` node IDs. The root has `format: 1`, a world `seed`, `nodes`, and `links`.
- Node ports must be outside protected building/shaft footprints. Small loading pads are placed beside the track ends. The prototype accepts at most eight nodes, four links and 4096-block links.
- Short missing-track intervals are fixed by route identity and seed. Some intervals also omit the central bridge span or roadbed. Planned damage avoids the first and last 80 blocks. Entire curves crossing a gap are omitted, so actual missing rail spans can be longer than the short damage interval.
- Roadbeds are placed during generation. Tracks finish near players only when endpoint neighbourhoods are already loaded. At most two placers and normally 32 track records finish per server tick. The temporary placer becomes cobblestone once; damaged tracks are excluded from its saved work list.
- Changing anchors is intended for new worlds. Automatic discovery/selection of EOW structures is not implemented; supply frozen endpoint coordinates in the external node file. Upstream and fork share the `tongdarailway` mod ID and must not be installed together.

Build with Java 21: `./gradlew build --no-daemon --no-configuration-cache`. This fork targets NeoForge 21.1.262 and Create 6.0.10. Upstream Java files retain their GBK encoding.

## EOW 分支

本分支独立维护，保留上游作者与 GPL-3.0 授权。默认使用创建世界前提供的固定节点文件，节点带世界种子校验。节点附近保留建筑／矿井保护范围，并生成小型装卸平台。线路沿用上游路基、桥隧和 Create 曲线轨道，加入持久化的短缺轨段与部分路基缺口。

当前节点需从外部配置提供，尚未实现自动寻找雷达站与矿井。修改节点后应新建测试世界。轨道延后放置只访问已加载区块，损毁段不进入待铺轨清单。

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