package net.venipa.stashling.core.whisparr

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonObject
import kotlin.time.Instant

/** Lookup hit; `movie` is kept raw so it can be POSTed back as-is (what the Whisparr web UI does). */
@Serializable
data class SearchResult(
    val foreignId: String? = null,
    val movie: JsonObject? = null,
)

@Serializable
data class WhisparrScene(
    val id: Int = 0,
    val title: String,
    val foreignId: String,
    val stashId: String? = null,
    /** Web UI route `/movie/{titleSlug}` (frontend AppRoutes.tsx). */
    val titleSlug: String? = null,
    val monitored: Boolean = false,
    val hasFile: Boolean? = null,
    val sizeOnDisk: Long? = null,
    val movieFile: MovieFile? = null,
    val isExcluded: Boolean = false,
    val qualityProfileId: Int = 0,
)

@Serializable
data class MovieFile(
    val size: Long,
    val quality: QualityWrap,
)

@Serializable
data class QualityWrap(
    val quality: Named,
)

@Serializable
data class Named(
    val id: Int,
    val name: String,
)

@Serializable
data class QueueItem(
    val id: Int,
    val movieId: Int? = null,
    val title: String? = null,
    val size: Double = 0.0,
    val sizeleft: Double = 0.0,
    @Serializable(with = LenientInstantSerializer::class) val estimatedCompletionTime: Instant? = null,
    val status: String,
    val trackedDownloadState: String? = null,
    val trackedDownloadStatus: String? = null,
    val errorMessage: String? = null,
    val movie: WhisparrScene? = null,
) {
    val progress: Double get() = if (size > 0) 1 - sizeleft / size else 0.0
}

@Serializable
data class HistoryItem(
    val id: Int,
    val movieId: Int,
    val sourceTitle: String,
    @Serializable(with = LenientInstantSerializer::class) val date: Instant,
    val eventType: String,
    val movie: WhisparrScene? = null,
)

@Serializable
data class Release(
    val guid: String,
    val indexerId: Int,
    val indexer: String,
    val title: String,
    val size: Long,
    val ageHours: Double,
    val quality: QualityWrap,
    val seeders: Int? = null,
    val protocol: String,
    val approved: Boolean,
    val rejections: List<String> = emptyList(),
    val customFormatScore: Int = 0,
)

@Serializable
data class RootFolder(
    val id: Int,
    val path: String,
    val accessible: Boolean,
    val freeSpace: Long? = null,
)

@Serializable
data class QualityProfile(
    val id: Int,
    val name: String,
)

/** GET /api/v3/tag. */
@Serializable
data class WhisparrTag(
    val id: Int,
    val label: String,
)

@Serializable
data class CommandRes(
    val id: Int,
    val name: String,
    val status: String,
    val message: String? = null,
    @Serializable(with = LenientInstantSerializer::class) val queued: Instant? = null,
)

@Serializable
data class SystemStatus(
    val version: String,
    val instanceName: String? = null,
    val urlBase: String? = null,
)

sealed interface PanelState {
    data object Unavailable : PanelState

    data object Loading : PanelState

    data class NotInLibrary(
        val lookup: JsonObject,
        val excluded: Boolean,
    ) : PanelState

    data class Missing(
        val scene: WhisparrScene,
    ) : PanelState

    data class Searching(
        val scene: WhisparrScene,
        val commandId: Int,
    ) : PanelState

    data class InQueue(
        val scene: WhisparrScene,
        val item: QueueItem,
    ) : PanelState

    data class Failed(
        val scene: WhisparrScene,
        val message: String?,
    ) : PanelState

    data class Downloaded(
        val scene: WhisparrScene,
    ) : PanelState

    data class Error(
        val message: String,
    ) : PanelState
}

/** Whisparr labels: REQUEST = "Add", QUEUE = "Add and search", PICK = add then "Interactive search". */
enum class AddMode { REQUEST, QUEUE, PICK }

sealed class WhisparrError(
    msg: String?,
    cause: Throwable? = null,
) : Exception(msg, cause) {
    class Unauthorized : WhisparrError("API key rejected")

    class LoginRequired : WhisparrError("Whisparr needs a username and password")

    class BadLogin : WhisparrError("wrong username or password")

    /** [message] is Whisparr's own text (validation errors, "try searching again", …). */
    class Http(
        val status: Int,
        message: String,
    ) : WhisparrError(message)

    class Network(
        cause: Throwable,
    ) : WhisparrError(cause.message ?: "network error", cause)
}

/** .NET writes DateTime without an offset when its Kind is unspecified; those are UTC in Whisparr. */
internal object LenientInstantSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("WhisparrInstant", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Instant = parseWhisparrInstant(decoder.decodeString())

    override fun serialize(
        encoder: Encoder,
        value: Instant,
    ) = encoder.encodeString(value.toString())
}

internal fun parseWhisparrInstant(s: String): Instant {
    val hasOffset = s.endsWith("Z", ignoreCase = true) || Regex("[+-]\\d{2}:?\\d{2}$").containsMatchIn(s.substringAfter('T', ""))
    return Instant.parse(if (hasOffset) s else "${s}Z")
}

/** Slim, all-optional view of a library item for tile badges: one odd item (e.g. no foreignId/title) must not fail the whole list. */
@Serializable
data class LibraryEntry(
    val id: Int = 0,
    val foreignId: String? = null,
    val stashId: String? = null,
    val hasFile: Boolean? = null,
)
