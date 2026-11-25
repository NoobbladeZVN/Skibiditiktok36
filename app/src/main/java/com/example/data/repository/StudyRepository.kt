package com.example.data.repository

import android.graphics.Bitmap
import android.util.Log
import com.example.core.ImageUtils
import com.example.data.local.ChatMessageEntity
import com.example.data.local.SessionWithMessages
import com.example.data.local.SolvedSessionEntity
import com.example.data.local.StudyDao
import com.example.model.EducationLevel
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.IOException

private const val TAG = "StudyRepository"
private const val PRIMARY_MODEL = "gemini-3.8-flash"
private const val FALLBACK_MODEL = "gemini-3.5-flash-lite"
private const val MAX_PRIMARY_RETRIES = 2
private const val REQUEST_TIMEOUT_MS = 45_000L

const val FRIENDLY_OVERLOAD_ERROR =
    "Máy chủ AI đang hơi đông bạn học, hãy bấm Thử lại sau vài giây nhé!"

data class GeneratedSolution(
    val subject: String,
    val title: String,
    val finalAnswer: String,
    val detailedSteps: String,
    val similarExercise: String,
    val similarExerciseHint: String
)

class StudyRepository(private val studyDao: StudyDao) {

    private val solutionSchema = Schema.obj(
        properties = mapOf(
            "subject" to Schema.string(
                description = "Môn học ngắn gọn bằng tiếng Việt (VD: Toán, Vật lý, Hóa học, Ngữ văn, Tiếng Anh, Sinh học, Tin học)"
            ),
            "title" to Schema.string(
                description = "Tiêu đề ngắn gọn mô tả dạng bài tập (tối đa 10 từ)"
            ),
            "finalAnswer" to Schema.string(
                description = "Đáp án chốt ngắn gọn, chính xác, dễ nhìn bằng tiếng Việt"
            ),
            "detailedSteps" to Schema.string(
                description = "Các bước giải chi tiết, đánh số từng bước rõ ràng bằng tiếng Việt, phù hợp đúng cấp học"
            ),
            "similarExercise" to Schema.string(
                description = "1 bài tập tương tự cùng dạng và độ khó phù hợp để học sinh tự luyện"
            ),
            "similarExerciseHint" to Schema.string(
                description = "Gợi ý ngắn gọn và đáp số của bài tập tương tự để học sinh tự kiểm tra"
            )
        )
    )

    private val solverConfig = generationConfig {
        responseMimeType = "application/json"
        responseSchema = solutionSchema
    }

    private val solverSystemInstruction = content {
        text(
            "Bạn là gia sư AI xuất sắc của ứng dụng 'Học não to', chuyên giúp học sinh và sinh viên Việt Nam ôn thi cuối kỳ đạt điểm 10. " +
                "Luôn trả lời hoàn toàn bằng tiếng Việt rõ ràng, chính xác, sư phạm, không bỏ bước quan trọng. " +
                "Nếu đề bài trong ảnh mờ hoặc thiếu dữ kiện, hãy nêu rõ phần đọc được và hướng dẫn giải chính xác nhất có thể."
        )
    }

    private val chatSystemInstruction = content {
        text(
            "Bạn là gia sư AI thân thiện của 'Học não to'. Hãy giải đáp thắc mắc tiếp nối của học sinh bằng tiếng Việt " +
                "dễ hiểu, bám sát bài toán đang giải và đúng cấp học của học sinh. Sử dụng gạch đầu dòng ngắn gọn khi cần."
        )
    }

    private val primarySolverModel by lazy {
        Firebase.ai.generativeModel(
            modelName = PRIMARY_MODEL,
            generationConfig = solverConfig,
            systemInstruction = solverSystemInstruction
        )
    }

    private val fallbackSolverModel by lazy {
        Firebase.ai.generativeModel(
            modelName = FALLBACK_MODEL,
            generationConfig = solverConfig,
            systemInstruction = solverSystemInstruction
        )
    }

    private val primaryChatModel by lazy {
        Firebase.ai.generativeModel(
            modelName = PRIMARY_MODEL,
            systemInstruction = chatSystemInstruction
        )
    }

    private val fallbackChatModel by lazy {
        Firebase.ai.generativeModel(
            modelName = FALLBACK_MODEL,
            systemInstruction = chatSystemInstruction
        )
    }

    fun observeAllSessions(): Flow<List<SessionWithMessages>> =
        studyDao.observeAllSessionsWithMessages()

    fun observeSessionById(sessionId: Long): Flow<SessionWithMessages?> =
        studyDao.observeSessionById(sessionId)

