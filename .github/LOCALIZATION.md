# 本地化追踪 · Localization Tracking

汉化内容变更记录，含 strings.xml 和 Kotlin 硬编码文本。

## 基准

| 字段 | 值 |
|------|-----|
| 基准 commit | `af215690` |
| 基准日期 | 2026-07-28 |
| 英文 strings 总数 | 1879 |
| 简中 strings 覆盖 | 1879 / 1879 |
| 繁中（香港）strings 覆盖 | 1879 / 1879 |
| 硬编码汉化 | 诊断工具 / 权限清单 / Emoji / 聊天列表 / 供应商 / 占位符 |

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

```
git diff af215690 HEAD -- app/src/main/res/values/strings.xml | grep '^+.*<string name='
```

## 硬编码汉化清单

| 文件 | 内容 | 条数 |
|------|------|------|
| `doctor/DoctorChecks.kt` | 诊断工具：权限/服务/数据库/网络/系统信息标签和描述 | ~150 |
| `doctor/DoctorModels.kt` | 诊断分类名 | 8 |
| `doctor/DoctorReport.kt` | 诊断报告表头 | 3 |
| `permissions/PermissionInventory.kt` | 权限清单全部标签和描述（服务/特殊访问/运行时/自动授予） | ~60 |
| `Emoji.kt` | 表情分类 | 10 |
| `ChatList.kt` | 多选操作 | 3 |
| `DefaultProviders.kt` | LLM 供应商描述 | 4 |
| `ToolHostActivity.kt` | 生物识别弹窗 | 1 |
| 通用占位符 | (unnamed)/(no title)/(none)/(Override) | 6 处共 10 |

## 翻译工作流

1. 上游合并后 diff 定位新增 strings key
2. 翻译并同步更新 `values-zh/strings.xml` 和 `values-zh-rHK/strings.xml`
3. 检查上方硬编码文件是否有新增英文
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
| 2026-07-28 | `7b79d8f` | 硬编码组件汉化 ~170 条（诊断/Emoji/聊天/供应商） |
| 2026-07-28 | `af215690` | 权限清单 + 占位符汉化 ~70 条 |
