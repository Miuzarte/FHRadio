# USAGE

## 文件准备

- 安装好的[《极限竞速：地平线 6》](https://store.steampowered.com/app/2483190)
- [Fmod-Bank-Tools](https://github.com/Wouldubeinta/Fmod-Bank-Tools)

### RadioInfo.xml

位于 `ForzaHorizon6\media\Audio\RadioInfo_*.xml`,
语言地区代码根据需要的语言进行选择,
比如不喜欢中文 (CN) 的台呼 CV 语音可以选择英文 (EN),
最好能跟后续的 `FMODBank` 选择匹配

### 解包音频

1. 下载并解压 [Fmod-Bank-Tools/releases](https://github.com/Wouldubeinta/Fmod-Bank-Tools/releases)
2. 将 `ForzaHorizon6\media\Audio\FMODBanks\` 下需要的 `.bank` 文件 `移动` 或 `复制` 到 `Fmod_Bank_Tools\bank\` 内
      - 具体电台名对电台 ID 的映射需要查看 `RadioInfo_*.xml`, 可以跳过不需要的电台, 下列给出 FH6 的映射
        - 1: Horizon Pulse
        - 2: Horizon Bass Arena
        - 3: Horizon Block Party
        - 4: Horizon XS
        - 5: Hospital Records
        - 6: Gacha City Radio
        - 7: Sub Pop Records
        - 8: Horizon Wave
        - 9: Horizon Opus
      - Track:
        - 在 `FMODBanks\` 内搜索 `R*_Tracks_*.assets.bank`
        - 右键空白处 -> 查看 -> 详细信息, 然后按大小降序排序
        - 将 `R1_Tracks_*.assets.bank` ~ `R9_Tracks_*.assets.bank` (后缀存在 `CU1`, `Disk` 等, 只要是大小不低于 `1MB` 的) 复制到 `Fmod_Bank_Tools\bank\`, 下列以 FH6 为例
          - 1: R1_Tracks_CU1.assets.bank
          - 2: R1_Tracks_Disk.assets.bank
          - 3: R2_Tracks_CU1.assets.bank
          - 4: R2_Tracks_Disk.assets.bank
          - 5: R3_Tracks_CU1.assets.bank
          - 6: R4_Tracks_CU1.assets.bank
          - 7: R5_Tracks_Disk.assets.bank (R5 的曲目在 `_Disk` 内)
          - 8: R6_Tracks_CU1.assets.bank
          - 9: R7_Tracks_CU1.assets.bank
          - 10: R8_Tracks_CU1.assets.bank
          - 11: R9_Tracks_CU1.assets.bank
          - (btw R10 里只有一首, 是游戏启动到主菜单的音乐)
      - Stinger: (建议不跳过)
        - 搜索 `R*_Stingers_CN.assets.bank`, 语言地区代码最好匹配前面选择的 `RadioInfo_*.xml`, 例如 `R*_Stingers_EN`
        - 复制 `R1_Stingers_*.assets.bank` ~ `R9_Stingers_*.assets.bank`
          - 1: R1_Stingers_CN.assets.bank
          - 2: R2_Stingers_CN.assets.bank
          - 3: R3_Stingers_CN.assets.bank
          - 4: R4_Stingers_CN.assets.bank
          - 5: R5_Stingers_CN.assets.bank
          - 6: R6_Stingers_CN.assets.bank
          - 7: R7_Stingers_CN.assets.bank
          - 8: R8_Stingers_CN.assets.bank
          - 9: R9_Stingers_CN.assets.bank
      - DJ: (可以跳过)
        - 搜索 `VO_DJ_*_CN.assets.bank`
        - 复制 `VO_DJ_01_CN.assets.bank` ~ `VO_DJ_09_CN.assets.bank`
          - 1: VO_DJ_01_CN.assets.bank
          - 2: VO_DJ_02_CN.assets.bank
          - 3: VO_DJ_03_CN.assets.bank
          - 4: VO_DJ_04_CN.assets.bank
          - 5: VO_DJ_05_CN.assets.bank
          - 6: VO_DJ_06_CN.assets.bank
          - 7: VO_DJ_07_CN.assets.bank
          - 8: VO_DJ_08_CN.assets.bank
          - 9: VO_DJ_09_CN.assets.bank
3. 解包复制好的 banks
    - 启动 `Fmod_Bank_Tools\Fmod_Bank_Tools.exe`
    - 点击左上角的 `Extract` 等待解包完毕 ("Extracting Bank files has finished.")
    - 关闭

### (可选) 音频压缩/格式转码

- 将解包出来的 `.wav` 音频转换为 `.flac`/`.mp3` 等压缩后的音频格式
  - 具体支持格式取决于平台
    - Desktop: 基于 [vlc](https://www.videolan.org/vlc/), [caprica/vlcj](https://github.com/caprica/vlcj), 支持绝大部分音频格式
    - Android: 基于 [MediaCodec](https://developer.android.com/media/platform/supported-formats), 根据文档: `flac`, `mp3`, `aac`, `ogg`
    - iOS: [TBD]
  - 需要 [FFmpeg](ffmpeg.org) 安装于 path 中, [BtbN/FFmpeg-Builds/releases](https://github.com/BtbN/FFmpeg-Builds/releases)
  - [TODO] 提供 FFmpeg ps1 脚本, 可选删除不需要的 `.wav`

### 整理音频

- 整理解包好的音频 (新建与重命名文件夹)
  - 转到目录 `Fmod_Bank_Tools\wav\`
  - 跨多个 bank 的电台: (R1 Horizon Pulse 与 R2 Horizon Bass Arena)
    - 以 `电台名` 创建文件夹: `Horizon Pulse\`
    - `R1_Tracks_CU1.assets[0]\` -> `Horizon Pulse\Track\CU1\`
    - `R1_Tracks_Disk.assets[0]\` -> `Horizon Pulse\Track\Disk\`
    - `R1_Stingers_CN.assets[0]\` -> `Horizon Pulse\Stinger\`
    - `VO_DJ_01_CN.assets[0]\` -> `Horizon Pulse\DJ\`
  - 单个 bank 的电台: (如 R6 Gacha City Radio)
    - `R6_Tracks_CU1.assets[0]\` -> `Gacha City Radio\Track\` (不需要 `CU1`/`Disk` 子文件夹)
    - `R6_Stingers_CN.assets[0]\` -> `Gacha City Radio\Stinger\`
    - `VO_DJ_06_CN.assets[0]\` -> `Gacha City Radio\DJ\`
  - 整理完毕后可以一起移动到其他地方

### 导入电台源

- 打开 `FHRadio`
  - 首页右上角三点菜单 -> 导入电台源
  - 先选择 `ForzaHorizon6\media\Audio\RadioInfo_*.xml` 并确认
  - 再选择 `Fmod_Bank_Tools\wav\`(如果未移动) 并确认
    - 选择包含所有电台的 `wav\`, 而不是单个电台文件夹
  - 简单对电台源进行命名、排序后保存即可
