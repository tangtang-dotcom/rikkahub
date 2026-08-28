package me.rerere.rikkahub.data.ai.tools

import me.rerere.rikkahub.data.terminal.AndroidRootTerminalController
import me.rerere.rikkahub.data.terminal.TerminalResult
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * 个人数据读取只绑定已验证的系统或厂商 Provider，绝不向模型开放 URI、表名或 SQL。
 * Root 用于调用受签名权限保护的 Provider；查询条件始终由固定列和受控关键词组成。
 */
internal class StructuredPersonalDataSource(
    private val root: AndroidRootTerminalController,
) {
    fun execute(name: String, args: JSONObject): String? =
        when (name) {
            "search_media" -> searchMedia(args)
            "search_audio" -> searchAudio(args, recordingsOnly = false)
            "search_recordings" -> searchAudio(args, recordingsOnly = true)
            "search_files" -> searchFiles(args)
            "search_calendar_events" -> searchCalendarEvents(args)
            "search_contacts" -> searchContacts(args)
            "search_call_history" -> searchCallHistory(args)
            "search_messages" -> searchMessages(args)
            "search_downloads" -> searchDownloads(args)
            "search_qq_chat_images" -> searchQqChatImages(args)
            "search_wechat_chat_images" -> searchWechatChatImages(args)
            else -> null
        }

    private fun searchMedia(args: JSONObject): String = query(
        tool = "search_media",
        uri = "content://media/external/file",
        projection = listOf("_id", "_display_name", "mime_type", "relative_path", "datetaken", "date_modified", "_size"),
        sort = "date_modified DESC",
        searchableColumns = listOf("_display_name", "relative_path"),
        fixedWhere = "media_type=1",
        args = args,
    ) { row ->
        row.put("uri", "content://media/external/file/${row.optString("_id")}")
    }

    private fun searchAudio(args: JSONObject, recordingsOnly: Boolean): String = query(
        tool = if (recordingsOnly) "search_recordings" else "search_audio",
        uri = "content://media/external/audio/media",
        projection = listOf("_id", "title", "_display_name", "artist", "album", "relative_path", "duration", "date_modified", "_size"),
        sort = "date_modified DESC",
        searchableColumns = listOf("title", "_display_name", "artist", "relative_path"),
        fixedWhere = if (recordingsOnly) "relative_path LIKE '%Record%'" else null,
        args = args,
    ) { row ->
        row.put("uri", "content://media/external/audio/media/${row.optString("_id")}")
    }

    private fun searchFiles(args: JSONObject): String = query(
        tool = "search_files",
        uri = "content://media/external/file",
        projection = listOf("_id", "_display_name", "mime_type", "relative_path", "date_modified", "_size"),
        sort = "date_modified DESC",
        searchableColumns = listOf("_display_name", "relative_path"),
        fixedWhere = "media_type=0",
        args = args,
    ) { row ->
        row.put("uri", "content://media/external/file/${row.optString("_id")}")
    }

    private fun searchCalendarEvents(args: JSONObject): String = query(
        tool = "search_calendar_events",
        uri = "content://com.android.calendar/events",
        projection = listOf("_id", "title", "description", "eventLocation", "dtstart", "dtend", "allDay", "calendar_displayName"),
        sort = "dtstart DESC",
        searchableColumns = listOf("title", "description", "eventLocation"),
        fixedWhere = "deleted=0",
        args = args,
    )

    private fun searchContacts(args: JSONObject): String = query(
        tool = "search_contacts",
        uri = "content://com.android.contacts/contacts",
        projection = listOf("_id", "display_name", "lookup", "has_phone_number", "contact_last_updated_timestamp"),
        sort = "display_name COLLATE LOCALIZED ASC",
        searchableColumns = listOf("display_name"),
        fixedWhere = null,
        args = args,
    ) { row ->
        row.put("uri", "content://com.android.contacts/contacts/${row.optString("_id")}")
    }

    private fun searchCallHistory(args: JSONObject): String = query(
        tool = "search_call_history",
        uri = "content://call_log/calls",
        projection = listOf("_id", "number", "name", "date", "duration", "type", "geocoded_location"),
        sort = "date DESC",
        searchableColumns = listOf("number", "name"),
        fixedWhere = null,
        args = args,
    )

    private fun searchMessages(args: JSONObject): String = query(
        tool = "search_messages",
        uri = "content://sms",
        projection = listOf("_id", "thread_id", "address", "body", "date", "type", "read"),
        sort = "date DESC",
        searchableColumns = listOf("address", "body"),
        fixedWhere = null,
        args = args,
    )

    private fun searchDownloads(args: JSONObject): String = query(
        tool = "search_downloads",
        uri = "content://downloads/my_downloads",
        projection = listOf("_id", "title", "description", "mime_type", "total_size", "lastmod", "status", "local_uri"),
        sort = "lastmod DESC",
        searchableColumns = listOf("title", "description"),
        fixedWhere = null,
        args = args,
    )


    private fun searchQqChatImages(args: JSONObject): String = searchPrivateChatImages(
        tool = "search_qq_chat_images",
        directory = QQ_CHAT_IMAGES_DIRECTORY,
        pathFilter = "\\( -path '*/chatimg/*' -o -path '*/chatraw/*' -o -path '*/chatthumb/*' \\)",
        unavailableCode = "QQ_CHAT_IMAGES_UNAVAILABLE",
        unavailableMessage = "QQ 聊天图片缓存暂时不可访问",
        args = args,
        kind = ::qqImageKind,
    )

    private fun searchWechatChatImages(args: JSONObject): String = searchPrivateChatImages(
        tool = "search_wechat_chat_images",
        directory = WECHAT_CHAT_IMAGES_DIRECTORY,
        pathFilter = "-path '*/image/*'",
        unavailableCode = "WECHAT_CHAT_IMAGES_UNAVAILABLE",
        unavailableMessage = "微信聊天图片缓存暂时不可访问",
        args = args,
        kind = { "image" },
    )

    private fun searchPrivateChatImages(
        tool: String,
        directory: String,
        pathFilter: String,
        unavailableCode: String,
        unavailableMessage: String,
        args: JSONObject,
        kind: (String) -> String,
    ): String {
        val result = root.executeSync(
            "test -d $directory && " +
                "find $directory -type f $pathFilter -size -${CHAT_IMAGE_MAX_FILE_BYTES}c " +
                "-printf '%T@|%s|%p\\n' 2>/dev/null | sort -rn | head -n $CHAT_IMAGE_CANDIDATE_LIMIT",
            timeoutMs = QUERY_TIMEOUT_MS
        )
        if ((result.exitCode != 0 || result.timedOut)) return sensitive(error(unavailableCode, unavailableMessage))

        val keyword = args.optString("query").trim().lowercase(Locale.ROOT)
        val limit = args.optInt("limit", DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)
        val candidates = result.stdout.lineSequence()
            .mapNotNull { privateChatImageRow(it, directory, kind) }
            .toList()
        val items = candidates.asSequence()
            .filter { keyword.isBlank() || it.optString("path").lowercase(Locale.ROOT).contains(keyword) }
            .take(limit)
            .toList()
        return sensitive(
            JSONObject()
                .put("ok", true)
                .put("tool", tool)
                .put("items", JSONArray(items))
                .put("count", items.size)
                .put("truncated", result.truncated || candidates.size == CHAT_IMAGE_CANDIDATE_LIMIT || items.size == limit)
                .toString(),
        )
    }

    private fun query(
        tool: String,
        uri: String,
        projection: List<String>,
        sort: String,
        searchableColumns: List<String>,
        fixedWhere: String?,
        args: JSONObject,
        transform: (JSONObject) -> Unit = {},
    ): String {
        val limit = args.optInt("limit", DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)
        val keyword = args.optString("query").trim()
        val where = combineWhere(fixedWhere, keyword.takeIf(String::isNotBlank)?.let {
            searchableColumns.likeClause(it)
        })
        val command = buildString {
            append("content query --uri ").append(shellQuote(uri))
            append(" --projection ").append(shellQuote(projection.joinToString(":")))
            where?.let { append(" --where ").append(shellQuote(it)) }
            append(" --sort ").append(shellQuote(sort))
        }
        val result = root.executeSync(command, timeoutMs = QUERY_TIMEOUT_MS)
        if ((result.exitCode != 0 || result.timedOut) || PersonalDataContentParser.hasProviderFailure(result.stdout, result.stderr)) {
            return sensitive(error(rootErrorCode(result), "个人数据源暂时不可访问"))
        }
        val items = PersonalDataContentParser.parseRows(result.stdout, projection)
            .take(limit)
            .map { row -> row.also(transform) }
        return sensitive(
            JSONObject()
                .put("ok", true)
                .put("tool", tool)
                .put("items", JSONArray(items))
                .put("count", items.size)
                .put("truncated", result.truncated || items.size == limit)
                .toString(),
        )
    }

    private fun List<String>.likeClause(keyword: String): String {
        val escaped = keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_").replace("'", "''")
        val value = "'%$escaped%'"
        return joinToString(" OR ", prefix = "(", postfix = ")") { column ->
            "LOWER($column) LIKE LOWER($value) ESCAPE '\\'"
        }
    }

    private fun combineWhere(first: String?, second: String?): String? = when {
        first == null -> second
        second == null -> first
        else -> "($first) AND ($second)"
    }

    private fun privateChatImageRow(
        line: String,
        directory: String,
        kind: (String) -> String,
    ): JSONObject? {
        val fields = line.split('|', limit = 3)
        if (fields.size != 3) return null
        val modifiedAt = fields[0].toDoubleOrNull()?.toLong() ?: return null
        val size = fields[1].toLongOrNull() ?: return null
        val path = fields[2]
        if (!path.startsWith("$directory/")) return null
        return JSONObject()
            .put("path", path)
            .put("name", path.substringAfterLast('/'))
            .put("kind", kind(path))
            .put("modified_at_epoch_seconds", modifiedAt)
            .put("size_bytes", size)
    }

    private fun qqImageKind(path: String): String = when {
        "/chatraw/" in path -> "original"
        "/chatimg/" in path -> "image"
        "/chatthumb/" in path -> "thumbnail"
        else -> "other"
    }

    private fun rootErrorCode(result: TerminalResult): String = if (result.timedOut) "PERSONAL_DATA_QUERY_TIMEOUT" else "PERSONAL_DATA_UNAVAILABLE"

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private fun error(code: String, message: String): String =
        JSONObject().put("ok", false).put("code", code).put("message", message).toString()

    private fun sensitive(content: String) = content

    private companion object {
        const val DEFAULT_LIMIT = 10
        const val MAX_LIMIT = 30
        const val QUERY_TIMEOUT_MS = 15_000L
        const val MAX_OUTPUT_BYTES = 512 * 1024
        const val QQ_CHAT_IMAGES_DIRECTORY = "/storage/emulated/0/Android/data/com.tencent.mobileqq/Tencent/MobileQQ/chatpic"
        const val WECHAT_CHAT_IMAGES_DIRECTORY = "/storage/emulated/0/Android/data/com.tencent.mm/MicroMsg"
        const val CHAT_IMAGE_CANDIDATE_LIMIT = 120
        const val CHAT_IMAGE_MAX_FILE_BYTES = 20L * 1024L * 1024L
    }

}

internal object PersonalDataContentParser {
    fun hasProviderFailure(stdout: String, stderr: String): Boolean =
        sequenceOf(stdout, stderr).any { output ->
            output.contains("Error while accessing provider:") ||
                output.contains("java.lang.IllegalArgumentException:") ||
                output.contains("java.lang.SecurityException:")
        }

    fun parseRows(source: String, columns: List<String>): List<JSONObject> =
        source.lineSequence()
            .filter { it.startsWith("Row:") }
            .map { line ->
                JSONObject().also { row ->
                    columns.forEach { column -> value(line, column, columns)?.let { row.put(column, it) } }
                }
            }
            .toList()

    private fun value(line: String, column: String, columns: List<String>): String? {
        val following = columns.filterNot { it == column }.joinToString("|") { Regex.escape(it) }
        return Regex("(?:^|,\\s*|\\s)${Regex.escape(column)}=(.*?)(?=,\\s*(?:$following)=|$)")
            .find(line)
            ?.groupValues
            ?.get(1)
            ?.takeUnless { it == "null" }
    }
}
