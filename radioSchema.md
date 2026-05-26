# FHRadio 架构与播放逻辑规范

> 本文档以源码为准，整合外部 `RadioSchema.md` 中的设计信息。

---

## 一、核心数据类型

### 1.1 Sample 体系

```kotlin
sealed interface Sample {
    val type: SampleType       // Track | Stinger | DJ
    val soundName: String
    val sampleLength: Int
    val sampleRate: Int         // 默认 48000 Hz
    val durationMs: Long
    val duration: Duration
    val end: Duration           // 默认 = (sampleLength - 1) * 1000 / sampleRate 毫秒
}
```

| 子类型 | 专属字段 |
|--------|----------|
| `TrackSample` | `displayName`, `artist`, `markers`, `loops`, `bpms` |
| `StingerSample` | `markers`（含 `StartNextTrack`, `End`） |
| `DjSample` | `gameEvent` |

### 1.2 Marker 系统

每个 `TrackSample` 包含一组 `Marker`（单位 samples @ sampleRate），映射为 `Duration?`：

```
TrackStart → DJDrop → TrackDrop →
  TrackLoopStart ═══ [主循环] ═══ TrackLoopEnd →
    DJSegment → PostDrop →
      PostRaceLoopStart ═══ [赛后循环] ═══ PostRaceLoopEnd →
        StingerStart → DJStart → End
```

| Marker | 含义 | 备注 |
|--------|------|------|
| `TrackStart` | 音频真正开始 | 全部 |
| `DJDrop` | DJ 淡出，音乐渐起 | 大部分 |
| `TrackDrop` | 音乐完全进入 | 全部 |
| `TrackLoopStart` | 主循环起点 | 全部 |
| `TrackLoopEnd` | 主循环终点 | 全部 |
| `DJSegment` | DJ 曲中插话（单点） | 大部分 |
| `PostDrop` | 二次突降 | 极少数 |
| `PostRaceLoopStart/End` | 赛后循环 | 极少数 |
| `StingerStart` | 电台标识音起点 | 全部 |
| `DJStart` | DJ 介绍下首歌起点 | 全部 |
| `End` | 歌曲真正结束（覆盖基类 `end`） | 全部 |

Stinger 专用：
| Marker | 含义 |
|--------|------|
| `StartNextTrack` | 切下一首时刻 |
| `End` | Stinger 结束 |

Loop 类型（标记循环区间）：
| 类型 | 起始 | 结束 | 用途 |
|------|------|------|------|
| `TrackMain` | TrackLoopStart | TrackLoopEnd | 自由漫游/赛事 |
| `TrackPostRace` | PostRaceLoopStart | PostRaceLoopEnd | 赛后专用 |

### 1.3 PlayItem 与 PlaySection

```kotlin
sealed class PlayItem {
    abstract val sample: Sample
    abstract val beginAt: Duration       // 播放切入点偏移

    data class Track(override val sample: TrackSample, override val beginAt: Duration = Duration.ZERO) : PlayItem()
    data class Stinger(override val sample: StingerSample, override val beginAt: Duration = Duration.ZERO) : PlayItem()
    data class Dj(override val sample: DjSample, override val beginAt: Duration = Duration.ZERO) : PlayItem()
}

data class PlaySection(
    val track: PlayItem.Track? = null,
    val stinger: PlayItem.Stinger? = null,
    val dj: PlayItem.Dj? = null,
    val solo: Boolean = false,          // true → 先停止所有播放再开始
)
```

**约束**：至少一个字段非 null。Stinger 与 DJ 在 Engine 层面互斥（Engine 只产出三者之一的 Section），但 PlaySection 本身允许共存（用于 Track + Stinger 或 Track + DJ 的组合 Section）。

**`solo` 字段**：仅 `Radio.nextSection()`（用户手动跳过）设为 `true`。`beginSection` 检测到 `solo = true` 时先 `Scheduler.cancel()` + `stopBothPlayer()`。Marker 回调（`continueNext`）产出的 Section 默认 `solo = false`，不影响正在播放的 Track/Stinger 交叉淡出。

### 1.4 PlayerState

