<div align="center">
  <img src="docs/logo.svg" alt="RikkaHub Agents" width="100" height="100" />
  <h1>RikkaHub Agents</h1>
  <p><b>🤖 AI 自动维护 · Android Agent Fork · 多 LLM 自动编译</b></p>

[![Build](https://img.shields.io/github/actions/workflow/status/xiwangone/rikkahub-agents/build.yml?style=flat&logo=githubactions&label=Build)](https://github.com/xiwangone/rikkahub-agents/actions)
[![Release](https://img.shields.io/github/v/release/xiwangone/rikkahub-agents?style=flat&logo=github)](https://github.com/xiwangone/rikkahub-agents/releases)
[![License](https://img.shields.io/github/license/xiwangone/rikkahub-agents?style=flat)](LICENSE)
[![Last Commit](https://img.shields.io/github/last-commit/xiwangone/rikkahub-agents?style=flat&label=Updated)](https://github.com/xiwangone/rikkahub-agents/commits/master)
[![Repo Size](https://img.shields.io/github/repo-size/xiwangone/rikkahub-agents?style=flat&label=Size)](https://github.com/xiwangone/rikkahub-agents)
[![Telegram](https://img.shields.io/badge/Telegram-Bot-2CA5E0?style=flat&logo=telegram)](https://t.me/your_bot)

> AI 自动维护编译 · 提交即构建 · 结果推送微信 & Telegram

</div>

---

## 📋 概述

本仓库是 **RikkaHub** 官方版与 **ExTV** Fork 的合并维护版，集成了 AI 驱动的自动化编译、设备管理和通知推送能力。

### 🎯 核心能力

| 功能 | 说明 |
|------|------|
| 🤖 **AI 自动化维护** | 通过 Action 工作流自动编译、检测代码变更 |
| 🔧 **80+ 设备工具** | 集成 SSH、ADB、Telegram Bot 等设备管理工具 |
| ⚙️ **自动编译** | 提交代码后自动构建 APK，编译结果微信/Telegram 通知 |
| 🔗 **Telegram Bot** | 远程控制、查询编译状态、触发任务 |
| 🔄 **SSH 通道** | 内网穿透与远程设备管理 |
| 🌐 **Server酱通知** | 编译成功/失败即时推送到微信 |

### 📱 上游特性（源自 RikkaHub）

- 🎨 Material You 设计 + 深色模式
- 📦 Workspace：基于 proot 的 Linux agent 环境
- 🔄 多 AI Provider 支持（OpenAI / Google / Anthropic 兼容 API）
- 🖼️ 多模态输入（图片、PDF、DOCX）
- 🖥️ Web 多端访问
- 🛠️ MCP 支持
- 🔍 多搜索引擎（Exa / Tavily / Zhipu / Bing / Brave 等）
- 🧩 Prompt 变量、Agent 自定义、记忆功能
- 📝 Markdown 渲染（代码高亮、LaTeX、图表）

---

## ⚡ 快速开始

### 构建

```bash
# 克隆仓库
git clone https://github.com/xiwangone/rikkahub-agents.git
cd rikkahub-agents

# 使用 Android Studio 打开
./gradlew assembleDebug
```

> [!TIP]
> 需要在 `app/` 目录下放置 `google-services.json`（Firebase 配置）。

### 自动化工作流

仓库内置 GitHub Actions 工作流，支持：

- `build.yml` - 自动编译 Debug/Release APK
- `release.yml` - 自动发布 Release
- 编译结果推送到 **Server酱**（微信）和 **Telegram**

---

## 📁 仓库结构

```
├── .agents/              # AI Agent 配置文件
├── .github/              # GitHub Actions 工作流
├── app/                  # 主应用模块
├── ai/                   # AI SDK 抽象层
├── common/               # 公共工具库
├── workspace/            # Proot Linux 环境模块
├── web/                  # 嵌入式 Web 服务器
├── docs/                 # 文档与图标资源
│   ├── logo.svg          # 项目 Logo
│   ├── icon.png          # 应用图标
│   └── img/              # 截图
└── AGENTS.md             # Agent 开发指南
```

---

## 📦 下载

> 来自 Actions 的自动构建产物，可在 [Releases](https://github.com/xiwangone/rikkahub-agents/releases) 页面获取最新 APK。

---

## 🛠️ 技术栈

- **Kotlin** / **Koin** / **Jetpack Compose**
- **DataStore** / **Room** / **Coil**
- **Material You** / **Navigation 3**
- **OkHttp** / **kotlinx.serialization**

---

## ⚠️ 声明

- 本仓库为个人维护的 **Fork 版本**，与原版 RikkaHub 无关
- Issues / PRs 请提交到本仓库，不要打扰上游原项目
- 使用 Fork 版本请注意隐私安全与权限管理

---

## 📄 许可

[GNU Affero General Public License v3.0](LICENSE) (AGPL-3.0)
