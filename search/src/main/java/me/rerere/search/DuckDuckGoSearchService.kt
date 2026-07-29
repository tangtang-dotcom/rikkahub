package me.rerere.search

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.search.SearchResult.SearchResultItem
import org.jsoup.Jsoup
import java.net.URLEncoder

/**
 * Free search via DuckDuckGo HTML scraping. No API key required.
 * Uses the lite version for cleaner HTML parsing.
 */
object DuckDuckGoSearchService : SearchService<SearchServiceOptions.DuckDuckGoOptions> {
    override val name: String = "DuckDuckGo"

    @Composable
    override fun Description() {
        Text("DuckDuckGo — 免费，无需 API Key。隐私优先搜索，通过 HTML 解析获取结果。")
    }

    override fun parameters(options: SearchServiceOptions.DuckDuckGoOptions): InputSchema? =
        InputSchema.Obj(
            properties = buildJsonObject {
                put("query", buildJsonObject {
                    put("type", "string")
                    put("description", "搜索关键词")
                })
            },
            required = listOf("query")
        )

    override fun scrapingParameters(options: SearchServiceOptions.DuckDuckGoOptions): InputSchema? = null

    override suspend fun search(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.DuckDuckGoOptions
    ): Result<SearchResult> = withContext(Dispatchers.IO) {
        runCatching {
            val query = params["query"]?.jsonPrimitive?.content ?: error("query is required")
            val url = "https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(query, "UTF-8")

            val doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "text/html,application/xhtml+xml")
                .timeout(8000)
                .get()

            val elements = doc.select(".result")
            if (elements.isEmpty()) {
                // Fallback: try other selectors for different DuckDuckGo layouts
                val alt = doc.select(".web-result, article[data-testid=result]")
                if (alt.isEmpty()) {
                    error("Search failed: no results found (page structure changed?)")
                }
            }

            val results = doc.select(".result").map { element ->
                val titleEl = element.selectFirst(".result__title, .result__a, h2")
                val linkEl = element.selectFirst(".result__url, .result__snippet a, a.result__a")
                val snippetEl = element.selectFirst(".result__snippet")

                SearchResultItem(
                    title = titleEl?.text()?.trim() ?: "",
                    url = linkEl?.attr("href")?.trim() ?: "",
                    text = snippetEl?.text()?.trim() ?: ""
                )
            }.filter { it.title.isNotBlank() && it.url.isNotBlank() }
                .take(commonOptions.resultSize)

            require(results.isNotEmpty()) {
                "Search failed: no results found"
            }

            SearchResult(items = results)
        }
    }

    override suspend fun scrape(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.DuckDuckGoOptions
    ): Result<ScrapedResult> {
        return Result.failure(Exception("Scraping is not supported for DuckDuckGo"))
    }
}