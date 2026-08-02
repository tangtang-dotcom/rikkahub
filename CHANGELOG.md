# Changelog — RikkaHub Agents

> 基于 `xiwangone/rikkahub-agents` fork，从 `ExTV/rikkahub-agent` 分叉，随后合入官方 `rikkahub/rikkahub v2.4.3`。
> ~~删除线~~ = 已回退或删除的变更。

---

## 2026-08-02

### 🐛 Bug 修复
- **工具输出默认值 + 单位修正** — 默认值从 4KB 改为 **5KB**（`PreferencesStore.kt`）；修复 `ChatPage.kt` 中 KB/byte 单位混淆（`maxCharsKB` 传参除以 1024），对话框输回乘回 1024 存 bytes；放宽范围下限至 1KB
- **累计 Token 统计 UI 收尾** — `sessionTotals` 移至 `ChatPageContent` 确保输入栏可见；`CardGroup` 支持 `onClick`/`content @Composable`
- **编译错误修复** — 补 `SettingModelPromptPage` 缺的 `Row`/`Spacer`/`width`/`ArrowDown01`/`Tools`/`CardGroupScope` import；`Box`/`AutoCompressDialog`/`ToolOutputDialog` import 校正；删除死 import + 死状态变量

### ✨ 新功能
- **累计 Token 统计** — 对话消息统计区域新增会话级累计行（↑输入 ↓输出 + 命中缓存），各带复制图标
- **工具输出限制开关** — 设置页新增 `toolOutputEnabled` 开关，支持 ON/OFF
- **统计条 UI 中文化** — 消息统计条标签改中文（输入/输出/命中缓存）
- **自动压缩/工具输出对话框** — 点击弹出设置对话框，新增 `ArrowDown01`/`Tools` 图标

### 🌐 本地化
- 补 `settings_confirm` / `settings_cancel` / `setting_model_page_tool_output_desc`（en / zh / zh-rHK）
- 补 TTS 默认播放速度繁中翻译
- 补充简中 strings（`zh-CN`）

### 🔧 CI/CD
- push 触发只构建验证，Release 创建/上传仅限 `workflow_dispatch`
- build-apk workflow 支持 `release_tag` 自定义 + 动态 Release tag + Server酱失败通知


## 2026-08-01

### ✨ 新功能
- **对话底部累计 Token 统计** — 聊天输入栏底部新增会话级累计统计行（↑输入 ↓输出 + 命中缓存），与消息统计条各带复制图标；复制内容保持英文（↑x tokens (x cached)）便于粘贴给 AI 分析
- **统计条 UI 中文化** — 消息统计条标签改中文（输入/输出/命中缓存），数据项（tok/s、耗时、成本）保留

### 🐛 Bug 修复
- 修复设置页 Kotlin 编译错误：`SettingModelPromptPage` 缺 `Row`/`Spacer`/`width`/`ArrowDown01`/`Tools`/`CardGroupScope` import；`AutoCompressDialog`/`ToolOutputDialog` 缺 `settings_confirm`/`settings_cancel` 字符串资源

### 🌐 本地化
- 新增 `settings_confirm` / `settings_cancel` / `setting_model_page_tool_output_desc`（en / zh / zh-rHK 三语）
- 补 TTS 默认播放速度繁中（`setting_tts_page_default_playback_speed`、`setting_tts_page_default_playback_speed_description`）

### 🔧 CI/CD
- build-apk workflow 升级：workflow_dispatch 支持 `release_tag` 自定义、push 自动构建、动态 Release tag、Server酱失败通知

---

## 2026-07-31

### ✨ 新功能
- **本地 OCR 优先** — OcrTransformer 新增 ML Kit 本地识别（中文+拉丁模型合并去重，覆盖中英日韩），识别为空/失败才回退 AI OCR。解决 AI OCR 模型不支持视觉或网络失败时图片无法识别的问题
- **MCP Header 密钥显隐** — 新建 MCP 的请求头 Value 输入框支持小眼睛显隐（PasswordVisualTransformation），与预置 Provider 一致
- **模型多选删除** — Provider 模型列表左滑新增「多选」入口，进入多选模式支持勾选/全选/批量删除，保留长按拖拽排序

### 🐛 Bug 修复
- 修复 `setting_provider_page_select_all` 字符串重复定义导致编译失败（多选全选改用独立 key `setting_provider_page_multi_select_all`）

### 🌐 本地化
- 新增多选相关字符串（中/英）：multi_select、selected_count、multi_select_all、delete_selected

---

## 2026-07-30

### 🔧 重构
- **侧边栏精简** — 移除 Sparkles 菜单中的导出/分享码/导入，恢复干净布局
- **多选导出面板** — JSON 导出 + 导入功能移到 ChatExportSheet（长按消息→分享→勾选）
- **移除分享码功能** — 不再支持分享码生成/复制

### 🐛 Bug 修复
- 修复导出面板编译错误（`rememberLauncherForActivityResult` import 路径、`koinInject` 非 Composable 上下文、包名 `data.chat` → `data.repository`）
- 修复 `FileImport` / `Download01` 缺失 import

