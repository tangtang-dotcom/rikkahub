package me.rerere.agenttools

import me.rerere.workspace.WorkspaceFileEntry

/**
 * 让本模块不依赖宿主 App 的薄接口。
 *
 * 由宿主 App 提供实现（例如基于 WorkspaceRepository 的适配器），
 * 这样本模块只依赖 :ai + :workspace，宿主可自由适配自己的存储。
 */
interface AgentWorkspaceIO {
    suspend fun writeText(path: String, text: String, overwrite: Boolean)
    suspend fun readText(path: String): String
    suspend fun listFiles(path: String): List<WorkspaceFileEntry>
}