```kotlin
data class PlayerState(
    val status: PlaybackStatus,    // Idle | Opening | Buffering | Playing | Paused | Stopped | Ended | Error
    val currentPath: String?,
    val position: Duration,
    val duration: Duration,
    val isMuted: Boolean,
    val volume: Int,
) {
    val isBusy: Boolean            // true = Opening | Buffering | Playing
}
```

### 1.5 PlaybackState（持久化）

用于应用启动时恢复播放位置：
```kotlin
data class PlaybackState(
    val soundName: String?,        // 正在播放的 Sample 的 soundName
    val position: Duration?,       // 播放进度
    val sampleType: PlaybackStateSampleType? = null,  // Track | Stinger | DJ
)
```

---

## 二、播放器架构

### 2.1 双播放器模型

| 播放器 | 用途 | 实例化 |
|--------|------|--------|
| `mainPlayer` | 主播放器，播 Track | `AppRuntime.mainPlayer`（val，自动构造）|
| `secondaryPlayer` | 副播放器，播 Stinger / DJ | `AppRuntime.secondaryPlayer` |

底层 JVM 实现使用 **vlcj 3.x**（VLC Java 绑定），Android/iOS 为 stub。

### 2.2 AudioPlayer 接口

```kotlin
expect class AudioPlayer() {
    var state: PlayerState
    fun play(path: String, beginAt: Duration)
    fun tryPlay(path: String, beginAt: Duration): Boolean   // idle 时才播，返回是否成功
    fun stop()
    fun pause()
    fun resume()
    fun setVolume(volume: Int): Boolean
    fun getVolume(): Int
    fun dispose()
}
```

- `play()`：强制播放，覆盖当前内容
- `tryPlay()`：仅在 `!isBusy` 时播放，用于兜底逻辑避免与交叉淡出冲突
- `setVolume()`：直接操作 VLC 进程在 Windows 音量合成器中的音量
- `getVolume()`：从 `libvlc_audio_get_volume` 读取真实值

### 2.3 音量反相同步

`AppRuntime` 维护两种同步机制：
1. **后台轮询**：`volumeSyncJob` 每 1 秒调 `syncVolumeFromPlayers(false)`，检测 `mainPlayer.getVolume()` 与 `AppSettings.volume` 差异 > 1 则回写（100ms 时间戳守卫，防止覆盖用户拖拽）
2. **设置页帧级同步**：`SettingsScreen` 的 `LaunchedEffect` 每帧调 `syncVolumeFromPlayers(force=true)`，绕过时间戳守卫，实时刷新音量滑块

`AppRuntime.setVolume(volume)` 封装两播放器设置 + 时间戳打点。

---

## 三、Radio 对象（核心调度器）

`Radio` 是单例，管理所有播放逻辑。

### 3.1 播放状态

```kotlin
object Radio {
    val mainPlayer       // → AppRuntime.mainPlayer
    val secondaryPlayer  // → AppRuntime.secondaryPlayer
    var selectedStation: RadioStation?
    var modeEngine: RadioModeEngineV2?

    var trackPlaying: TrackSample?      // 主播放器当前曲目（Compose state）
    var trackBeginInstant: Instant?     // 播放开始时钟
    var trackBeginPos: Duration         // 切入偏移
    val trackCurrentPos: Duration?      // 当前进度 = beginPos + (now - beginInstant)

    var stingerPlaying: StingerSample?  // 副播放器当前 Stinger
    var stingerBeginInstant: Instant?
    var stingerCurrentPos: Duration?

    var djPlaying: DjSample?            // 副播放器当前 DJ
    var djBeginInstant: Instant?
    var djCurrentPos: Duration?

    var currentSection: PlaySection?    // 当前正在播放的 Section
}
```

### 3.2 播放进度追踪

- `beginSample()` 设置 `*Playing` 和 `*BeginInstant`、`*BeginPos`
- `*CurrentPos` 是计算属性，依赖 `Clock.System.now()`，由 UI 帧刷新驱动
- `stopMainPlayer()` / `stopSecondaryPlayer()` 清除对应字段
- UI 层面通过 `currentPos < duration` 判断"播放中"，不依赖显式 `null` 清除

### 3.3 播放状态持久化

`periodicSaveJob` 每秒保存一次：
- 优先级：Track > Stinger > DJ
- 保存 `soundName` + `currentPos` + `SampleType` 到 `PlaybackState`
- 启动时 Engine 的 `resume(playbackState)` 按 `soundName` 匹配恢复

