<div align="center">
  <img src="docs/logo.svg" alt="RikkaHub Agents" width="104" height="104" />
  <h1>RikkaHub Agents</h1>
  <p><strong>🤖 面向 Android 的全功能 Agent 客户端</strong></p>
  <p>📱 设备自动化 · 💻 远程终端 · 🧠 多模型会话 · 🛠️ 工具调用</p>

[简体中文](README.md) | [English](README_EN.md)

[![Build](https://img.shields.io/github/actions/workflow/status/xiwangone/rikkahub-agents/build-apk.yml?style=flat&logo=githubactions&label=Build)](https://github.com/xiwangone/rikkahub-agents/actions/workflows/build-apk.yml)
[![Upstream](https://img.shields.io/github/actions/workflow/status/xiwangone/rikkahub-agents/merge-upstream.yml?style=flat&logo=git&label=Sync)](https://github.com/xiwangone/rikkahub-agents/actions/workflows/merge-upstream.yml)
[![Release](https://img.shields.io/github/v/release/xiwangone/rikkahub-agents?style=flat&logo=github)](https://github.com/xiwangone/rikkahub-agents/releases)
[![License](https://img.shields.io/github/license/xiwangone/rikkahub-agents?style=flat)](LICENSE)

</div>


## 🚀 下载

- [Releases](https://github.com/xiwangone/rikkahub-agents/releases)：稳定发布包
- [Actions](https://github.com/xiwangone/rikkahub-agents/actions)：每次成功构建的 APK 产物

> [!CAUTION]
> ## 非官方、非原版构建
> **本仓库不是 RikkaHub 官方仓库，也不是 ExTV/rikkahub-agent 原版仓库。**
> 这是由第三方独立维护的 Fork，代码、自动化流程、签名与发布产物均可能不同。
> 安装前请核对仓库地址、APK 签名、权限范围和发布说明；请勿将本仓库问题提交给官方或原版维护者。

## ✨ 核心能力

| 能力 | 说明 |
|:--|:--|
| Agent 模式 | 面向复杂任务的连续工具调用与执行流程 |
| 设备工具 | SSH、终端、文件、应用与 Android 设备自动化能力 |
| 多模型接入 | 支持 OpenAI、Google、Anthropic 兼容接口及自定义服务 |
| 工作区 | 基于 proot 的隔离 Linux 环境与命令执行 |
| 多模态 | 支持图片、文档、PDF 等输入与内容处理 |
| 扩展集成 | 支持 MCP、搜索、Web 访问和 Telegram Bot |
| 自动维护 | 定时同步源代码，按需构建签名 APK |

## 🧰 快速构建

```bash
git clone --recurse-submodules https://github.com/xiwangone/rikkahub-agents.git
cd rikkahub-agents
./gradlew assembleDebug
```

> [!TIP]
> 构建前需在 `app/` 放置 `google-services.json`。  
> `web` 模块依赖 **pnpm** 构建 `web-ui/`。

## 🔐 CI 与签名

- `.github/workflows/` 属于仓库内容，clone 或 fork 时会一并获得工作流文件。
- GitHub Secrets 不属于 Git 仓库，不会被 clone 或 fork。
- Release 构建使用固定签名密钥，使用同一密钥生成的后续 APK 可覆盖安装。
- 手动构建入口：Actions → **编译 APK · Build APK** → Run workflow。

## 📁 项目结构

```text
├── app/                    # Android 应用
├── ai/                     # 模型与消息抽象
├── workspace/              # Agent 工作区与设备工具
├── web/                    # 内嵌 Web 服务
├── web-ui/                 # Web 前端
├── .github/workflows/      # 自动维护与构建
└── docs/                   # 图标与界面资源
```

## 🤝 反馈与贡献

- Fork 专属问题请提交到本仓库的 [Issues](https://github.com/xiwangone/rikkahub-agents/issues)。
- 提交改动前请完成对应模块构建或测试，避免混入无关重构。

## 🙏 致谢与来源

感谢以下项目及其维护者提供的代码与设计基础：

- **RikkaHub 官方项目**：[rikkahub/rikkahub](https://github.com/rikkahub/rikkahub)
- **Agent 原版项目**：[ExTV/rikkahub-agent](https://github.com/ExTV/rikkahub-agent)

本仓库源自 Agent 原版，并持续检查和合并官方更新。本仓库的修改、构建及发布均由本仓库独立负责，不代表上述项目。

项目遵循 [GNU AGPL v3.0](LICENSE)。
