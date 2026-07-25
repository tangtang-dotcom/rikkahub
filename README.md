<p align="center">
  <img src="docs/icon.png" width="120" height="120" alt="RikkaHub Agents" style="border-radius: 24px" />
</p>

<h1 align="center">RikkaHub Agents</h1>

<p align="center">
  <b>🤖 AI 自动维护 · ❌ 非官方 · ❌ 非原版</b>
</p>

<p align="center">
  <a href="README_EN.md">English</a> · <a href="README.md">中文</a>
</p>

<p align="center">
  <a href="https://github.com/xiwangone/rikkahub-agents/releases/tag/v2.4.3">
    <img src="https://img.shields.io/github/v/release/xiwangone/rikkahub-agents?style=flat-square&label=version&color=2196F3" alt="版本" />
  </a>
  <a href="https://github.com/xiwangone/rikkahub-agents/releases">
    <img src="https://img.shields.io/github/downloads/xiwangone/rikkahub-agents/total?style=flat-square&label=downloads&color=4CAF50" alt="下载" />
  </a>
  <a href="https://github.com/xiwangone/rikkahub-agents/blob/master/LICENSE">
    <img src="https://img.shields.io/badge/license-AGPL--3.0-FF5722?style=flat-square" alt="许可证" />
  </a>
  <img src="https://img.shields.io/badge/Android-8%2B-3DDC84?style=flat-square&logo=android" alt="Android" />
  <img src="https://img.shields.io/badge/arch-arm64%20%7C%20universal-9C27B0?style=flat-square" alt="架构" />
</p>

---

## 📥 下载

<p align="center">
  <a href="https://github.com/xiwangone/rikkahub-agents/releases/tag/v2.4.3">
    <img src="https://img.shields.io/badge/⬇_Download_APK-2196F3?style=for-the-badge&logo=android" alt="下载 APK" />
  </a>
</p>

| 版本 | 大小 | 说明 |
|:----:|:----:|:----:|
| 📱 **arm64-v8a** | ~55 MB | ✅ **推荐 — 绝大多数手机用这个** |
| 🌐 **universal** | ~76 MB | 通用包，兼容所有设备 |

> 💡 不确定选哪个？下载 **arm64-v8a** 就行，90% 的手机都是这个架构。

---

## 🚨 重要声明

| 项目 | 链接 |
|:----|:----|
| 🔵 **RikkaHub（官方）** | https://github.com/rikkahub/rikkahub |
| 🟢 **ExTV/rikkahub-agent（原版）** | https://github.com/ExTV/rikkahub-agent |
| 🟡 **本仓库（AI 维护版）** | https://github.com/xiwangone/rikkahub-agents |

> ⚠️ **本仓库由 AI 自动维护**，代码从官方仓库和原版 Fork 拉取后自动合并，通过 GitHub Actions 自动编译和签名 APK。
>
> - ❌ **非官方发布** — 不是 RikkaHub 官方团队发布
> - ❌ **非原版发布** — 不是 ExTV 原版开发者发布
> - ✅ 代码来源可信，但编译和发布过程由 AI 自动处理
> - ⚠️ 使用前请自行评估风险，本仓库不提供任何质量保证

---

## ✨ 功能一览

| 功能 | 说明 |
|:----|:----|
| 🤖 **80+ 设备工具** | 点击、滑动、截图、打开应用、调节音量、通知、传感器… |
| ⚡ **工作流引擎** | 19 种触发器 + 14 种条件，自然语言设置自动化 |
| ⏰ **定时任务** | "每周一 8 点"、"每两小时"，重启后仍有效 |
| 💬 **Telegram Bot** | 远程对话、发图片/PDF/语音，AI 确认按钮 |
| 🌐 **内置浏览器** | AI 自动操作网页，截图流式返回 |
| 📁 **文件管理** | 查找、编辑、复制、移动，一句话搞定 |
| 🔗 **SSH** | 保存服务器，聊天里跑命令、传文件 |
| 🧩 **子 Agent** | 复杂任务拆分为多个子任务并行处理 |
| 🔌 **MCP 服务器** | 连接 Model Context Protocol 扩展能力 |
| 🛡️ **安全隐私** | 工具默认关闭、操作需批准、危险命令无条件阻止 |

---

## 🚀 快速开始

### 1️⃣ 安装
从 [Releases](https://github.com/xiwangone/rikkahub-agents/releases) 下载 APK，打开安装。

> 包名 `excp.rikkahub.agents`，可与官方版/原版共存。

### 2️⃣ 配置 LLM
**设置 → 提供商 → 添加** → 选择 OpenAI 兼容或内置 LiteRT 本地模型。

### 3️⃣ 开启功能（可选）
**设置 → 助手 → 本地工具** → 按需开启。

### 4️⃣ Telegram Bot（可选）
向 [@BotFather](https://t.me/BotFather) 申请 Token，告诉助手配置即可。

---

## 🔗 相关链接

- [RikkaHub（官方）](https://github.com/rikkahub/rikkahub)
- [ExTV/rikkahub-agent（原版 Fork）](https://github.com/ExTV/rikkahub-agent)
- [GitHub Actions](https://github.com/xiwangone/rikkahub-agents/actions) — 自动构建日志

---

## 📄 许可证

**GNU Affero General Public License v3.0**

- ✅ 自由使用、修改、分发
- ✅ 可用于商业用途
- ⚠️ 网络提供服务必须公开源码
- ⚠️ 修改版必须使用相同许可证

完整文本见 [LICENSE](LICENSE)。
