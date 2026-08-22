# agent-tools 模块

独立 Gradle 模块，为绑定工作区提供一组**通用、可复用**的 Agent 工具能力，
与宿主实现完全解耦。只依赖 `:ai`（`Tool`/`InputSchema`）与 `:workspace`（`WorkspaceFileEntry`），
不依赖 `:app`，因此可独立演进、单独测试。

## 能力

### 1. 大输出落盘：`workspace_offload` / `workspace_offload_list`
- 长日志、完整文件 dump、diff、转录、生成代码 → 写入绑定的 `offloads/` 目录
- 只回显 400 字符 `preview`，完整内容落盘，避免撑爆上下文
- 常量 `OFFLOAD_HINT_BYTES`（12KB）：可放系统提示词供模型"超此长度先落盘"的纪律

### 2. 键值配置即工具：`config_get_all` / `config_set` / `config_revert`
- JSON 存储（`config/settings.json`）+ 追加式审计（`config/audit.jsonl`）
- 每次写入记审计条目，支持按 key 回滚
- 通用于"会话/助手级键值配置"；如需对接 App 全局真实偏好，在适配层相应实现即可

## 解耦设计

模块对外只依赖一个薄接口 `AgentWorkspaceIO`：

```kotlin
interface AgentWorkspaceIO {
    suspend fun writeText(path: String, text: String, overwrite: Boolean)
    suspend fun readText(path: String): String
    suspend fun listFiles(path: String): List<WorkspaceFileEntry>
}
```

宿主 App 提供实现：
- `app/…/data/ai/tools/AppAgentWorkspaceIO.kt` —— 基于 `WorkspaceRepository`（LINUX 区）

聚合入口：
```kotlin
createAgentTools(io: AgentWorkspaceIO, approvalOverrides) // -> List<Tool>
```

## 接线（已完成）
1. `settings.gradle.kts` 已 `include(":agent-tools")`
2. `app/build.gradle.kts` 已 `implementation(project(":agent-tools"))`
3. `WorkspaceTools.createWorkspaceTools` 末尾追加：
   ```kotlin
   ) + me.rerere.agenttools.createAgentTools(
       AppAgentWorkspaceIO(workspaceId, workspaceRepository),
       approvalOverrides,
   )
   ```
4. 审批默认值在 `AgentToolsDefaultApprovals`（默认一律需授权），与该 App 的
   per-assistant toggles / default-OFF 体系一致。

## 命名说明
`WorkspaceTools.kt` 内的 `readTextInRootfs` / `writeTextInRootfs` / `readImageInRootfs` 等
是该文件**自带的 `private` 扩展函数**（定义于同文件约 324–395 行），非未定义调用。
本模块通过 `AppAgentWorkspaceIO` 直接使用仓库真实 API（`readText`/`writeText`/`listFiles`），
与既有实现并行、互不干扰。