# 待办事项 (TODO)

## 1. 累计 Token 缓存统计

- **状态**: 待开发
- **优先级**: P2
- **来源**: 用户需求 - 2026-08-02
- **描述**: 在聊天输入框底部添加"累计缓存命中"统计卡片，与本轮统计格式一致，仅显示输入命中缓存数据，支持点击复制图标分别复制"本轮"和"累计"缓存数据。

### 技术方案

**目标位置:** `ChatInput.kt` 或新建统计组件
**数据来源:** 现有 Usage 结构扩展 vs 新建 SessionTracker（需确认）
**交互方式:** 点击复制图标分别复制"本轮"和"累计"缓存数据

### 关键代码位置

| 文件 | 说明 |
|------|------|
| `app/src/main/java/me/rerere/rikkahub/ui/components/ai/ChatInput.kt` | 输入栏 UI 组件 |
| `app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatVM.kt` | 聊天 ViewModel |
| `ai/src/main/java/me/rerere/ai/core/Usage.kt` | 使用量数据结构（含 cachedTokens） |
| `ai/src/main/java/me/rerere/ai/provider/providers/openai/ChatCompletionsAPI.kt` | 缓存控制实现 |

### 注意事项

- 当前 `ui/pages/chat/` 目录下未实现会话级累计 token 统计逻辑
- 需先确认数据来源是扩展 Usage 还是新建 Tracker
- 复用 ChatMessageNerdLine.kt 中的 `cachedTokens` 展示格式
