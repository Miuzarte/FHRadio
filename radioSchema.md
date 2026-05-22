# FHRadio 播放逻辑规范

## 一、音频标记系统 (Marker System)

每首歌 (`TrackSample`) 包含一组精密的时间标记（单位: samples @48000Hz），定义了歌曲结构和过渡节点。

```
时间线: VeryStart → TrackStart → DJDrop → TrackDrop
  → ═══ TrackLoopStart ═══ [主循环] ═══ TrackLoopEnd
  → DJSegment
  → PostDrop → ═══ PostRaceLoopStart ═══ [赛后循环] ═══ PostRaceLoopEnd
  → StingerStart → DJStart → End
```

### Marker 说明

| Marker            | 含义                        | 启用状态      |
|-------------------|---------------------------|-----------|
| VeryStart         | 极早起始点                     | 仅少量歌曲启用   |
| TrackStart        | 音频真正开始                    | 全部        |
| DJDrop            | DJ淡出, 音乐开始渐起              | 大部分 (非-1) |
| TrackDrop         | 音乐完全进入 (前奏结束，人声/高潮进入)     | 全部        |
| TrackLoopStart    | 主循环区间起点 (自由漫游/赛事中循环播放的起点) | 全部        |
| TrackLoopEnd      | 主循环区间终点                   | 全部        |
| DJSegment         | DJ曲中插话位置 (单点，非区间)         | 大部分 (非-1) |
| PostDrop          | 音乐二次突降 (用于过渡到赛后模式)        | 极少数启用     |
| PostRaceLoopStart | 赛后循环起点 (赛事结束后播放的专用循环)     | 只有几首启用    |
| PostRaceLoopEnd   | 赛后循环终点                    | 只有几首启用    |
| StingerStart      | 电台标识音播放起点                 | 全部        |
| DJStart           | DJ 开始介绍下首歌                | 全部        |
| End               | 歌曲真正结束                    | 全部        |

### Stinger 专用 Marker

每个 Stinger 也有独立的标记:

| Marker         | 含义         |
|----------------|------------|
| StartNextTrack | 开始下一首曲目的时刻 |
| End            | Stinger 结束 |

### Loop 类型

| Loop 类型       | 起始 Marker         | 结束 Marker       | 用途        |
|---------------|-------------------|-----------------|-----------|
| TrackMain     | TrackLoopStart    | TrackLoopEnd    | 自由漫游/赛事循环 |
| TrackPostRace | PostRaceLoopStart | PostRaceLoopEnd | 赛后专用循环    |

### 预留 Marker (全部 -1，当前未启用)

- Section1-5: 动态段落切换
- BinkTransition: Bink 视频过渡交叉淡入淡出点
- Loop1-5: 扩展循环槽位

---

## 二、BPM 数据

- 每首歌 1-2 条 BPM 条目
- 两条 BPM 值相同: 工具对前后半段独立检测，结果一致
- 一条 BPM: 歌曲全程无变奏，工具跳过第二轮检测
- 精确到小数点后 4 位 (如 128.0000)

---

## 三、播放列表 (PlayList)

每个电台有 3 个播放列表:

| 类型           | 用途      | 当前实现  |
|--------------|---------|-------|
| FreeRoam     | 自由漫游播放  | ✅ 已实现 |
| Event        | 赛事播放    | ✅ 已实现 |
| ShortStinger | 标识音播放列表 | 未实现   |

播放列表中的条目是 SoundName 列表，定义了曲目的播放顺序。播放时按列表顺序获取对应的 TrackSample。

---

## 四、播放逻辑设计

### 4.1 纯随机模式 (当前实现)

1. 用户在电台页点击电台 → 选中电台 + 随机播放一首曲目
2. 随机起始位置: `Random.nextLong(track.durationMs)` 模拟"中途切台"感
3. 不处理 Marker 循环，让整首歌自然播完
4. 曲目播完 (`finished` / `stopped` / `error`) 后不做自动切歌

### 4.2 拟真模式 (待实现)

完整的电台模拟流程:

```
1. 切台 → 播放 Stinger (电台标识音) + 交叉淡入下一首歌
2. 歌曲播放 → 从 TrackDrop 开始 (跳过前奏的 DJ 介绍)
3. 主循环 → 在 TrackLoopStart ~ TrackLoopEnd 区间循环
4. 曲中 DJ 插话 → 检查 DJSegment 标记，根据 djProbability 概率插入 DJ 语音
5. 即将结束 → 检查 StingerStart 位置，根据 stingerProbability 概率插入 Stinger
6. DJ 发音结束 (End / StartNextTrack) → 从下一首歌的 TrackDrop 衔接
```

### 4.3 插入概率控制

| 设置         | 范围     | 默认  | 含义                 |
|------------|--------|-----|--------------------|
| Stinger 概率 | 0-100% | 10% | 每首歌结束时插入电台标识音的概率   |
| DJ 概率      | 0-100% | 1%  | 每个 DJ 插话点实际插入语音的概率 |

- 概率按百分比计算: `Random.nextInt(100) < probability` 则触发

### 4.4 DJ 时间间隔

- XML 中 `TimeBetweenMidTrackDJLines="120"` 表示 DJ 曲中插话最小间隔 120 秒
- 一次播放中多次插话需间隔 ≥ 此值

---

## 五、音频文件绑定

### 目录结构约定

```
选中的根目录/
├── Gacha City Radio/
│   ├── Track/
│   │   ├── HZ6_R6_SongName1_Artist1.wav
│   │   └── ...
│   ├── DJ/
│   │   ├── 1.wav
│   │   └── 2.wav
│   ├── Stinger/
│   │   ├── 1.wav
│   │   └── 2.wav
├── 其他电台// ...
```

### 匹配规则

- Track: 按 `soundName` 精确匹配文件名 (不含扩展名)
- DJ / Stinger: 按列表顺序自然排序 (数字前缀: 1,2,...10,11 -- 非字典序)
- 匹配失败: DJ/Stinger 跳过整个列表 (数量必须严格匹配)，Track 跳过单首
- 扫描完成输出 snackbar 汇总

---

## 六、Player 状态机

```
Idle ──play()──▶ Opening ──▶ Buffering ──▶ Playing
  ▲                 │              │             │
  │    ┌────────────┘              │             │
  │    ▼                           ▼             ▼
  ├── Error ◀── error()          Paused ◀── pause()
  │                                      │  resume()
  │                                      ▼
  │                                   Playing
  │
  ├── Ended ◀── finished()
  │
  └── Stopped ◀── stop()

finished/stopped/error: 清空 currentPath + durationMs → auto-deselect
```
