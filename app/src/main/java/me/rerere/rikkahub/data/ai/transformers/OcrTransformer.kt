package me.rerere.rikkahub.data.ai.transformers

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.common.cache.LruCache
import me.rerere.common.cache.SingleFileCacheStore
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.io.File
import kotlin.time.Duration.Companion.days

private const val TAG = "OcrTransformer"

// Hard ceiling on a single OCR/vision describe call. Without it, an OCR model that is
// misconfigured, dead, or itself not vision-capable blocks the whole generation forever.
// On Telegram that wedges the per-chat mutex, so every later message queues until the user
// sends /new — the symptom this bound exists to prevent.
private const val OCR_TIMEOUT_MS = 60_000L

object OcrTransformer : InputMessageTransformer, KoinComponent {
    /** 本地 ML Kit recognizer（复用实例，避免每次新建/关闭） */
    private val chineseRecognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }
    private val latinRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    private val cache by lazy {
        val context = get<Context>()
        val json = Json { allowStructuredMapKeys = true }
        val store = SingleFileCacheStore(
            file = File(context.cacheDir, "ocr_cache.json"),
            keySerializer = String.serializer(),
            valueSerializer = String.serializer(),
            json = json
        )
        LruCache(
            capacity = 64,
            store = store,
            deleteOnEvict = true,
            preloadFromStore = true,
            expireAfterWriteMillis = 3.days.inWholeMilliseconds,
        )
    }

    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        if (ctx.model.inputModalities.contains(Modality.IMAGE)) {
            return messages
        }

        val hasImages = messages.any { message ->
            message.parts.any { it is UIMessagePart.Image && (it.url.startsWith("file:") || it.url.startsWith("content:")) }
        }
        if (!hasImages) return messages

        return withContext(Dispatchers.IO) {
            try {
                // 仅当存在未被缓存的图片（真正需要 OCR）时才显示提示；历史图片已缓存则跳过，避免每次无图对话都出现"正在识别图片"
                val needsActualOcr = messages.any { message ->
                    message.parts.any {
                        it is UIMessagePart.Image && (it.url.startsWith("file:") || it.url.startsWith("content:")) && cache.get(it.url) == null
                    }
                }
                val settings = get<SettingsStore>().settingsFlow.value
                val localOcrEnabled = settings.ocrLocalEnabled
                if (needsActualOcr) {
                    // 区分提示：本地 OCR 开启时先显示本地识别，回退 AI 时由 performOcr 切换
                    ctx.processingStatus.value = if (localOcrEnabled) {
                        ctx.context.getString(R.string.ocr_status_local_recognizing)
                    } else {
                        ctx.context.getString(R.string.ocr_status_ai_recognizing)
                    }
                }
                messages.map { message ->
                    message.copy(
                        parts = message.parts.map { part ->
                            when {
                                part is UIMessagePart.Image && (part.url.startsWith("file:") || part.url.startsWith("content:")) -> {
                                    UIMessagePart.Text(
                                        performOcr(
                                            part,
                                            onFallbackToAi = {
                                                ctx.processingStatus.value = ctx.context.getString(R.string.ocr_status_ai_recognizing)
                                            },
                                        )
                                    )
                                }

                                else -> part
                            }
                        }
                    )
                }
            } finally {
                ctx.processingStatus.value = null
            }
        }
    }

    suspend fun performOcr(
        part: UIMessagePart.Image,
        onFallbackToAi: () -> Unit = {},
    ): String = runCatching {
        // Check cache first
        cache.get(part.url)?.let { cachedResult ->
            Log.i(TAG, "performOcr: Using cached result for ${part.url}")
            return cachedResult
        }

        // 本地 ML Kit OCR 优先（离线、免费、稳定）
        val settings = get<SettingsStore>().settingsFlow.value
        val localOcrEnabled = settings.ocrLocalEnabled
        val localResult = if (localOcrEnabled) performLocalOcr(part.url) else null
        if (!localResult.isNullOrBlank()) {
            val ocrResult = """
                <image_file_ocr>
                   $localResult
                </image_file_ocr>
                * The image_file_ocr tag contains a description of an image that the user uploaded to you, not the user's prompt.
            """.trimIndent()
            cache.put(part.url, ocrResult)
            return ocrResult
        }
        Log.i(TAG, "performOcr: local OCR empty, falling back to AI OCR")
        onFallbackToAi()

        val model = settings.findModelById(settings.ocrModelId) ?: return "[Image]"
        val providerSetting = model.findProvider(settings.providers) ?: return "[Image]"
        val provider = get<ProviderManager>().getProviderByType(providerSetting)
        val result = withTimeoutOrNull(OCR_TIMEOUT_MS) {
            provider.generateText(
                providerSetting = providerSetting,
                messages = listOf(
                    UIMessage.system(settings.ocrPrompt),
                    UIMessage(
                        role = MessageRole.USER,
                        parts = listOf(UIMessagePart.Image(part.url))
                    )
                ),
                params = TextGenerationParams(
                    model = model,
                    customHeaders = model.customHeaders,
                    customBody = model.customBodies,
                ),
            )
        }
        if (result == null) {
            Log.w(TAG, "performOcr: timed out after ${OCR_TIMEOUT_MS}ms for ${part.url}")
            // Not cached: a timeout is usually transient/config-related, so a later retry
            // should be allowed to reach the model again.
            return "[Image: could not be read — the OCR model did not respond in time]"
        }
        val content = result.choices[0].message?.toText() ?: "[ERROR, OCR failed]"
        Log.i(TAG, "performOcr: $content")
        val ocrResult = """
            <image_file_ocr>
               $content
            </image_file_ocr>
            * The image_file_ocr tag contains a description of an image that the user uploaded to you, not the user's prompt.
        """.trimIndent()

        // Cache the result
        cache.put(part.url, ocrResult)
        return ocrResult
    }.getOrElse {
        // Let a real cancellation (e.g. the user's /stop) propagate instead of swallowing
        // it into a fake OCR-failure string, which would defeat cooperative cancellation.
        if (it is kotlinx.coroutines.CancellationException) throw it
        "[ERROR, OCR failed: $it]"
    }

    /**
     * 本地 ML Kit OCR：中文模型 + 拉丁模型都跑，合并去重（覆盖中日韩+英文混排）。
     * 返回 null 表示无法识别（模型未下载/图片解码失败），由调用方决定回退 AI。
     */
    private suspend fun performLocalOcr(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val context = get<Context>()
            val image: InputImage = when {
                // 绝对路径（文件管理器返回 /storage/emulated/0/... 等，无 scheme）
                url.startsWith("/") || (!url.startsWith("file:") && !url.startsWith("content:") && url.startsWith("file:///") == false && android.net.Uri.parse(url).scheme == null) -> {
                    InputImage.fromFilePath(context, android.net.Uri.fromFile(java.io.File(url)))
                }
                url.startsWith("file://") -> InputImage.fromFilePath(context, Uri.parse(url))
                url.startsWith("content://") -> {
                    // 采样解码（防高清相册大图 OOM）：先量尺寸，再按需采样（最长边 <= 2048）
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(Uri.parse(url))?.use { ins ->
                        BitmapFactory.decodeStream(ins, null, bounds)
                    }
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                        Log.w(TAG, "performLocalOcr: content:// 解码失败（bounds 无尺寸）: $url")
                        return@runCatching null
                    }
                    var sample = 1
                    while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) {
                        sample *= 2
                    }
                    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                    val bitmap = context.contentResolver.openInputStream(Uri.parse(url))?.use { ins ->
                        BitmapFactory.decodeStream(ins, null, opts)
                    }
                    if (bitmap == null) {
                        Log.w(TAG, "performLocalOcr: content:// 采样解码失败（bitmap null）: $url")
                        return@runCatching null
                    }
                    // ML Kit 16.x 已移除单参 fromBitmap(Bitmap) 重载；decodeStream 产出的位图
                    // 未应用 EXIF 旋转，rotationDegrees 传 0 保持原语义
                    InputImage.fromBitmap(bitmap, 0)
                }
                else -> {
                    Log.w(TAG, "performLocalOcr: 不支持的 URL 格式: $url")
                    return@runCatching null
                }
            }

            // 中文模型（涵盖中日韩字符）+ 拉丁模型（英文等）同时识别，合并结果（复用实例，避免每次新建）
            val chineseText = runCatching { chineseRecognizer.process(image).await() }.onFailure { e -> Log.w(TAG, "performLocalOcr: 中文识别异常", e) }.getOrNull()?.text?.trim()
            val latinText = runCatching { latinRecognizer.process(image).await() }.onFailure { e -> Log.w(TAG, "performLocalOcr: 拉丁识别异常", e) }.getOrNull()?.text?.trim()

            // 合并去重：保留行级并集，按出现顺序
            val combined = LinkedHashSet<String>()
            listOfNotNull(chineseText, latinText).forEach { text ->
                text.lines().forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotBlank()) combined.add(trimmed)
                }
            }
            combined.joinToString("\n").takeIf { it.isNotBlank() }?.also { Log.i(TAG, "performLocalOcr: 识别成功，字符数=${it.length}") }
                ?: also { Log.w(TAG, "performLocalOcr: 识别结果空白: $url") }
        }.getOrNull()
    }
}
