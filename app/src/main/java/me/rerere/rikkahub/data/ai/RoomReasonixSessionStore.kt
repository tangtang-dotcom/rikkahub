package me.rerere.rikkahub.data.ai

import me.rerere.ai.provider.providers.reasonix.ReasonixSessionStore
import me.rerere.rikkahub.data.db.dao.ConversationDAO

/**
 * [ReasonixSessionStore] 的 Room 实现——读写 ConversationEntity.backendSessionPath。
 *
 * 跨模块桥接：ai module 只定义接口（不依赖 Room），app module 用 Room 实现并注入。
 * 用于 reasonix 会话隔离的「conversationId ↔ session path」持久化映射。
 */
class RoomReasonixSessionStore(
    private val conversationDao: ConversationDAO,
) : ReasonixSessionStore {
    override suspend fun loadPath(conversationId: String): String? =
        conversationDao.getConversationById(conversationId)?.backendSessionPath?.takeIf { it.isNotBlank() }

    override suspend fun savePath(conversationId: String, path: String) {
        val conversation = conversationDao.getConversationById(conversationId) ?: return
        conversationDao.update(conversation.copy(backendSessionPath = path))
    }
}
