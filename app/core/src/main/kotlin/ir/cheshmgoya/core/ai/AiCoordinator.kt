package ir.cheshmgoya.core.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

enum class ConnectionStatus {
    /** AI layer switched off or offline-only: nothing to connect to. */
    OFFLINE_MODE,
    CONNECTED,
    /** Last request to the selected provider failed; running on offline prediction. */
    FALLBACK,
}

data class Timeouts(val rankMs: Long = 1000, val generateMs: Long = 3000, val classifyMs: Long = 3000)

/**
 * Wraps the selected [AiProvider] so that any timeout, error or missing
 * capability silently falls back to [offline]. Nothing the UI depends on can
 * fail because of the network.
 */
class AiCoordinator(
    private val offline: OfflineProvider,
    var timeouts: Timeouts = Timeouts(),
    /** Minimum confidence for AI ranking to be shown in the suggestion row. */
    var rankConfidenceThreshold: Double = 0.35,
) {
    @Volatile
    var provider: AiProvider? = null
        set(value) {
            field = value
            _status.value = if (value == null || value.id == ProviderId.OFFLINE) ConnectionStatus.OFFLINE_MODE else ConnectionStatus.FALLBACK
        }

    private val _status = MutableStateFlow(ConnectionStatus.OFFLINE_MODE)
    val status: StateFlow<ConnectionStatus> = _status

    private suspend fun <T : Any> attempt(timeoutMs: Long, block: suspend (AiProvider) -> T?): T? {
        val p = provider ?: return null
        if (p.id == ProviderId.OFFLINE) return null
        return try {
            val r = withTimeout(timeoutMs) { block(p) }
            // null without an error means "not supported by this provider", not a connection problem.
            if (r != null) _status.value = ConnectionStatus.CONNECTED
            r
        } catch (e: TimeoutCancellationException) {
            _status.value = ConnectionStatus.FALLBACK
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _status.value = ConnectionStatus.FALLBACK
            null
        }
    }

    suspend fun complete(context: AiContext): List<String> {
        val ai = attempt(timeouts.generateMs) { it.complete(context) }?.filter { it.isNotBlank() }
        return if (!ai.isNullOrEmpty()) ai.take(3) else offline.complete(context)
    }

    /**
     * Ranked suggestions for the "پیشنهادها" row, or an empty list when no
     * source is confident enough — the row then stays empty rather than guessing.
     */
    suspend fun suggestionsFor(context: AiContext, phrases: List<String>, max: Int = 4): List<String> {
        if (phrases.isEmpty()) return emptyList()
        val r = attempt(timeouts.rankMs) { it.rank(context, phrases) }
            ?: if (provider == null) return emptyList() else offline.rank(context, phrases)
        if (r.confidence < rankConfidenceThreshold) return emptyList()
        return r.ranked.filter { it.probability >= r.ranked.first().probability * 0.25 }.take(max).map { it.text }
    }

    suspend fun classify(question: String): QuestionClass {
        val ai = attempt(timeouts.classifyMs) { it.classify(question) }
        return if (ai != null && ai.confidence >= 0.4) ai else offline.classify(question)
    }

    /** Refresh the status indicator. */
    suspend fun ping(): ConnectionStatus {
        val p = provider
        if (p == null || p.id == ProviderId.OFFLINE) return ConnectionStatus.OFFLINE_MODE.also { _status.value = it }
        val ok = try {
            withTimeoutOrNull(timeouts.generateMs) { p.ping() } ?: false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
        return (if (ok) ConnectionStatus.CONNECTED else ConnectionStatus.FALLBACK).also { _status.value = it }
    }
}
