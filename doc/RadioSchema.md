# FHRadio 架构与播放逻辑

## 一、核心数据类型

### 1.1 Sample 体系

```kotlin
sealed interface Sample {
    val type: SampleType       // Track | Stinger | DJ
    val soundName: String
    val sampleLength: Int      // 单位 samples
    val sampleRate: Int        // 默认 48000 Hz
    val durationMs: Long
    val duration: Duration
    val end: Duration           // (sampleLength - 1) * 1000 / sampleRate 毫秒
    var parentStation: RadioStation?  // 跨电台引用（虚拟电台用）
}
```

| 子类型             | 专属字段                                                |
|-----------------|-----------------------------------------------------|
| `TrackSample`   | `displayName`, `artist`, `markers`, `loops`, `bpms` |
| `StingerSample` | `markers`（含 `StartNextTrack`, `End`）                |
| `DjSample`      | `gameEvent`（游戏事件标签）                                 |

### 1.2 Marker 系统

Track 的 `Marker`（单位 samples，`Duration?` 访问器）：

```
TrackStart -> DJDrop -> TrackDrop ->
  TrackLoopStart ═══ [主循环] ═══ TrackLoopEnd ->
    DJSegment -> PostDrop ->
      PostRaceLoopStart ═══ [赛后循环] ═══ PostRaceLoopEnd ->
        StingerStart -> DJStart -> End
```

| Marker                  | 含义               |
|-------------------------|------------------|
| `TrackStart`            | 音频真正开始           |
| `DJDrop`                | DJ 淡出，音乐渐起       |
| `TrackDrop`             | 音乐完全进入           |
| `TrackLoopStart/End`    | 主循环区间            |
| `DJSegment`             | DJ 曲中插话          |
| `PostRaceLoopStart/End` | 赛后循环             |
| `StingerStart`          | 电台标识音起点          |
| `DJStart`               | DJ 语音起点          |
| `End`                   | 音频结束（覆盖基类 `end`） |

Stinger 专用：`StartNextTrack`（切歌点）、`End`（结束）

DJ 无独立 marker，使用 `sampleLength` 推算 `end`

Loop 类型：

| 类型 | 起始 Marker | 结束 Marker |
|------|-------------|-------------|
| `TrackMain` | TrackLoopStart | TrackLoopEnd |
| `TrackPostRace` | PostRaceLoopStart | PostRaceLoopEnd |

### 1.3 PlayItem 与 PlaySection

```kotlin
sealed class PlayItem {
    abstract val sample: Sample
    abstract val beginAt: Duration

    data class Track(override val sample: TrackSample, override val beginAt: Duration = Duration.ZERO): PlayItem()
    data class Stinger(override val sample: StingerSample, override val beginAt: Duration = Duration.ZERO): PlayItem()
    data class Dj(override val sample: DjSample, override val beginAt: Duration = Duration.ZERO): PlayItem()
}

data class PlaySection(
    val track: PlayItem.Track? = null,
    val stinger: PlayItem.Stinger? = null,
    val dj: PlayItem.Dj? = null,
    val solo: Boolean = false,
)
```

- 至少一个字段非 null
- Engine 产出的 Section 中 Stinger/DJ 互斥
- `solo = true`：仅用户手动跳歌（`Radio.nextSection()`），`beginSection` 先 `Scheduler.cancel() + stopBothPlayer()`
- Marker 回调 `continueNext()` 产出的 Section 默认 `solo = false`

### 1.4 PlayState / PlayerState

```kotlin
data class PlayerState(
    val status: Status,       // Idle | Opening | Buffering | Playing | Paused | Stopped | Ended | Error
    val currentPath: String?,
    val position: Duration,
    val duration: Duration,
    val isMuted: Boolean,
    val volume: Int,
) {
    val isBusy: Boolean       // Opening | Buffering | Playing
}
```

### 1.5 PlaybackState（持久化续播）