    suspend fun solveAndSaveProblem(
        level: EducationLevel,
        problemText: String,
        localImagePath: String?
    ): Long {
        val bitmap: Bitmap? = localImagePath?.let { ImageUtils.loadBitmapFromPath(it) }
        val trimmedText = problemText.trim()

        val instruction = buildString {
            append("Cấp học của người học: ${level.displayName} (${level.gradeSubtitle}).\n")
            append("Yêu cầu sư phạm: ${level.aiInstruction}\n\n")
            if (trimmedText.isNotBlank()) {
                append("Đề bài / yêu cầu của học sinh:\n$trimmedText\n\n")
            } else {
                append("Hãy đọc đề bài trong hình ảnh đính kèm và giải đầy đủ.\n\n")
            }
            append(
                "Hãy phân tích và trả về kết quả đúng cấu trúc gồm:\n" +
                    "- Đáp án chốt\n" +
                    "- Các bước giải chi tiết\n" +
                    "- 1 bài tập tương tự kèm gợi ý tự kiểm tra."
            )
        }

        val requestContent = content {
            if (bitmap != null) {
                image(bitmap)
            }
            text(instruction)
        }

        val rawJson = executeWithRetryAndFallback(requestContent)
        val parsed = parseSolutionJson(rawJson)
        val now = System.currentTimeMillis()
        val session = SolvedSessionEntity(
            educationLevel = level.name,
            subject = parsed.subject,
            title = parsed.title,
            problemText = trimmedText,
            imagePath = localImagePath,
            finalAnswer = parsed.finalAnswer,
            detailedSteps = parsed.detailedSteps,
            similarExercise = parsed.similarExercise,
            similarExerciseHint = parsed.similarExerciseHint,
            createdAt = now,
            updatedAt = now
        )
        return studyDao.insertSession(session)
    }

    private suspend fun executeWithRetryAndFallback(requestContent: Content): String {
        var lastError: Throwable? = null

        // 1. Primary model with 2 automatic retries on 503 / overload / timeout
        for (attempt in 0..MAX_PRIMARY_RETRIES) {
            try {
                val response = withTimeout(REQUEST_TIMEOUT_MS) {
                    primarySolverModel.generateContent(requestContent)
                }
                val text = response.text
                if (!text.isNullOrBlank()) {
                    return text
                }
                throw IllegalStateException("Empty model response")
            } catch (e: TimeoutCancellationException) {
                lastError = e
                Log.w(TAG, "Primary solver attempt ${attempt + 1} timed out", e)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "Primary solver attempt ${attempt + 1} failed: ${e.message}", e)
                if (!isTransientOverloadError(e)) {
                    throw e
                }
            }
            if (attempt < MAX_PRIMARY_RETRIES) {
                delay(1_000L * (attempt + 1))
            }
        }

