# Import Guide

## 文件准备

- 安装好的[《极限竞速：地平线 6》](https://store.steampowered.com/app/2483190)
- [Fmod-Bank-Tools](https://github.com/Wouldubeinta/Fmod-Bank-Tools)

### RadioInfo.xml

位于 `ForzaHorizon6\media\Audio\RadioInfo_*.xml`,
语言地区代码根据需要的语言进行选择,
比如不喜欢中文 (CN) 的台呼 CV 语音可以选择英文 (EN),
需要跟后续的 `FMODBank` 选择匹配,
否则如果跳转标记点不匹配实际音频会导致巨缝切歌

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
          - 7: R5_Tracks_Disk.assets.bank (R5 的曲目都在 `_Disk` 内)
          - 8: R6_Tracks_CU1.assets.bank
          - 9: R7_Tracks_CU1.assets.bank
          - 10: R8_Tracks_CU1.assets.bank
          - 11: R9_Tracks_CU1.assets.bank
      - Stinger: (建议不跳过, 种子控制模式需要)
        - 搜索 `R*_Stingers_CN.assets.bank`, 语言地区代码最好匹配前面选择的 `RadioInfo_*.xml`, 例如 `R*_Stingers_EN.assets.bank`
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
      - DJ: (种子控制模式需要)
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
    - 关闭 `Fmod_Bank_Tools`

### (可选) 音频响度均衡

`Stinger` 与 `DJ` 的响度都在 -21 LUFS，而 `Track` 在 -24 ~ -23 LUFS，两者之间的响度有较明显的差异，有条件的话推荐将 `DJ` 的响度下调至 -23 ~ -22 LUFS

### (可选) 音频压缩/格式转码

- 将解包出来的 `.wav` 音频转换为 `.flac`/`.mp3` 等压缩后的音频格式
  - 所有电台包括 `Track`, `Stinger`, `DJ` 解包后占用一共 `12.9 GB`, 按 ffmpeg -compression_level 8 压缩为 `flac` 后, 占用 `6.32 GB`
  - 具体支持格式~~取决于平台~~只有 `wav`, `flac`, `mp3`, `aac`, `ogg(opus)`
    - Desktop: 基于 [vlc](https://www.videolan.org/vlc/), [caprica/vlcj](https://github.com/caprica/vlcj), 支持绝大部分音频格式
    - Android: 基于 [MediaCodec](https://developer.android.com/media/platform/supported-formats), 根据文档: `flac`, `mp3`, `aac`, `ogg`(`opus`)
    - iOS: [TBD]
  - 项目仓库 [tool/](./tool/) 中有个脚本 [tool/convert.ps1](./tool/convert.ps1), 需要 [FFmpeg](ffmpeg.org) 安装于 path 中, [BtbN/FFmpeg-Builds/releases](https://github.com/BtbN/FFmpeg-Builds/releases)

### 整理音频

- 整理解包好的音频 (新建与重命名文件夹)
  - 转到目录 `Fmod_Bank_Tools\wav\`
  - 跨多个 bank 的电台: (R1 Horizon Pulse 与 R2 Horizon Bass Arena)
    - 以 `{电台名}` 创建文件夹: `Horizon Pulse\`
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
  - 简单对电台源进行命名、排序后返回即可

### 示例结果

Windows:

```tree
B:\SOFTWARE\FMOD_BANK_TOOLS\WAV
├─Gacha City Radio
│  ├─DJ
│  │      sound_0.wav
│  │      ...
│  │      sound_246.wav
│  │      
│  ├─Stinger
│  │      sound_0.wav
│  │      ...
│  │      sound_9.wav
│  │      
│  └─Track
│          sound_0.wav
│          ...
│          sound_24.wav
│          
├─Horizon Bass Arena
│  ├─DJ
│  │      sound_0.wav
│  │      ...
│  │      sound_196.wav
│  │      VO_DJ_02_CN.assets[0].txt // 留着或删掉都可以, 无任何影响
│  │      
│  ├─Stinger
│  │      R2_Stingers_CN.assets[0].txt
│  │      sound_0.wav
│  │      ...
│  │      sound_9.wav
│  │      
│  └─Track
│      ├─CU1
│      │      sound_0.wav
│      │      ...
│      │      sound_26.wav
│      │      
│      └─Disk
│              R2_Tracks_Disk.assets[0].txt
│              sound_0.wav
│              sound_1.wav
│              
├─Horizon Block Party
...
```

Android:

```find
/sdcard/Music/Forza Horizon 6
|____Gacha City Radio
| |____Stinger
| | |____sound_0.flac
| | |____...
| | |____sound_9.flac
| |____Track
| | |____sound_0.flac
| | |____...
| | |____sound_24.flac
| |____DJ
| | |____sound_99.flac
| | |____...
| | |____sound_246.flac
|____Horizon Bass Arena
| |____Stinger
| | |____sound_0.flac
| | |____...
| | |____sound_9.flac
| |____Track
| | |____CU1
| | | |____sound_0.flac
| | | |____...
| | | |____sound_26.flac
| | |____Disk
| | | |____sound_0.flac
| | | |____sound_1.flac
| |____DJ
| | |____sound_0.flac
| | |____...
| | |____sound_196.flac
|____Horizon Block Party
```