```kotlin
data class PlaybackState(
    val soundName: String?,
    val position: Duration?,
    val sampleType: SampleType?,
)
```

---

## 二、播放器架构

### 2.1 双播放器模型

| 播放器               | 用途           | 实例                           |
|-------------------|--------------|------------------------------|
| `mainPlayer`      | Track        | `AppRuntime.mainPlayer`      |
| `secondaryPlayer` | Stinger / DJ | `AppRuntime.secondaryPlayer` |

Desktop 使用 **vlcj 4.11.0 + VLC 3.0.x**Android/iOS 为 stub/TODO

### 2.2 AudioPlayer 接口

```kotlin
expect class AudioPlayer(tag: String) {
    var state: PlayerState
    fun play(path: String, beginAt: Duration)
    fun tryPlay(path: String, beginAt: Duration): Boolean
    fun stop()
    fun pause()
    fun resume()
    fun setVolume(volume: Int): Boolean
    fun getVolume(): Int
    fun setPreamp(db: Float)
    fun dispose()
}
```

- `play()`：强制播放
- `tryPlay()`：仅在 `!isBusy` 时播放，用于 Stinger.End / DJ 兜底，避免与交叉淡出冲突
- `setPreamp(db)`：Desktop 用 VLC Equalizer 预放大（per-player 增益，范围 -20~+20 dB）；Android 转为 `volumeScale` 乘数；iOS
  stub

### 2.3 音量 / 音频闪避

**`audioDucking` 开关**（`RadioSettings.audioDucking`）：

| 平台            | 默认值     | 原因                                        |
|---------------|---------|-------------------------------------------|
| Desktop (JVM) | `false` | VLC 两个播放器共享同一 WASAPI 会话，`setVolume` 会互相覆盖 |
| Android       | `true`  | ExoPlayer 各实例音量独立                         |
| iOS           | `true`  | 预留                                        |

**DJ 音量压低流程**（当 `audioDucking = true`）：

```
Track.DJStart -> beginSample(DJ) -> djActive=true -> fadeVolume(mainPlayer, 100%, 50%)
    ↓
Track.End -> continueNext() -> 下一首 Track 开始（主播放器音量保持 50%）
    ↓
DJ.End -> fadeVolume(mainPlayer, 50%, 100%) -> djActive=false
```

- `fadeVolume(player, from, to, duration=500ms)`：60fps 线性插值过渡，避免突变
- `djActive` 为 true 期间：`AppRuntime.setVolume(v)` 主播放器实际音量 = v / 2
- `syncVolumeFromPlayers` 在 `djActive=true` 时跳过，防止减半值写回 `AppSettings.volume`
- 切台/关闭电台时 `stopSecondaryPlayer()` 立即重置 `djActive=false` 并恢复音量

**平台差异总结**：

|                 | Desktop                   | Android                     |
|-----------------|---------------------------|-----------------------------|
| 多播放器音量          | **共享**同一个 WASAPI 会话       | **独立** ExoPlayer.volume     |
| audioDucking 默认 | `false`（不压低，避免冲突）         | `true`（压低有效）                |
| 音量同步            | VLC `getVolume()` 每 1 秒回读 | 不需要（`needVolumeSync=false`） |
| 音量映射            | cbrt 补偿 VLC 三次方曲线         | 线性 0~1                      |

### 2.4 音量同步

`syncVolumeFromPlayers`：每 1 秒（桌面端）或每帧（设置页）读取 `mainPlayer.getVolume()`：

- `vol <= 0` -> 拒绝（VLC 过渡状态短暂 0 值）
- 100ms 时间戳守卫避免覆盖用户拖拽
- `djActive=true` 时跳过

---

## 三、Radio 对象（核心调度器）

### 3.1 播放状态