### 🔧 CI/CD
- 移除 Gitee 同步步（同步脚本卡死）
- Gitee Release 改为手动上传

---

### ✨ 新功能
- **导出会话为 JSON** — Export.kt 新增 `exportToJson()`，导出为可导入恢复的 JSON 文件
- **导入会话** — 侧边栏抽屉新增导入按钮，支持选择 JSON 文件恢复会话
- **浏览器增强** — 地址栏菜单新增「在浏览器打开」「分享链接」
- **AI 后台浏览** — 默认使用 headless 模式，AI 在后台浏览网页，你留在聊天页

### 🛠 基础设施
- **自定义输入栏按钮** — PreferencesStore.kt 新增 `InputButtonType` / `InputButtonItem` / `ChatInputButtons` 数据类（UI 页面待建）

### 🌐 本地化
- 新增 7 条英文字符串（导入导出 + 浏览器）
- 新增 7 条中文字符串（values-zh）

---

## 2026-07-29

### 🔧 重构
- **侧边栏精简** — 移除 Sparkles 菜单中的导出/分享码/导入，恢复干净布局
- **多选导出面板** — JSON 导出 + 导入功能移到 ChatExportSheet（长按消息→分享→勾选）
- **移除分享码功能** — 不再支持分享码生成/复制

### 🐛 Bug 修复
- 修复导出面板编译错误（`rememberLauncherForActivityResult` import 路径、`koinInject` 非 Composable 上下文、包名 `data.chat` → `data.repository`）
- 修复 `FileImport` / `Download01` 缺失 import

### 🔧 CI/CD
- 移除 Gitee 同步步（同步脚本卡死）
- Gitee Release 改为手动上传

---

### ✨ 新功能
- **DuckDuckGo + AnySearch 免费搜索供应商**（后撤回）
  - ~~添加 DuckDuckGo + AnySearch 免费搜索供应商~~

### 🔧 CI/CD
- `build-apk.yml` 自动上传 APK 到 Release
- 移除 CI 导出 JKS 步骤（已本地保存）
- 移除 CodeQL 代码扫描工作流
- 精简工作流：删除微信通知/issue-notify
- 合并上游改为手动触发

### 🌐 本地化
- 全局替换 `RikkaHub` → `RikkaHub Agents`
- `app_name` 全语言改为 `RikkaHub Agents`
- 汉化硬编码组件：诊断工具/Emoji/聊天列表/供应商描述/生物识别
- 汉化权限清单 `PermissionInventory.kt` 全部标签和描述
- 汉化硬编码占位符（覆盖/未命名/无标题/无）
- 补全简繁体中文字符串翻译（104 + 5 条），中英对齐 1879/1879
- OAuth `clientName` 默认改为 `RikkaHub Agents`
- `X-Title` 头改为 `RikkaHub Agents`
- 修复字符串中 `&gt;&gt;` 导致 AAPT2 编译失败
- ~~繁中 values-zh-rTW → values-zh-rHK~~
- 保留中英双语首页

### 🐛 Bug 修复
- 修复剩余 4 处字符串拼接和中文引号问题
- 修复 `DoctorChecks.kt` 中字符串拼接符位置错误
- 修复字符串中单引号转义
- CI/CD 审计修复（签名/合并冲突/通知URL/CodeQL）
- 签名 keystore 修复（多次迭代）
  - ~~使用临时签名直到 KEYSTORE_BASE64 验证通过~~
  - ✅ 最终使用 Secrets 中的固定签名 keystore
- ~~回退损坏文件到安全状态~~

### 📝 文档
- 更新 `LOCALIZATION.md` 包含权限清单汉化记录
- README 重写聚焦功能
- 添加本地化追踪文件 `LOCALIZATION.md`

---

## 2026-07-28

### ⚡ 初始分支创建
- 从 `ExTV/rikkahub-agent` 分叉
- 合入官方 `rikkahub/rikkahub v2.4.3`
- ~~回退到 v2.4.3 已知完好树（上游损坏后）~~
- 解决 29 个文件冲突（strings.xml 汉化保留）
- emoji README, CI 修复, 本地化门控

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
| **Grok/xAI** | Grok 订阅 + xAI Imagine 图像生成 | ExTV |
| **权限控制** | 工具审批, 安全策略, 内容过滤 | 上游 |
| **主题/UI** | Material You, 深色模式, AMOLED, 自定义字体 | 上游 |

### 优化与修复
- MCP 并发安全, 工具名去重
- 工具输出限制/超时
- Skills WebView 沙箱
- OCR 超时限制
- Logcat 隐私打码
- 浏览器 file:// 导航阻止
- Telegram 429 重试
- 会话上下文阶梯式截断（含提示词缓存）
- 热路径性能收敛
- 全量 reasoning_content 回传

---

### 升级指引

**从 v2.4.3（官方）升级：**
```
git remote add upstream https://github.com/rikkahub/rikkahub.git
git fetch upstream
git merge upstream/master
# 解决 strings.xml 冲突（保留汉化）
```

**从 ExTV 升级：**
```
git remote add extv https://github.com/ExTV/rikkahub-agent.git
git fetch extv
git merge extv/master
# ExTV 功能已全量包含在本 fork 中
```
