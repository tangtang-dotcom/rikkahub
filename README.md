<div align="center">
  <img src="docs/logo.svg" alt="RikkaHub Agents" width="100" height="100" />
  <h1>RikkaHub Agents</h1>

A maintained Android Agent fork of RikkaHub with automated CI builds,
upstream sync, and full device-tooling capabilities 🤖

[简体中文](README_ZH_CN.md) | English

[![Build](https://img.shields.io/github/actions/workflow/status/xiwangone/rikkahub-agents/build-apk.yml?style=flat&logo=githubactions&label=Build)](https://github.com/xiwangone/rikkahub-agents/actions/workflows/build-apk.yml)
[![Upstream](https://img.shields.io/github/actions/workflow/status/xiwangone/rikkahub-agents/merge-upstream.yml?style=flat&logo=git&label=Upstream)](https://github.com/xiwangone/rikkahub-agents/actions/workflows/merge-upstream.yml)
[![CodeQL](https://img.shields.io/github/actions/workflow/status/xiwangone/rikkahub-agents/codeql.yml?style=flat&logo=github&label=CodeQL)](https://github.com/xiwangone/rikkahub-agents/actions/workflows/codeql.yml)
[![Release](https://img.shields.io/github/v/release/xiwangone/rikkahub-agents?style=flat&logo=github)](https://github.com/xiwangone/rikkahub-agents/releases)
[![License](https://img.shields.io/github/license/xiwangone/rikkahub-agents?style=flat)](LICENSE)
[![Last Commit](https://img.shields.io/github/last-commit/xiwangone/rikkahub-agents?style=flat&label=Updated)](https://github.com/xiwangone/rikkahub-agents/commits/master)

</div>

<div align="center">
  <img src="docs/img/chat.png" alt="Chat Interface" width="150" />
  <img src="docs/img/desktop.png" alt="Desktop / models" width="450" />
</div>

## 🚀 Download

🔗 [Releases](https://github.com/xiwangone/rikkahub-agents/releases) — latest signed APKs  
🔗 [Actions artifacts](https://github.com/xiwangone/rikkahub-agents/actions) — per-run build outputs

> [!WARNING]
> This is an **unofficial fork**. Issues in forks are unrelated to upstream RikkaHub.
> Use third-party builds carefully to avoid privacy risks or excessive permissions.

## ✨ What this fork adds

| Capability | Description |
|:--|:--|
| 🤖 **Automated maintenance** | Workflows for Release builds and scheduled upstream merge |
| 🔧 **Agent tooling** | SSH, Termux, device tools, Telegram bot integrations from the agent lineage |
| 🔐 **Stable CI signing** | Fixed keystore via repository Secrets — successive builds can overwrite-install |
| 🔍 **Code scanning** | Weekly / manual CodeQL (no scan on every push, to save runner time) |

## ✨ Features (from RikkaHub)

- 🎨 Material You design and 🌙 dark mode
- 📦 Workspace: a proot-based Linux agent environment
- 🔄 Multiple AI providers (OpenAI / Google / Anthropic compatible APIs)
- 🖼️ Multimodal input (images, PDF, DOCX, …)
- 🖥️ Embedded web access
- 🛠️ MCP support
- 📝 Markdown (code highlight, LaTeX, tables, Mermaid)
- 🔍 Search providers (Exa, Tavily, Zhipu, Brave, …)
- 🧩 Prompt variables, agent customization, memory

## ⚡ Build

```bash
git clone --recurse-submodules https://github.com/xiwangone/rikkahub-agents.git
cd rikkahub-agents
./gradlew assembleDebug
```

> [!TIP]
> Place `google-services.json` under `app/` to build.  
> Building the `web` module requires **pnpm** for `web-ui/`.

### CI note

- Workflows live under `.github/workflows/` and are part of the git tree — a normal `git clone` / fork includes them.
- Secrets (`KEYSTORE_*`, notification keys, optional `PAT_TOKEN`) stay on GitHub and are **never** cloned.
- Signed Release builds: Actions → **Build APK** → Run workflow.

## ✨ Contributing

Developed with [Android Studio](https://developer.android.com/studio). PRs that fix bugs or docs are welcome.

Stack: Kotlin · Koin · Jetpack Compose · DataStore · Room · Coil · Material You · Navigation 3 · OkHttp · kotlinx.serialization

> [!IMPORTANT]
> Please open issues/PRs **here**, not on upstream RikkaHub, for fork-specific problems.

## 🙏 Acknowledgments

- [**RikkaHub**](https://github.com/rikkahub/rikkahub) — official client
- [**ExTV/rikkahub-agent**](https://github.com/ExTV/rikkahub-agent) — agent-mode fork this tree started from

## 📄 License

[GNU Affero General Public License v3.0](LICENSE) (AGPL-3.0)