```kotlin
object Radio {
    var selectedStation: RadioStation?
    var modeEngine: RadioModeEngineV2?

    // PlaySlot<T> 封装 playing + beginInstant + beginPos
    // currentPos 从 AudioPlayer.state 读取，displayPos 从 Clock 推算
    var trackSlot: PlaySlot<TrackSample>
    var stingerSlot: PlaySlot<StingerSample>
    var djSlot: PlaySlot<DjSample>

    var djActive: Boolean           // DJ 正在播放（音量压低标记）
    var currentSection: PlaySection?
}
```

### 3.2 电台生命周期

```
setStation(station, play=true)
  ├── station=null -> 关闭电台（cancel Scheduler, stopBothPlayer, 清状态）
  └── station≠null -> selectEngine() -> Engine.resume() -> beginSection()
```

`reset()`：仅重建 Engine + 同步 `onSectionStarted`，不中断播放

### 3.3 beginSection() — 核心播放调度

```
beginSection(section, useTryPlay=false)
  ├── solo? -> Scheduler.cancel() + stopBothPlayer()
  ├── 根据 section 类型分支:
  │
  ├── Track only:
  │     beginSample(track) -> schedule Track.End -> continueNext()
  │
  ├── Track + Stinger:
  │     beginSample(track)
  │     schedule Track.StingerStart:
  │       beginSample(stinger)
  │       schedule Stinger.StartNextTrack -> continueNext()
  │       schedule Stinger.End -> continueNext(useTryPlay=true)
  │
  ├── Track + DJ:
  │     beginSample(track)
  │     schedule Track.DJStart:
  │       beginSample(dj)
  │       if audioDucking: djActive=true, fadeVolume(mainPlayer, 100->50)
  │       schedule DJ.End:
  │         if audioDucking: fadeVolume(mainPlayer, 50->100), djActive=false
  │         djSlot = PlaySlot()
  │     schedule Track.End -> continueNext()   ← DJ 不阻断 Track 连播
  │
  ├── Stinger only:
  │     beginSample(stinger)
  │     schedule Stinger.StartNextTrack -> continueNext()
  │     schedule Stinger.End -> continueNext(useTryPlay=true)
  │
  └── DJ only:
        beginSample(dj)
        if audioDucking: djActive=true
        schedule DJ.End:
          if audioDucking: djActive=false
          djSlot = PlaySlot()
          continueNext()
```

**关键变化**：Track+DJ 中 **Track.End 和 Track.DJStart 同时派发**，Track 结束时立即 `continueNext()`，不等 DJ 播完DJ 跨越
Section 边界延续到下一首 Track 播放过程中与游戏实际行为一致

### 3.4 `continueNext()` vs `nextSection()`

|        | `continueNext()` | `nextSection()`  |
|--------|------------------|------------------|
| 触发来源   | Marker 回调        | 用户点击 "下一首"       |
| `solo` | `false`          | `true`           |
| 行为     | 链式过渡，不中断当前播放     | 停止一切后开始新 Section |

### 3.5 播放状态持久化

`periodicSaveJob` 每秒保存：优先级 Track > Stinger > DJ存储到 `PlaybackState`，启动时 Engine 的 `resume()` 按 `soundName`
恢复

---

## 四、Scheduler（Marker 调度器）

```kotlin
object Scheduler {
    val jobs: SnapshotStateList<Job>
    fun scheduleMarker(tag, triggerPosition, player, block)
    fun cancel(tag);fun cancel(vararg tags)
    fun cancel()
}
```

- **轮询驱动**：协程每 50ms 检查各 player 的 `state.position >= job.triggerPosition`
- 同一 tick 内按 `triggerPosition` 升序执行，保证顺序
- 先快照再触发，回调中可安全新增/取消 marker
- `scheduleMarker()` 先 `cancel(tag)` 清同名旧任务
- `Ended` / `Error` 状态回退触发所有剩余 marker
- `Stopped` **不触发**回退（Player.stop() 在 Section 间过渡时会置 Stopped）

---

## 五、Engine 体系

