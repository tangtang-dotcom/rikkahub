package me.rerere.search

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.search.SearchResult.SearchResultItem
import me.rerere.search.SearchService.Companion.httpClient
import me.rerere.search.SearchService.Companion.json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object AnySearchSearchService : SearchService<SearchServiceOptions.AnySearchOptions> {
    override val name: String = "AnySearch"

    @Composable
    override fun Description() {
        Text("AnySearch — 每日 1000 次免费。专为 AI Agent 设计。")
    }

    override fun parameters(options: SearchServiceOptions.AnySearchOptions): InputSchema? =
        InputSchema.Obj(
            properties = buildJsonObject {
                put("query", buildJsonObject {
                    put("type", "string")
                    put("description", "搜索关键词")
                })
            },
            required = listOf("query")
        )

    override fun scrapingParameters(options: SearchServiceOptions.AnySearchOptions): InputSchema? = null

    override suspend fun search(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.AnySearchOptions
    ): Result<SearchResult> = withContext(Dispatchers.IO) {
        runCatching {
            val query = params["query"]?.jsonPrimitive?.content ?: error("query is required")
            val apiKey = serviceOptions.apiKey.ifBlank { error("API Key is required") }

            val requestBody = buildJsonObject {
                put("query", query)
                put("count", commonOptions.resultSize)
            }.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("https://api.anysearch.ai/v1/search")
                .post(requestBody)
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: error("Empty response")
            val data = json.decodeFromString<AnySearchResponse>(body)

            val items = data.results?.map { item ->
                SearchResultItem(
                    title = item.title ?: "",
                    url = item.url ?: "",
                    text = item.snippet ?: item.content ?: ""
                )
            } ?: emptyList()

            SearchResult(answer = data.answer, items = items)
        }
    }

    override suspend fun scrape(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.AnySearchOptions
    ): Result<ScrapedResult> {
        return Result.failure(Exception("Scraping is not supported for AnySearch"))
    }

    @Serializable
    data class AnySearchResponse(
        val answer: String? = null,
        val results: List<AnySearchResultItem>? = null,
    )

    @Serializable
    data class AnySearchResultItem(
        val title: String? = null,
        val url: String? = null,
        val snippet: String? = null,
        val content: String? = null,
    )
}
