<div align="center">

<img src="docs/icon.png" width="96" height="96" alt="RikkaHub Agents" style="border-radius: 24px" />

# RikkaHub Agents

**🤖 AI-maintained fork · ❌ NOT official · ❌ NOT original**

[**English**](README_EN.md) | [**中文**](README.md)

</div>

---

## 🚨 DISCLAIMER

| Project | Link | Description |
|---------|------|-------------|
| 🔵 **RikkaHub (Official)** | https://github.com/rikkahub/rikkahub | Official upstream, source of this code |
| 🟢 **ExTV/rikkahub-agent (Original Fork)** | https://github.com/ExTV/rikkahub-agent | Original fork this repo is based on |
| 🟡 **This repo (AI-maintained)** | https://github.com/xiwangone/rikkahub-agents | AI auto-merged + compiled |

> ### 🚨🚨🚨 WARNING
>
> **This repository is AI-maintained.** Code is pulled from the official upstream and original fork, auto-merged, and compiled via GitHub Actions.
>
> - **❌ NOT official release** — Not from the RikkaHub team
> - **❌ NOT original release** — Not from the ExTV developer
> - ✅ Code sources are trustworthy (official + original), but compilation and release are **AI-automated**
> - **⚠️ Use at your own risk. No quality guarantee.**
> - **💡 For issues, please use [Official](https://github.com/rikkahub/rikkahub) or [Original Fork](https://github.com/ExTV/rikkahub-agent) first.**

---

## Overview

A fork that turns the native Android LLM chat client into a full on-device agent: **80+ device tools**, AI-driven workflows, scheduled jobs, an in-app browser the AI controls, SSH, screen automation, file manager, music player, voice transcription, downloadable on-device LLMs, and a remote Telegram bot. All features start disabled.

> *"Export my todos to a Markdown file in the workspace."*
> *"Take a screenshot every 2 hours for the next 4 hours."*
> *"When I receive a delivery notification, screenshot and save it."*
> *"When I connect to my work WiFi, disable the personal Telegram bot."*
> *"Write a Python script in Termux to check the weather forecast."*

Each is a one-line setup.

---

## Features

### Device Control
Tap, swipe, scroll, type, screenshot, open apps, adjust brightness/volume, check battery/WiFi/signal/location/sensors, read contacts & SMS, send SMS, set wallpaper, NFC, ZIP management. **80+ tools**.

### Workflows & Schedules
**Workflows** — Natural language triggers & actions: *"When I get home, silence the ringer."* 19 triggers, 14 conditions.

**Schedules** — *"Every Monday at 8am"*, *"Every 2 hours"*. Survives reboots.

### Telegram Bot
Chat with your assistant from anywhere. Send text, photos, PDFs, voice notes. Yes/No approval buttons.

### In-App Browser
A real browser driven by AI. Auto-clicks cookie banners, fills search boxes, scrolls, reads pages.

### File Manager
Find, read, save, copy, move, rename, delete files.

### SSH
Save servers, run commands, upload files, pull backups — all from chat.

### Sub-Agents
Long tasks dispatch to focused sub-agents in parallel.

### MCP Servers
Connect Model Context Protocol servers.

### Safety & Privacy
1. Per-assistant toggles — all tools start disabled
2. Per-call approval for modifying actions
3. HARDLINE floor — dangerous commands blocked unconditionally

---

## Quick Start

### 1. Download APK
From **Releases** or **Actions** page.

### 2. Install
Open APK, allow unknown sources.

### 3. Configure
Settings → Providers → Add → OpenAI-compatible or LiteRT local model.

### 4. Enable Tools (optional)
Settings → Assistants → Local Tools.

---

## Requirements

| | |
|---|---|
| **Architecture** | arm64 or x86_64 |
| **Android** | 8.0+ (API 26) |
| **Storage** | ~80 MB |

---

## Credits

- **[RikkaHub (Official)](https://github.com/rikkahub/rikkahub)** — Upstream project
- **[ExTV/rikkahub-agent (Original Fork)](https://github.com/ExTV/rikkahub-agent)** — Original fork
- **GitHub Actions** — Auto build & release

---

## License

**GNU Affero General Public License v3.0 (AGPL-3.0)**

See [LICENSE](LICENSE).
