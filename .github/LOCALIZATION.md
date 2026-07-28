# 本地化追踪 · Localization Tracking

汉化内容变更记录，含 strings.xml 和 Kotlin 硬编码文本。

## 基准

| 字段 | 值 |
|------|-----|
| 基准 commit | `36618f46` |
| 基准日期 | 2026-07-28 |
| 英文 strings 总数 | 1879 |
| 简中 strings 覆盖 | 1879/1879 |
| 繁中 strings 覆盖 | 1879/1879 |
| 硬编码汉化 | 诊断工具 / Emoji / 聊天列表 / 供应商 / 生物识别 |

## 各语言 strings 覆盖

| 语言 | app | search | web-ui |
|------|-----|--------|--------|
| zh-CN | 1879/1879 | 5/5 | 20/20 |
| zh-HK | 1879/1879 | 5/5 | — |
| ja | 1770/1879 | 5/5 | — |
| ko | 1770/1879 | 5/5 | — |
| ru | 1770/1879 | 5/5 | — |
| ar | 1490/1879 | — | — |

## 检测新增字符串

用 diff 对比检测新增英文 key：

```
git diff 36618f46 HEAD -- app/src/main/res/values/strings.xml | grep '^+.*<string name='
```

或精确 key 对比脚本（见仓库根目录 `check_zh.sh` 可用）。

## 硬编码汉化清单

以下文件在 `7b79d8f` 完成汉化，上游合并后需复查：

| 文件 | 汉化内容 | 条数 |
|------|----------|------|
| `doctor/DoctorChecks.kt` | 权限标签、状态描述、修复按钮、系统信息 | ~140 |
| `doctor/DoctorModels.kt` | 诊断分类名 | 8 |
| `doctor/DoctorReport.kt` | 诊断报告表头 | 3 |
| `Emoji.kt` | 表情分类 | 10 |
| `ChatList.kt` | 多选操作 | 3 |
| `DefaultProviders.kt` | 供应商描述 | 4 |
| `ToolHostActivity.kt` | 生物识别弹窗 | 1 |

## 翻译工作流

1. 上游合并后 diff 定位新增 strings key
2. 翻译并同步更新 `values-zh/strings.xml` 和 `values-zh-rHK/strings.xml`
3. 检查硬编码文件是否有新增英文
4. 更新本文件基准 commit

## 工具

`locale-tui/` Python TUI 翻译管理工具：
```
cd locale-tui && uv run locale-tui
```

## 补全历史

| 日期 | commit | 内容 |
|------|--------|------|
| 2026-07-28 | `5044796` | strings.xml 简/繁 104+5 条 |
| 2026-07-28 | `7b79d8f` | 硬编码组件汉化 ~170 条 |
