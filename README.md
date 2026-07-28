<div align="center">

<img src="docs/icon.png" width="96" height="96" alt="RikkaHub Agents" style="border-radius: 24px" />

# RikkaHub Agents

**Android 设备端 LLM 智能体 · 80+ 设备工具 · 工作流自动化 · Telegram Bot**

[**English**](README_EN.md) | [**简体中文**](README_ZH_CN.md) | [**繁體中文**](README_ZH_TW.md)

</div>

---

## 简介

基于 [RikkaHub（官方）](https://github.com/rikkahub/rikkahub) 和 [ExTV/rikkahub-agent（原版）](https://github.com/ExTV/rikkahub-agent) 的 Fork。将原生 Android LLM 聊天客户端变为真正的设备端智能体：用自然语言操控手机 — 点击、截图、短信、NFC、SSH、自动化工作流、Telegram Bot 远程控制。

> *"收到快递通知时，自动截图并保存到相册。"*
> *"每两小时截屏，持续 4 小时，看看下午都干了什么。"*
> *"连上公司 WiFi 后自动关闭个人 Telegram Bot。"*

---

## 功能

### 设备控制
80+ 工具：点击、滑动、打字、截图、亮度/音量、通知、电池/WiFi/信号/位置/传感器、联系人/短信、发送短信、壁纸、NFC 读写、ZIP 压缩 — 全部默认关闭。

### 工作流与定时任务
19 种触发器（WiFi、蓝牙、地理围栏、通知、时间等）+ 14 种条件，自然语言配置。定时任务重启后仍有效。

### Telegram Bot
远程对话，支持照片、PDF、语音、确认按钮，长消息自动打包。

### 内置浏览器
AI 自动操作 WebView — 点击弹窗、填表单、滚动、读取页面。

### 文件管理
查找、读写、复制、移动、删除。一句话搜全机 PDF。

### SSH 远程
聊天中运行命令、上传文件、跟踪日志。

### Skills / 子 Agent / MCP
拖入 Markdown Skill 扩展能力。长任务自动拆分并行。MCP 协议接入外部工具。

### 安全
三层保护：独立开关、调用批准、HARDLINE 底线规则。

---

## 系统要求

| | |
|---|---|
| 架构 | arm64 或 x86_64 |
| Android | 8.0+ (API 26) |
| 语言 | English、简中、繁中、日本語、한국어、Русский |

---

## 快速开始

1. [Releases](../../releases) 下载 APK
2. 安装 → 设置 → 提供商 → 添加 LLM
3. （可选）开启本地工具

---

## 来源

| 项目 | 地址 |
|------|------|
| 官方 RikkaHub | [rikkahub/rikkahub](https://github.com/rikkahub/rikkahub) |
| 原版 Fork | [ExTV/rikkahub-agent](https://github.com/ExTV/rikkahub-agent) |
| 本仓库 | [xiwangone/rikkahub-agents](https://github.com/xiwangone/rikkahub-agents) |

> ⚠️ 非官方发布。代码来自上游，固定签名编译，包名 `excp.rikkahub.agents`。

---

## 许可证

[GNU AGPL-3.0](LICENSE)
