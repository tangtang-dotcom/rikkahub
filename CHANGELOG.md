# Changelog — RikkaHub Agents

> 基于 `xiwangone/rikkahub-agents` fork，从 `ExTV/rikkahub-agent` 分叉，随后合入官方 `rikkahub/rikkahub v2.4.3`。
> 只保留时间线的**功能改动**与**修复成功**记录。~~删除线~~ = 已回退或删除的变更。

---

## 2026-08-03

- **修复** OCR 提示仅在真正需要识别（未缓存）时显示，避免历史图片每次触发"正在识别图片"（`e8869ddb`）
- **修复** OCR 本地识别支持 `content://` 路径 + Codex OFF 不传 `reasoning_effort=none`（`7d43d0ef`）
- **修复** OCR content:// 改用 `BitmapFactory` 解码（`fromInputStream` 不存在，`f9bc39ac`）
- **修复** OCR content:// 分支 `fromBitmap` 缺 `rotationDegrees` 参数导致编译失败（`7f6ebb88`）
- **功能** 累计统计复制按钮 + 本地 OCR 开关（默认开）+ LiteRT 本地导入（`de355aa3`）
- **功能** 提供商双语描述 + 恢复本地 LLM 按钮 + 移除 LiteRt 添加选项 + 文件管理全选批量删除（`69ca514d`）
- **修复** 删除 `TokenBudgetTracker` 重复导入，修复 CI 编译错误（`5714ba69`）
- **修复** 删除误提交的 `review.txt`（reasonix 审查产物，`11b2533e`）

## 2026-08-02

- **功能** 累计 Token 统计 — 对话消息统计区域新增会话级累计行（↑输入 ↓输出 + 命中缓存），各带复制图标
- **功能** 工具输出限制开关 — 设置页新增 `toolOutputEnabled` 开关（ON/OFF）
- **功能** 统计条 UI 中文化 — 消息统计条标签改中文（输入/输出/命中缓存）
- **功能** 自动压缩/工具输出对话框 — 点击弹出设置对话框
- **修复** 工具输出默认值 + 单位修正 — 默认 4KB→5KB；修复 KB/byte 单位混淆
- **修复** 累计 Token 统计 UI 收尾 — `sessionTotals` 移至 `ChatPageContent` 确保输入栏可见
- **修复** 编译错误修复 — 补 `SettingModelPromptPage` 缺失 import

## 2026-08-01

- **功能** 对话底部累计 Token 统计 — 聊天输入栏底部新增会话级累计统计行（↑输入 ↓输出 + 命中缓存）
- **功能** 统计条 UI 中文化 — 消息统计条标签改中文
- **修复** 设置页 Kotlin 编译错误 — 补缺失 import + 字符串资源

## 2026-07-31

- **功能** 本地 OCR 优先 — `OcrTransformer` 新增 ML Kit 本地识别（中文+拉丁合并去重，覆盖中英日韩），失败才回退 AI OCR
- **功能** MCP Header 密钥显隐 — 新建 MCP 请求头 Value 输入框支持小眼睛显隐
- **功能** 模型多选删除 — Provider 模型列表左滑多选/全选/批量删除，保留长按拖拽排序
- **修复** `setting_provider_page_select_all` 字符串重复导致编译失败

## 2026-07-30

- **重构** 侧边栏精简 — 移除 Sparkles 菜单中的导出/分享码/导入
- **重构** 多选导出面板 — JSON 导出 + 导入移到 ChatExportSheet
- **重构** 移除分享码功能
- **功能** 导出会话为 JSON — 导出为可导入恢复的 JSON 文件
- **功能** 导入会话 — 侧边栏抽屉新增导入按钮
- **功能** 浏览器增强 — 地址栏「在浏览器打开」「分享链接」
- **功能** AI 后台浏览 — 默认 headless 模式，AI 后台浏览网页
- **修复** 导出面板编译错误 — import 路径 / `koinInject` 上下文 / 包名 `data.chat`→`data.repository`
- **修复** `FileImport` / `Download01` 缺失 import

## 2026-07-29

- **修复** 导出面板编译错误 — import 路径 / `koinInject` 上下文 / 包名
- **修复** `FileImport` / `Download01` 缺失 import
- ~~**功能** DuckDuckGo + AnySearch 免费搜索供应商（后撤回）~~
- **修复** 剩余 4 处字符串拼接和中文引号问题
- **修复** `DoctorChecks.kt` 字符串拼接符位置错误
- **修复** 字符串中单引号转义
- **修复** 签名 keystore 修复（多次迭代，最终使用 Secrets 固定签名 keystore）
- **修复** 汉化硬编码组件（诊断工具/Emoji/聊天列表/供应商描述/生物识别）+ 补全简繁中翻译 104+5 条对齐 1879/1879
- **修复** 字符串 `&gt;&gt;` 导致 AAPT2 编译失败

## 2026-07-27

- **重构** 重写合并策略 — 官方为主上游 + 智能冲突解决（`merge-upstream.yml`）
- **修复** 工作流：`GITHUB_OUTPUT` 格式 / 补 `workflows:write` 权限 / push 前删上游 workflow / `rm` 顺序
- **品牌** README 重塑 — SVG logo + 中文 i18n + 致谢 RikkaHub 官方 & ExTV
- **品牌** 更新 App 图标（现代 AI 主题）+ fork 专属主页描述

## 2026-07-28

- **初始** 从 `ExTV/rikkahub-agent` 分叉 + 合入官方 `rikkahub/rikkahub v2.4.3`
- **修复** 29 个文件冲突（strings.xml 汉化保留）
- **品牌** emoji README, CI 修复, 本地化门控

---

## 上游历史（2025-03 ~ 2026-07）

### 核心功能（来自官方 rikkahub/rikkahub + ExTV/rikkahub-agent）

| 类别 | 功能 | 来源 |
|------|------|------|
| **AI 引擎** | OpenAI / Claude / Gemini / DeepSeek / Grok / Ollama 等全部主流模型 | 上游 |
| **Agent 模式** | Function Calling, 工具调用, 本地工具系统 | 上游 |
| **工作区** | 沙箱执行环境, 文件读写, Shell 命令 | 上游 |
| **MCP 协议** | MCP 客户端, OAuth 2.1, 工具发现 | 上游 |
| **Skills 系统** | 技能安装/管理/沙箱 WebView | 上游 |
| **搜索** | Bing / DuckDuckGo / CustomJS / Exa / Grok / Jina / Brave / Tavily / SearXNG 等 | 上游 |
| **语音** | ElevenLabs TTS, MIMO TTS, Fish Audio, 系统 TTS, ASR 多引擎 | ExTV |
| **浏览器** | 17 个浏览器工具（读写/截图/提取文本/JS 执行）, 前台 + 后台模式 | 上游 |
| **Telegram Bot** | 完整的 Telegram 集成, 远程代理 | ExTV |
| **备份** | WebDAV / S3 备份恢复, Chatbox 导入 | 上游 |
| **导出** | Markdown / 图片导出 → 新增 JSON 导出（本 fork） | 本 fork |
| **文件夹** | 会话分组管理 | ExTV |