### 3.4 电台生命周期

```kotlin
fun setStation(station: RadioStation?, play: Boolean = true)
```

- `station = null`：关闭电台，停止播放，取消定时器，清除状态
- `station != null`：选中电台 → `selectEngine()` 构造对应 Engine → `resume()` 得出初始 Section → `beginSection()` 开始播放

### 3.5 beginSample()

```
beginSample(playItem, solo=false, player=自动选择, useTryPlay=副播放器默认true)
```

- `solo=true` → `stopBothPlayer()`
- `solo=false` → 仅停止对应播放器（main 或 secondary）
- 根据 `SampleType` 设置对应的 `*Playing`、`*BeginInstant`
- 返回播放是否成功

### 3.6 beginSection()

```
beginSection(section, useTryPlay=false)
```

核心流程：

```
beginSection(section)
  ├── solo? → Scheduler.cancel() + stopBothPlayer()
  ├── currentSection = section
  ├── modeEngine.onSectionStarted(section)    // 同步 Engine 内部状态
  └── when(section):
      ├── Track only:        beginSample(track) → schedule Track.End
      ├── Track + Stinger:   beginSample(track) → schedule Track.StingerStart
      │                      → if crossFade: schedule Stinger.StartNextTrack
      │                      → schedule Stinger.End (useTryPlay=true)
      ├── Track + DJ:        beginSample(track) → schedule Track.DJStart
      │                      → schedule DJ.SampleLength (useTryPlay=true)
      ├── Stinger only:      beginSample(stinger)
      │                      → if crossFade: schedule Stinger.StartNextTrack
      │                      → schedule Stinger.End (useTryPlay=true)
      └── DJ only:           beginSample(dj) → schedule DJ.SampleLength (useTryPlay=true)
```

### 3.7 continueNext()

```
continueNext(useTryPlay) {
    modeEngine.next(section)?.let { beginSection(it, useTryPlay) }
        ?: { 清除 *BeginInstant（让 UI 显示不播放） }
}
```

- 正常链式过渡：Engine 返回下一首 → 递归 `beginSection`
- 链终止（Engine 返回 null）：清除 `*BeginInstant`，不设 `*Playing = null`（UI 通过 `currentPos < duration` 判断）
- `useTryPlay` 由 Marker 回调传入：
  - `Stinger.End` / `DJ.SampleLength` → `true`（避免与 `StartNextTrack` 冲突）
  - `Track.End` / `Stinger.StartNextTrack` → `false`（默认）

### 3.8 nextSection()

```
nextSection() {
    modeEngine.next(currentSection)?.let { beginSection(it.copy(solo = true)) }
}
```

用户手动跳过：`solo = true` → 引擎的下一首 → 停止一切 → 开始播放。

---

## 四、Engine 体系

### 4.1 RadioModeEngineV2 接口

```kotlin
abstract class RadioModeEngineV2(val station: RadioStation) {
    abstract fun next(current: PlaySection?): PlaySection
    abstract fun resume(playbackState: PlaybackState?): PlaySection?
    open fun getPlayList(): Pair<List<PlaySection>, Int?>? = null
    open fun onSectionStarted(section: PlaySection) {}
}
```

- `next(current)`：返回 `current` 的下一首；用于 `continueNext()` 和 `nextSection()`
- `resume(playbackState)`：根据持久化状态返回续播 Section；未匹配则返回初始 Section
- `getPlayList()`：返回完整播放列表 + 当前索引（用于 UI 预览）；`Int?` 为 null 时不高亮
- `onSectionStarted(section)`：Radio 通知 Engine 某 Section 已开始播放（用于同步内部 index）

### 4.2 RandomEngine（完全随机模式）

**构造参数**：`station`, `djSet`, `stingerProbability`, `djProbability`

**核心机制**：

```
预填充队列(4 个 Section) → 每次 next() 取出队首 + 补充新随机 Section
```

- `rollPlaySection()`：**必定有 Track**，按概率独立 roll Stinger / DJ（互斥，同时中则随机二选一），用 `played*Deque`（容量 5）排除最近播放的曲目防重复
- `resume()`：尝试按 `soundName` 匹配已保存的 Sample，匹配到则返回精确续播 PlaySection；未匹配则队列首项附带随机切入点（`randomBeginAt()`）
- `playDequeNext()`：取队首 → `updatePlayed()` → 补充新 Section
- **init**：初始填充 `PLAY_DEQUE_SIZE` 个 Section，每个填充后调 `updatePlayed()` 防重复

