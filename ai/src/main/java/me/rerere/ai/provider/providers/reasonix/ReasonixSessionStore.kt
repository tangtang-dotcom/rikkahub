package me.rerere.ai.provider.providers.reasonix

/**
 * Reasonix 会话路径存储——跨模块解耦接口。
 *
 * ai module 只定义接口（不依赖 Room / app module），app module 负责实现
 * （读写 ConversationEntity.backendSessionPath），经 DI 注入 [ReasonixProvider]。
 *
 * 用途：持久化「RikkaHub conversationId ↔ reasonix session path」映射，
 * 实现会话隔离——同一对话多轮 turn 复用同一 reasonix 会话，不同对话互不串扰，
 * 也不复用 reasonix serve 的「当前」会话。
 */
interface ReasonixSessionStore {
    /** 查 conversationId 对应的 reasonix session path；无则返回 null。 */
    suspend fun loadPath(conversationId: String): String?

    /** 保存 conversationId → reasonix session path 映射。 */
    suspend fun savePath(conversationId: String, path: String)
}
