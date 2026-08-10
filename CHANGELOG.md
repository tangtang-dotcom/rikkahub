# Changelog — RikkaHub Agents

> 基于 `xiwangone/rikkahub-agents` fork，从 `ExTV/rikkahub-agent` 分叉，随后合入官方 `rikkahub/rikkahub v2.4.3`。
> 只保留时间线的**功能改动**与**修复成功**记录。~~删除线~~ = 已回退或删除的变更。

---

## 2026-08-11

- **功能** Vault 会话多会话 — 独立 token（id.expiry.HMAC）+ label + 自定义 TTL（30min/7d/30d/当场）+ 单会话撤销 + 会话列表 UI（`7c0fdec5` / `95b62736`）
- **功能** Vault 会话作用域 — `decrypt` scope 校验，`/vault/decrypt` 要求 scope，为 Web 桥 JWT 预留（`1b9069c0`）
- **修复** 签发新会话后列表不刷新 — sessionListState 提升页面级，签发/进入/撤销三处刷新（`65b47696`）
- **重构** 拆分 TelegramBotService 静态注册表 — ApprovalPromptRegistry/RejectedSenderLog/SlashCommandLog/BUILT_IN_COMMANDS → TelegramBotRegistries.kt（-170 行）（`9f6ae3b0`）
- **修复** WebView 资源释放 — onRelease 时 destroy 防泄漏（cherry-pick 上游 `2c980642`）（`34ba994e`）
- **功能** 新增 mimo v3 / v3 pro / qwen-3.8-max 模型定义（cherry-pick 上游）（`0ea350ea`）
- **修复** 过期会话惰性自动清理 — readSessions 过滤已过期，防 SESSIONS 无限增长（`97192302`）
- **ci** 缓存 web-ui 依赖 — bun.lock key，避免每次全量 pnpm install，加速构建（`3b86e00a`）

## 2026-08-10

- **修复** Room 迁移链断裂导致 2.45.6 闪退 — v29 迁移误判「v28 未发布」只写 `AutoMigration(27→29)`，而 v28 已随 2.45.5（Vault MVP）发布；已装 2.45.5 的用户升级 2.45.6 时 Room 找不到 28→29 迁移路径抛 IllegalStateException。新增手写 `Migration_28_29`（建 `vault_audit_log` 表 + 2 索引），与 27→29 并存，Room 按设备版本自动选路径（`198a8d85`）
- **功能** Vault 会话双模式 — 「30min TTL / 当场有效」持久化回显（退出重进不丢模式选择）+ `getSessionMode/hasSession` 读取方法（`90206b0c`）
- **功能** 聊天输入框全局设置快捷入口 — ⚡ 闪电旁新增齿轮图标，免切出设置 tab（`20ac3857`）
- **功能** Web 桥公钥一键复制按钮 — 生成密钥后可直接复制 ssh-rsa 公钥，杜绝截图 OCR 错误（`1f3c65e3`）
- **修复** Web 桥 SSH 私钥文件权限 0600 — `setReadable(false)` 误移除权限致 JSch `EACCES Permission denied`（`d86be0f5`）
- **修复** Web 桥隧道成功后清空上次失败红字 — 避免「✅已连接 + auth fail」矛盾显示（`90206b0c`）
- **功能** 悬浮窗小圆点常态 + 卡片动画展开收起 — 18dp 状态色圆点（绿/黄/红/灰），点击以圆点为锚 scale+淡入展开，收起反向动画；UNKNOWN 状态色白→灰（`7f157343`）
- **修复** 额度页 ProviderEditSection 未包 item() — 违反 CardGroup DSL 致展开表单不渲染 + 条目点击失效（`e342a4bf`）
- **chore** 版本号由发版控制 — build-apk.yml 纯编译（去 bump/发版/release_tag），release.yml 发版时 bump versionName→release_tag；新增 release.yml 发版专用工作流；删除 reasonix-review.yml 审查工作流（`0999dcde` / `54281400`）
- **ci** 构建加速 — Gradle 构建缓存 + 配置缓存 + ktlint 固定版本 + 缓存 key 精准化（`e3267654` / `ad5e2c7d`）
- **ci** 缓存防堆积 — 依赖缓存(libs 指纹) + 构建缓存固定 key，不再每构建存全量快照（`6078e671`）
- **ci** 统一 APK 命名 `RikkaHub-Agents-版本号-架构-release`（`29e6b809`）
- **chore** versionName 2.45.6 → 2.45.7（versionCode 176）
## 2026-08-06