**随机切入点 `randomBeginAt()`**：
| 类型 | 逻辑 |
|------|------|
| Track | 60% 概率在 `trackLoopStart * 2` 范围内居中，40% 全时长随机（1 秒安全边界） |
| Stinger | 前 1/4 时长随机 |
| DJ | 前 1/2 时长随机 |

**去重队列**：
| 队列 | 容量 | 作用 |
|------|------|------|
| `playedTrackDeque` | 5 | 排除最近 5 首 Track |
| `playedStingerDeque` | 5 | 排除最近 5 首 Stinger |
| `playedDjDeque` | 5 | 排除最近 5 首 DJ |

**Marker 派发**（在 `beginSection` 中，非 Engine 控制）：
| Marker | 条件 | useTryPlay |
|--------|------|------------|
| `Track.StingerStart` | `stingerProbability` roll 中 | `true`（副播放器） |
| `Track.DJStart` | `djProbability` roll 中 | `true` |
| `Track.End` | 一定 | `false` |
| `Stinger.StartNextTrack` | 一定（随机模式无视 crossFadeEnabled） | `false` |
| `Stinger.End` | 一定 | `true` |
| `DJ.SampleLength` | 一定 | `true` |

---

### 4.3 PlayerEngine（播放器模式）

**构造参数**：`station`, `playMode`, `crossLists`, `maxContinuousTrack/Stinger/DJ`, `patternEnabled`, `patternNodes`

**核心机制**：一次性构建完整 `prebuiltSampleList`，`prebuiltSampleListIndex` 指向当前播放位置（`Int?`，null = 未播放）。

**播放列表推进**：
```kotlin
prebuiltPlayListNext() {
    idx = ((prebuiltSampleListIndex ?: -1) + 1) % list.size
    if (idx == 0) rebuildPlayList()     // 播放完毕自动重建
    prebuiltSampleListIndex = idx
    return list[idx]
}
```

#### 4.3.1 CrossList 模式（非 pattern）

**Shuffle 模式**：
1. 打乱每个 `SampleType` 的列表
2. 放入 `ArrayDeque` pool
3. 加权随机选择 pool（权重 = deque 剩余大小）
4. 连续限制：`maxContinuous*` 控制同类型最大连续数（0 = 无限制）
5. 超限时从其余 pool 加权选

**Order 模式**：`SampleType.entries` 过滤 `crossLists`，`flatMap` 顺序拼接。

#### 4.3.2 Pattern 模式（`patternNodes` 非空）

每个 `PatternNode`：
```kotlin
data class PatternNode(
    val type: SampleType = SampleType.Track,
    val step: Int = 1,             // 相对上次同类型偏移，0 = 重播，负数 = 回退
    val probability: Int = 100,    // 0-100，roll 中才生效
)
```

- 遍历 `patternNodes`，每个 node roll probability → 按 `step` 从对应类型列表取下一首
- 首次遇到某类型固定从 index 0 开始
- `typeIndices` 按类型追踪上一步位置
- 上限：`min(typeLists.values.sumOf { it.size * 3 }, 384)`
- 一整轮所有 node 都未触发（全 0 概率）→ `break` 退出
- pattern 列表为空 → **自动 fallback 到 `buildCrossListPlaylist()`**

#### 4.3.3 onSectionStarted 同步

`beginSection` 入口调 `onSectionStarted(section)`，Engine 按 sample 在 `prebuiltSampleList` 中找到 index 并同步 `prebuiltSampleListIndex`。确保手动选歌、resume 恢复、设置变更重建 Engine 后 index 始终对齐。

---

## 五、Scheduler（定时器）

```kotlin
object Scheduler {
    val jobs: SnapshotStateList<ScheduledJob>    // Compose 可观察

    fun scheduleMarker(tag, delay, block)
    fun cancel(tag, onlyWhenFired=false)
    fun cancel()
}
```

