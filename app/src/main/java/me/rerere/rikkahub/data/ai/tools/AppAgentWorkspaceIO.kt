package me.rerere.rikkahub.data.ai.tools

import me.rerere.agenttools.AgentWorkspaceIO
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.workspace.WorkspaceFileEntry
import me.rerere.workspace.WorkspaceStorageArea

/**
 * 把 agent-tools 模块的 [AgentWorkspaceIO] 接到真实存储:
 * 绑定工作区的 Rootfs (LINUX 区), 内部走 WorkspaceRepository.
 */
class AppAgentWorkspaceIO(
    private val workspaceId: String,
    private val repository: WorkspaceRepository,
) : AgentWorkspaceIO {

    override suspend fun writeText(path: String, text: String, overwrite: Boolean) {
        repository.writeText(workspaceId, path, text, overwrite)
    }

    override suspend fun readText(path: String): String =
        repository.readText(workspaceId, path)

    override suspend fun listFiles(path: String): List<WorkspaceFileEntry> =
        repository.listFiles(workspaceId, WorkspaceStorageArea.LINUX, path)
}