- **功能** 缓存命中优化（目标 50%→90%+）：`limitContext` 改「保前缀、只从末尾回收」（`c378dd4`）；tool schema 规范化排序保证跨轮前缀字节稳定（`8fd0645` / `7613917`）
- **修复** tool 规范化排序编译错误 — `BuiltInTools`(sealed class) 用 `simpleName`、`UIMessagePart.Tool` 用 `toolName` 排序，修复 `:ai:compileReleaseKotlin` 失败（`13bed253`）
- **chore** versionName 同步 2.4.5 → 2.45.2（与 release 对齐，`2f75b3d`）

## 2026-08-05

- **功能** P0 凭证加密 — providers 含 apiKey/私钥以 AES-GCM 密文入库（AndroidKeyStore），读路径解密失败回退原文平滑升级；备份规则注释同步（`5404e61`）
- **功能** 自动压缩上下文重构 — 双模式（百分比基准×阈值 / token 消耗累计上限）+ 会话级触发点 + 确认弹窗（`d5b3ce6`）
- **功能** 自动压缩生成中不弹窗、延后到对话结束再弹（wait conversationJob 完成，最多 5 分钟防死锁）（`0112d83`）
- **重构** 工具输出常量统一 1000 进制（16K/4K/5K）+ tool_outputs 补清理机制（保留最近 50 个，防无限累积）（`a814e64`）
- **重构** 工具输出限制单位统一为 1000 进制（/1000、×1000），同步三语言文案 5 = 5000 字符（`af5a7df`）
- **修复** AutoCompressDialog 百分比输入框 currentThreshold 改为 String 状态，修复删空不更新导致 5→50→500 追加；确认时 toIntOrNull 钳制 50-95 默认 80（`83a1831`）
- **修复** ToolOutputDialog 输入框 currentKB 改为 String 状态，修复删空不更新导致 5→50→500 追加（`cd520b9`）
- **文档** 首页互链 Reasonix Agents（开发中）（`055b740`）
- **文档** README_EN 互链 Reasonix Agents（开发中）保持中英一致（`74542fc`）
- **文档** 重要声明调整（本项目+并列在上、上游在下、保留全部上游）+ 中英一致（`a3517bd` / `902d66d`）
- **文档** 首页关于描述更新（AI维护迭代文案+使用前提+红色注）+ 中英同步（`d3cedda`）

## 2026-08-04

- **功能** 新增对话 token 上限设置 — 达到上限自动压缩；依赖自动压缩开关，与阈值并列可单独设置（`b9bad775` / `7dbb1ac6`）
- **修复** 自动压缩 token 估算改为 1:1 — 原 `/4` 低估中文，长会话永不触发压缩（`c05ee0d5`）
- **修复** 删除设置界面重复的对话 token 上限输入框 — 保留开关内与阈值并列的一个（`46edb765`）
- **修复** 补 `LaunchedEffect` import — 修复 CI 编译失败 Unresolved reference（`84eb4a7f`）
- **修复** 模型多选删除移到顶栏 + 工具图片按视觉能力降级 + OCR 提示区分本地/AI（`ce89d580`）
- **修复** `reasoning_effort` OFF 时不传 — ResponseAPI + opencode 分支，修复 Console Go 400（`3d99a936`）
- **文档** AI 自动维护改为 AI 协助维护 + 去掉固定签名表述（`dd5d0b6d` / `77c62769`）

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

## 2026-07-28

- **初始** 从 `ExTV/rikkahub-agent` 分叉 + 合入官方 `rikkahub/rikkahub v2.4.3`
- **修复** 29 个文件冲突（strings.xml 汉化保留）
- **品牌** emoji README, CI 修复, 本地化门控

## 2026-07-27

- **重构** 重写合并策略 — 官方为主上游 + 智能冲突解决（`merge-upstream.yml`）
- **修复** 工作流：`GITHUB_OUTPUT` 格式 / 补 `workflows:write` 权限 / push 前删上游 workflow / `rm` 顺序
- **品牌** README 重塑 — SVG logo + 中文 i18n + 致谢 RikkaHub 官方 & ExTV
- **品牌** 更新 App 图标（现代 AI 主题）+ fork 专属主页描述


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