- `scheduleMarker()`：先 `cancel(tag)` 清除同名旧任务，再启动协程 delay → 执行 block → 5 秒后自动清除
- `cancel(tag, onlyWhenFired=true)`：仅清除已触发的任务
- `jobs` 为 `mutableStateListOf`，UI 可观察变化

### ScheduledJob

```kotlin
class ScheduledJob(val job, val tag, val createdAt, val delay) {
    val scheduledAt: Instant     // 触发时刻
    fun remaining(): Duration    // 剩余时间（帧级刷新）
    val hasFired: Boolean
}
```

---

## 六、跨列表播放（crossLists）

用户可选择 Track / Stinger / DJ 的任意组合，控制播放列表包含哪些类型。

- `AppSettings.crossLists: Set<SampleType>`（默认 `{Track}`）
- 持久化为 `crossListsJson` JSON 字符串
- PlayerEngine 构建列表时只用 `crossLists` 中的类型
- Pattern 模式从 `patternNodes` 中的类型自动收集所需列表

---

## 七、配置项一览

| 配置 | 类型 | 默认 | 说明 |
|------|------|------|------|
| `radioMode` | `RadioMode` | `Random` | Random / Seed(未实现) / Player |
| `playMode` | `PlayMode` | `Shuffle` | Shuffle / Order（仅 Player 模式） |
| `stingerProbability` | `Int` 0-100 | `10` | Stinger 插入概率 |
| `djProbability` | `Int` 0-100 | `1` | DJ 插入概率 |
| `patternEnabled` | `Boolean` | `false` | 启用循环模式（仅 Order 模式） |
| `patternNodes` | `List<PatternNode>` | — | 循环模式节点列表 |
| `crossFadeEnabled` | `Boolean` | `true` | Stinger 交叉淡出（用 StartNextTrack 提前切歌） |
| `maxContinuousTrack` | `Int` | `0` | Shuffle 模式最大连续 Track 数（0 = 不限） |
| `maxContinuousStinger` | `Int` | `1` | Shuffle 模式最大连续 Stinger 数 |
| `maxContinuousDj` | `Int` | `1` | Shuffle 模式最大连续 DJ 数 |
| `volume` | `Int` 0-100 | `100` | 主音量 |
| `autoResume` | `Boolean` | `false` | 启动后自动续播 |

所有设置通过 `SettingMutableState` 代理，变更时自动保存到 `RadioSettings` 并触发 `Radio.reset()` 重建 Engine。

---

## 八、state 管理模式

### SettingMutableState

```kotlin
class SettingMutableState<T>(initial, vararg onChanged, onSet)
```

- 行为类似 `mutableStateOf`，额外支持：
  - `onChanged`：值变更时回调（如 `Radio::reset`）
  - `onSet(old, new)`：值变更后回调（如 `saveRadioSettings`）

### AppSettings

所有设置项使用 `SettingMutableState`，自动持久化和通知。

```kotlin
object AppSettings {
    var radioMode
    var playMode
    var stingerProbability
    var djProbability
    var crossLists        // 解析自 crossListsJson
    var maxContinuousTrack / Stinger / DJ
    var patternEnabled
    var patternNodes      // 解析自 patternJson
    var crossFadeEnabled
    var autoResume
    var volume
    // ...
}
```

---

## 九、音频文件绑定

### 目录结构

```
根目录/
└── 电台名/
    ├── Track/
    │   ├── sound_0.wav          ← 按 soundName 排序后的第 0 首
    │   ├── sound_1.wav          ← 按 soundName 排序后的第 1 首
    │   └── ...
    ├── Stinger/
    │   ├── sound_0.wav          ← 按列表顺序（stingers[0]）
    │   ├── sound_1.wav
    │   └── ...
    └── DJ/
        ├── sound_0.wav          ← 按列表顺序（djSamples[0]）
        ├── sound_1.wav
        └── ...
```

### 匹配规则

所有三种类型均使用 `sound_{idx}` 顺序命名：

- **Track**：`sortedTracks` = `tracks.sortedBy { it.soundName }`，`idx` 为排序后的位置
- **Stinger**：`idx` 为 `stingers` 列表中的原始索引
- **DJ**：`idx` 为 `djSamples` 列表中的原始索引

路径构造：`{电台名}/{Track|Stinger|DJ}/sound_{idx}`，自动尝试 `.wav` → `.flac` → `.mp3` → `.opus` 扩展名。
