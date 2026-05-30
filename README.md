<!-- markdownlint-disable MD033 -->

# FHRadio

<a href="resource/icon.svg">
  <img src="resource/icon_white.png" width="128" height="128" alt="FHRadio" align="right" />
</a>

Forza Horizon (6) radio simulator

## 说明

电台导入: [ImportGuide.md](./doc/ImportGuide.md)

电台实现: [RadioSchema.md](./doc/RadioSchema.md)

这代的扭蛋城电台真不赖吧, 找歌单发现网易云官号有,
但是听了几天后才发现不全, 一气之下气了一下然后研究起来了解包,
解包研究完了看到个 RadioInfo.xml 里面有所有电台曲目的标记点,
前阵子正好写了个安卓上的 [Miuzarte/ScrcpyForAndroid](https://github.com/Miuzarte/ScrcpyForAndroid) 接触到了 Compose,
于是一时兴起用 kotlin multi platform 开了个跨平台项目,
解析这个 xml 并尝试自己实现一个最基础的电台模拟

## 下载

\> [releases](https://github.com/Miuzarte/FHRadio/releases)

## 截图

<p align="center">
  <img src="https://github.com/user-attachments/assets/89ab4bb8-dc97-4155-989c-ce58929c4216" height="307" alt="Radios" />
  <img src="https://github.com/user-attachments/assets/6214d6ed-53cb-4bcf-8d3a-04828dfefe73" height="307" alt="Android 1" />
  <img src="https://github.com/user-attachments/assets/f9d2d535-2439-4f35-a177-8610a891d9f2" height="307" alt="Radios Editing" />
  <img src="https://github.com/user-attachments/assets/94e4f0a9-cc6b-42a5-833e-5a138126aec2" height="307" alt="Tracks" />
  <img src="https://github.com/user-attachments/assets/3df148d7-1e6b-4260-9e07-8c3b4e2810a8" height="307" alt="Settings 1" />
  <img src="https://github.com/user-attachments/assets/8014ca5e-0817-4b1e-baed-9efb88fd2a0c" height="307" alt="Settings 2" />
  <img src="https://github.com/user-attachments/assets/8702399a-3b04-4fb1-a70a-1df0ab15f24c" height="307" alt="Settings 3" />
</p>

## Features

- 实时记录播放状态, 想关就直接杀后台, 完全随机/播放器 模式下可续播
- 光速冷启动且可以自动开播 (?车机储存空间够的话可以直接装车机里
- 音频解包整理后即可导入使用, 不需要任何重命名
- 三种不同的播放模式
  - 完全随机
    - 允许任意调整 `Stinger` / `DJ` 的触发概率与要触发的 `DJ 事件`, 完全随机
  - **种子控制**
    - 输入一个种子, 在所有设备上**完全同步**播放 (要求系统时间相同)
  - 播放器
    - 可以选择跨多个列表或是只播放单个列表
    - 随机播放
      - 允许调整 `Track` / `Stinger` / `DJ` 的最大连续数量
    - 顺序播放
      - 允许自定义循环模式 `Loop Pattern`, 完全掌控播放步进

## 已知问题

- 代码很屎, 后面也没空维护, 不过这种东西应该能用就行

## FAQ

1. 一个本地音乐播放器要占 300 MB (windows), 开什么玩笑
    - 你说得对这是我用 kmp 写的第一个应用也是最后一个
    - 构建产物附带的 `vlc\` 可以删掉, 应用会自动查找 `B:/C:/D:` 的 `Program Files\` / `Program Files (x86)\` 下安装的 VLC
2. 怎么同时听不同电台的曲目
    - 编辑 RadioInfo.xml 重新导入, 参考 `StreamerMode` 添加一个新的自定义电台, 或者直接改它也行
    - 注意应用默认隐藏 `StreamerMode`, 要在编辑电台源处手动解除隐藏
3. 我想听地平线 4/5 的电台怎么办
    - 地平线4: RadioInfo 中没有 DJ 的曲目, 同时也不清楚 bank 的结构, 大概率开摆
    - 地平线5: 我电脑正好没有 5, 如果其 `RadioInfo_*.xml` 的版本为 `2`, 也许可以直接用
      - `<Radio Version="2" TimeBetweenMidTrackDJLines="120" RecentlyPlayedMaxSize="-1">`
4. 苹果怎么用
    - 写好 actual 的文件(夹)选择以及媒体播放器还有 workflow 构建最后提交 Pr 即可

## Credits

- 图标版权归属于 [Playground Games](https://playground-games.com) / Microsoft
  - 来源: [wikimedia](https://commons.wikimedia.org/wiki/File:Forza_logo_2020.svg)
- desktop 端媒体播放: [caprica/vlcj](https://github.com/caprica/vlcj)
- desktop 端构建配置参考: [mahozad/cutcon](https://github.com/mahozad/cutcon)
- 界面组件: [YuKongA/miuix](https://github.com/compose-miuix-ui/miuix)

## License

[Apache License 2.0](LICENSE)