        // 2. Fallback model if primary model remains overloaded
        Log.i(TAG, "Switching to fallback model $FALLBACK_MODEL after primary retries")
        return try {
            val fallbackResponse = withTimeout(REQUEST_TIMEOUT_MS) {
                fallbackSolverModel.generateContent(requestContent)
            }
            val fallbackText = fallbackResponse.text
            if (!fallbackText.isNullOrBlank()) {
                fallbackText
            } else {
                throw lastError ?: IllegalStateException("Empty fallback response")
            }
        } catch (e: TimeoutCancellationException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Fallback solver $FALLBACK_MODEL also failed", e)
            throw e
        }
    }

    fun streamFollowUpReply(
        session: SolvedSessionEntity,
        priorMessages: List<ChatMessageEntity>,
        question: String
    ): Flow<String> = flow {
        val level = EducationLevel.fromStored(session.educationLevel)
        val attachedBitmap = session.imagePath?.let { ImageUtils.loadBitmapFromPath(it, 1200) }

        val history = buildList {
            add(
                content("user") {
                    if (attachedBitmap != null) {
                        image(attachedBitmap)
                    }
                    text(
                        buildString {
                            append("Cấp học: ${level.displayName} (${level.gradeSubtitle}).\n")
                            if (session.problemText.isNotBlank()) {
                                append("Đề bài: ${session.problemText}")
                            } else {
                                append("Đề bài nằm trong ảnh đính kèm.")
                            }
                        }
                    )
                }
            )
            add(
                content("model") {
                    text(
                        """
                        1. Đáp án chốt:
                        ${session.finalAnswer}

                        2. Các bước giải chi tiết:
                        ${session.detailedSteps}

                        3. Bài tập tương tự:
                        ${session.similarExercise}
                        """.trimIndent()
                    )
                }
            )
            priorMessages.forEach { msg ->
                add(content(if (msg.isUser) "user" else "model") { text(msg.content) })
            }
        }

        var succeeded = false
        var lastError: Throwable? = null

        // 1. Primary chat model with 2 automatic retries before any chunk is emitted
        for (attempt in 0..MAX_PRIMARY_RETRIES) {
            val accumulated = StringBuilder()
            try {
                streamFromModel(primaryChatModel, history, question, accumulated) { partial ->
                    emit(partial)
                }
                succeeded = true
                break
            } catch (e: TimeoutCancellationException) {
                lastError = e
                Log.w(TAG, "Primary chat attempt ${attempt + 1} timed out", e)
                if (accumulated.isNotEmpty()) throw e
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "Primary chat attempt ${attempt + 1} failed: ${e.message}", e)
                if (accumulated.isNotEmpty() || !isTransientOverloadError(e)) {
                    throw e
                }
            }
            if (attempt < MAX_PRIMARY_RETRIES) {
                delay(1_000L * (attempt + 1))
            }
        }

        // 2. Fallback chat model if primary model is overloaded
        if (!succeeded) {
            Log.i(TAG, "Switching follow-up chat to fallback model $FALLBACK_MODEL")
            val accumulated = StringBuilder()
            try {
                streamFromModel(fallbackChatModel, history, question, accumulated) { partial ->
                    emit(partial)
                }
            } catch (e: CancellationException) {
                if (e is TimeoutCancellationException) {
                    throw lastError ?: e
                }
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Fallback chat $FALLBACK_MODEL failed", e)
                throw e
            }
        }
    }

    private suspend fun streamFromModel(
        model: GenerativeModel,
        history: List<Content>,
        question: String,
        accumulated: StringBuilder,
        onPartial: suspend (String) -> Unit
    ) {
        val chat = model.startChat(history = history)
        withTimeout(REQUEST_TIMEOUT_MS) {
            chat.sendMessageStream(question).collect { chunk ->
                val piece = chunk.text.orEmpty()
                if (piece.isNotEmpty()) {
                    accumulated.append(piece)
                    onPartial(accumulated.toString())
                }
            }
        }
        if (accumulated.isBlank()) {
            throw IllegalStateException("Empty chat response")
        }
    }

    suspend fun saveChatTurn(sessionId: Long, isUser: Boolean, content: String) {
        val now = System.currentTimeMillis()
        studyDao.insertMessage(
            ChatMessageEntity(
                sessionId = sessionId,
                isUser = isUser,
                content = content,
                createdAt = now
            )
        )
        studyDao.updateSessionTimestamp(sessionId, now)
    }

    suspend fun deleteSession(session: SolvedSessionEntity) {
        ImageUtils.deleteProblemImage(session.imagePath)
        studyDao.deleteSessionById(session.id)
    }

    private fun parseSolutionJson(rawJson: String): GeneratedSolution {
        val json = JSONObject(rawJson)
        return GeneratedSolution(
            subject = json.optString("subject", "Tổng hợp").ifBlank { "Tổng hợp" },
            title = json.optString("title", "Bài giải ôn thi").ifBlank { "Bài giải ôn thi" },
            finalAnswer = json.optString("finalAnswer", "").ifBlank { "Chưa có đáp án chốt." },
            detailedSteps = json.optString("detailedSteps", "").ifBlank { "Chưa có lời giải chi tiết." },
            similarExercise = json.optString("similarExercise", "").ifBlank {
                "Hãy thử thay đổi số liệu trong đề bài trên để tự luyện lại."
            },
            similarExerciseHint = json.optString("similarExerciseHint", "")
        )
    }

    companion object {
        fun isTransientOverloadError(throwable: Throwable): Boolean {
            var current: Throwable? = throwable
            while (current != null) {
                if (current is TimeoutCancellationException) return true
                val msg = current.message?.lowercase().orEmpty()
                if (
                    msg.contains("503") ||
                    msg.contains("high demand") ||
                    msg.contains("overload") ||
                    msg.contains("unavailable") ||
                    msg.contains("resource exhausted") ||
                    msg.contains("429") ||
                    msg.contains("too many requests") ||
                    msg.contains("try again later") ||
                    msg.contains("temporarily") ||
                    msg.contains("deadline")
                ) {
                    return true
                }
                current = current.cause
            }
            return false
        }

        fun toFriendlyVietnameseError(throwable: Throwable): String {
            if (isTransientOverloadError(throwable)) {
                return FRIENDLY_OVERLOAD_ERROR
            }
            var current: Throwable? = throwable
            while (current != null) {
                val msg = current.message?.lowercase().orEmpty()
                if (
                    msg.contains("403") ||
                    msg.contains("app check") ||
                    msg.contains("attestation")
                ) {
                    return "Không thể xác thực kết nối AI (App Check). Bạn hãy thử khởi động lại ứng dụng nhé!"
                }
                if (
                    current is IOException ||
                    msg.contains("network") ||
                    msg.contains("unable to resolve host") ||
                    msg.contains("failed to connect") ||
                    msg.contains("connection")
                ) {
                    return "Kết nối mạng đang chập chờn. Bạn kiểm tra lại Wi-Fi/4G rồi bấm Thử lại nhé!"
                }
                current = current.cause
            }
            return FRIENDLY_OVERLOAD_ERROR
        }
    }
}