### 5.1 RadioModeEngineV2 接口

```kotlin
abstract class RadioModeEngineV2(val station: RadioStation) {
    abstract fun next(current: PlaySection?): PlaySection
    abstract fun resume(playbackState: PlaybackState?): PlaySection?
    open fun getPlayList(): Pair<List<PlaySection>, Int?>? = null
    open fun onSectionStarted(section: PlaySection) {}
}
```

### 5.2 RandomEngine（完全随机模式）

- **构造参数**：`station`, `stingerProbability`, `djProbability`, `djGameEvents`, `excludedTrackSuffixes`
- 预填充 4 个 Section -> `next()` 取队首 + 补充新随机 Section
- Stinger/DJ 按概率独立 roll，同时命中则 `Random.nextBoolean()` 二选一
- 去重：`playedDeque`（容量 5）排除最近播放的样本
- `resume()`：按 `soundName` 精确匹配；未匹配则附随机切入点权重分布

### 5.3 SeedEngine（种子控制模式）

**目标**：相同种子 + 相同墙钟时间 -> 任何设备播放相同音频

```kotlin
class SeedEngine(
    station: RadioStation,
    seed: Long,                       // 由 parseSeed() 从用户输入计算
    excludedTrackSuffixes: Set<String>,
)
```

### 5.4 PlayerEngine（播放器模式）

- 一次性构建完整播放列表（Shuffle 加权随机 / Order 顺序 / Pattern 循环模式）
- `prebuiltSampleListIndex` 指针推进，到头自动重建
- 支持 `crossLists` 跨列表组合、`maxContinuous*` 连续限制

**构造常量**：

| 常量 | 值 | 说明 |
|------|-----|------|
| `STINGER_PROB` | 60 | 每节必定有 Stinger/DJ 其一，概率比 60/40 |
| `DJ_PROB` | 40 | |
| `PLAYLIST_CYCLES` | 100 | 100 轮 × trackCount = ~2500 节 |

**DJ_ALLOWED_EVENTS**（硬编码，事件无关的 DJ）：

```
Aftermarket1, Ambassador1-4, AutoDrive1, Garage1-2,
LinkSkill1, CampaignFestivalNew1-6
```

**播放列表生成**：

1. 每轮 cycle 用 `hash64(seed, cycle)` 做 Fisher-Yates shuffle 打乱 track 顺序
2. 每节 sectionIndex 用 `Random(hash64(seed, sectionIndex))` 独立决定 Stinger/DJ 及样本选择
3. **节时长**：
    - 纯 Track -> `track.end`
    - Track + Stinger -> `stingerStart + startNextTrack`
    - Track + DJ -> `track.end`（DJ 跨节，不计入）
4. 累加 `startTimeMs` + `durationMs` -> `totalDurationMs`

**续播 (resume)**：

- **忽略 `PlaybackState`**，始终以 `wallClockMs % totalDurationMs` + 二分查找定位
- 计算节内 Track、Stinger、DJ 各自的 `beginAt` 偏移
- 写入 `currentSectionIndex` 用于后续 `next()`

**下一首 (next)**：

- `currentSectionIndex = (idx + 1) % size`，不依赖 soundName 匹配
- 直接取 `sections[index]` 构建 `PlaySection`

**hash64**：SplitMix64 风格确定性混合（负数常量适配 Kotlin Long 有符号范围）

## 八、电台目录结构

```
音频根目录/
└── 电台名/
    ├── Track/
    │   └── sound_{idx}.{wav|flac|mp3|opus}   ← idx = soundName 排序后位置
    ├── Stinger/
    │   └── sound_{idx}.{ext}                   ← idx = 原始列表位置
    └── DJ/
        └── sound_{idx}.{ext}                   ← idx = 原始列表位置
```

路径解析：`RadioStation.resolvePath(sample)` 按电台名 + 类型目录 + soundName 构造，自动尝试多种扩展名
