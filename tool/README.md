# FHRadio - Tool

## convert.ps1

参数:

- [string]$InputFolder: 输入文件夹, 默认取工作目录
- [string]$OutputFolder: 输出文件夹
- [string]$InputExt: 输入格式, 默认 `wav`
- [string]$OutputExt: 输出格式, 默认 `flac`
- [int]$Concurrency: 并发数, 默认 `1`
- [switch]$DryRun: dry run

用例:

```powershell
.\tool\convert.ps1 -InputFolder 'B:\Software\Fmod_Bank_Tools\wav' -OutputFolder 'A:\Miuzarte\Music\Forza Horizon 6' -Concurrency 6
```

## SampleMapper

开发用, 根据 `RadioInfo_*.xml` 中 Sample 的 SampleLength 采样数对比 `wav` 文件大小实现匹配,
输出 kotlin 语法的 map 字面量
