<div align="center">
  <img src="docs/logo.svg" alt="RikkaHub Agents" width="100" height="100" />
  <h1>RikkaHub Agents</h1>

RikkaHub 的 Android Agent 维护版 Fork：自动 CI 编译、上游同步、完整设备工具能力 🤖

[English](README.md) | 简体中文

[![Build](https://img.shields.io/github/actions/workflow/status/xiwangone/rikkahub-agents/build-apk.yml?style=flat&logo=githubactions&label=Build)](https://github.com/xiwangone/rikkahub-agents/actions/workflows/build-apk.yml)
[![Upstream](https://img.shields.io/github/actions/workflow/status/xiwangone/rikkahub-agents/merge-upstream.yml?style=flat&logo=git&label=Upstream)](https://github.com/xiwangone/rikkahub-agents/actions/workflows/merge-upstream.yml)
[![CodeQL](https://img.shields.io/github/actions/workflow/status/xiwangone/rikkahub-agents/codeql.yml?style=flat&logo=github&label=CodeQL)](https://github.com/xiwangone/rikkahub-agents/actions/workflows/codeql.yml)
[![Release](https://img.shields.io/github/v/release/xiwangone/rikkahub-agents?style=flat&logo=github)](https://github.com/xiwangone/rikkahub-agents/releases)
[![License](https://img.shields.io/github/license/xiwangone/rikkahub-agents?style=flat)](LICENSE)
[![Last Commit](https://img.shields.io/github/last-commit/xiwangone/rikkahub-agents?style=flat&label=Updated)](https://github.com/xiwangone/rikkahub-agents/commits/master)

</div>

<div align="center">
  <img src="docs/img/chat.png" alt="聊天界面" width="150" />
  <img src="docs/img/desktop.png" alt="桌面 / 模型" width="450" />
</div>

## 🚀 下载

🔗 [Releases](https://github.com/xiwangone/rikkahub-agents/releases) — 最新签名 APK  
🔗 [Actions 产物](https://github.com/xiwangone/rikkahub-agents/actions) — 每次构建的 artifact

> [!WARNING]
> 本仓库为**非官方 Fork**。Fork 问题与上游 RikkaHub 无关。
> 请谨慎使用第三方构建，注意隐私与权限。

## ✨ 本 Fork 额外能力

| 能力 | 说明 |
|:--|:--|
| 🤖 **自动维护** | Release 编译与定时合并上游工作流 |
| 🔧 **Agent 工具链** | SSH、Termux、设备工具、Telegram Bot 等 Agent 系能力 |
| 🔐 **稳定 CI 签名** | 仓库 Secrets 固定密钥，产物可覆盖安装 |
| 🔍 **代码扫描** | 每周 / 手动 CodeQL（不在每次 push 触发，节省 runner） |

## ✨ 功能特色（源自 RikkaHub）

- 🎨 Material You 设计与 🌙 深色模式
- 📦 工作区：基于 proot 的 Linux 智能体环境
- 🔄 多 AI 供应商（OpenAI / Google / Anthropic 兼容 API）
- 🖼️ 多模态输入（图片、PDF、DOCX 等）
- 🖥️ 内嵌 Web 多端访问
- 🛠️ MCP 支持
- 📝 Markdown（代码高亮、LaTeX、表格、Mermaid）
- 🔍 搜索（Exa、Tavily、Zhipu、Brave 等）
- 🧩 Prompt 变量、智能体自定义、记忆

## ⚡ 构建

```bash
git clone --recurse-submodules https://github.com/xiwangone/rikkahub-agents.git
cd rikkahub-agents
./gradlew assembleDebug
```

> [!TIP]
> 需在 `app/` 放置 `google-services.json`。  
> `web` 模块构建依赖 **pnpm**（`web-ui/`）。

### 关于 CI

- 工作流在 `.github/workflows/`，属于仓库文件：`git clone` / fork **会一并拉取**。
- Secrets（`KEYSTORE_*`、通知密钥、可选 `PAT_TOKEN`）只存在于 GitHub，**不会**被 clone。
- 签名 Release：Actions → **Build APK** → Run workflow。

## ✨ 贡献

使用 [Android Studio](https://developer.android.com/studio) 开发。欢迎修复类 / 文档类 PR。

技术栈：Kotlin · Koin · Jetpack Compose · DataStore · Room · Coil · Material You · Navigation 3 · OkHttp · kotlinx.serialization

> [!IMPORTANT]
> Fork 相关问题请在**本仓库**提 Issue / PR，勿打扰上游。

## 🙏 致谢

- [**RikkaHub**](https://github.com/rikkahub/rikkahub) — 官方客户端
- [**ExTV/rikkahub-agent**](https://github.com/ExTV/rikkahub-agent) — Agent 模式上游 Fork

## 📄 许可证

[GNU Affero General Public License v3.0](LICENSE) (AGPL-3.0)
