package net.venipa.stashling.core.stash

data class Page<T>(val items: List<T>, val page: Int, val perPage: Int, val count: Int) {
    val hasNext: Boolean get() = page * perPage < count
}

data class Image(val id: String, val url: String, val width: Int, val height: Int) {
    /** 300 grid, 600 hero/detail, 1280 fullscreen. No key needed. */
    fun sized(px: Int): String = "$url?size=$px"
}

data class Link(val url: String, val site: String)

data class StudioRef(val id: String, val name: String, val parent: StudioRef? = null)

data class TagRef(val id: String, val name: String, val category: String? = null)

data class PerformerRef(val id: String, val name: String, val disambiguation: String?, val asName: String?, val thumbnail: Image?)

data class SceneCard(
    val id: String,
    val title: String?,
    val releaseDate: String?,
    val durationSec: Int?,
    val studio: StudioRef?,
    val cover: Image?,
    val performers: List<String>,
    /** Same order as [performers]; used to hide scenes of hidden performers. */
    val performerIds: List<String> = emptyList(),
)

data class Scene(
    val id: String,
    val title: String?,
    val details: String?,
    val releaseDate: String?,
    val productionDate: String?,
    val code: String?,
    val director: String?,
    val durationSec: Int?,
    val deleted: Boolean,
    val studio: StudioRef?,
    val performers: List<PerformerRef>,
    val tags: List<TagRef>,
    val images: List<Image>,
    val urls: List<Link>,
)

data class BodyMod(val location: String, val description: String?)

/** From a list call (performers/searchPerformers) aliases, body mods, images, urls and studios are empty: load [StashDbApi.performer]. */
data class Performer(
    val id: String,
    val name: String,
    val disambiguation: String?,
    val aliases: List<String>,
    val gender: String?,
    val birthDate: String?,
    val deathDate: String?,
    val age: Int?,
    val country: String?,
    val ethnicity: String?,
    val eyeColor: String?,
    val hairColor: String?,
    val heightCm: Int?,
    val cupSize: String?,
    val bandSize: Int?,
    val waistSize: Int?,
    val hipSize: Int?,
    val breastType: String?,
    val careerStart: Int?,
    val careerEnd: Int?,
    val tattoos: List<BodyMod>,
    val piercings: List<BodyMod>,
    val sceneCount: Int,
    val isFavorite: Boolean,
    val deleted: Boolean,
    val mergedIntoId: String?,
    val thumbnail: Image?,
    val images: List<Image>,
    val urls: List<Link>,
    // studio + scene count, for the scene filter
    val studios: List<Pair<StudioRef, Int>>,
)

/** From [StashDbApi.studios] aliases, children and urls are empty: load [StashDbApi.studio]. */
data class Studio(
    val id: String,
    val name: String,
    val aliases: List<String>,
    val parent: StudioRef?,
    val children: List<StudioRef>,
    val isFavorite: Boolean,
    val deleted: Boolean,
    val images: List<Image>,
    val urls: List<Link>,
)

data class Tag(val id: String, val name: String, val description: String?, val aliases: List<String>, val category: String?)

data class TagCategory(val id: String, val name: String, val group: String, val description: String?)

data class Me(val id: String, val name: String)

enum class SortDirection { ASC, DESC }

enum class SceneSort { TITLE, DATE, DURATION, TRENDING, POPULARITY, CREATED_AT, UPDATED_AT }

enum class PerformerSort { NAME, SCENE_COUNT, POPULARITY, LAST_SCENE, DEBUT, CREATED_AT }

enum class NameSort { NAME, CREATED_AT, UPDATED_AT }

enum class Favorites { PERFORMER, STUDIO, ALL }

/** stash-box takes one bound: [before] true = LESS_THAN, false = GREATER_THAN. [date] is YYYY-MM-DD. */
data class DateBound(val date: String, val before: Boolean)

data class SceneFilter(
    val title: String? = null,
    val studioIds: List<String> = emptyList(),
    val parentStudioId: String? = null,
    val performerIds: List<String> = emptyList(),
    val tagIds: List<String> = emptyList(),
    val allTags: Boolean = true,
    /** Scenes without any of these tags. stash-box takes one modifier per field: ignored while [tagIds] is set. */
    val excludeTagIds: List<String> = emptyList(),
    val date: DateBound? = null,
    val favorites: Favorites? = null,
    val sort: SceneSort = SceneSort.DATE,
    val direction: SortDirection = SortDirection.DESC,
)

/** [gender] is a GenderFilterEnum name (MALE, FEMALE, TRANSGENDER_MALE, ...). */
data class PerformerFilter(
    val names: String? = null,
    val studioId: String? = null,
    val gender: String? = null,
    val favoritesOnly: Boolean = false,
    val sort: PerformerSort = PerformerSort.POPULARITY,
    val direction: SortDirection = SortDirection.DESC,
)

data class StudioFilter(
    val names: String? = null,
    val parentId: String? = null,
    val favoritesOnly: Boolean = false,
    val sort: NameSort = NameSort.NAME,
    val direction: SortDirection = SortDirection.ASC,
)

data class TagFilter(
    val names: String? = null,
    val categoryId: String? = null,
    val sort: NameSort = NameSort.NAME,
    val direction: SortDirection = SortDirection.ASC,
)

sealed class StashError(msg: String?, cause: Throwable? = null) : Exception(msg, cause) {
    /** HTTP 401 (bad key), GraphQL "not authorized" (no key / expired cookie), or no account stored. */
    class Unauthorized : StashError("not authorized")

    /** /login 401. */
    class BadLogin : StashError("wrong username or password")

    class Http(val status: Int) : StashError("HTTP $status")

    class GraphQl(val messages: List<String>) : StashError(messages.joinToString())

    /** Connection failure or an unreadable response. */
    class Network(cause: Throwable) : StashError(cause.message, cause)
}

/** Throws StashError. find-style calls return null when the id is gone. */
interface StashDbApi {
    /** Returns the api key; the session cookie is used once and dropped. */
    suspend fun login(baseUrl: String, username: String, password: String): String

    suspend fun verify(baseUrl: String, apiKey: String): Me

    suspend fun scenes(filter: SceneFilter, page: Int, perPage: Int = 40): Page<SceneCard>

    suspend fun searchScenes(term: String, page: Int, perPage: Int = 40): Page<SceneCard>

    suspend fun scene(id: String): Scene?

    suspend fun performers(filter: PerformerFilter, page: Int, perPage: Int = 40): Page<Performer>

    suspend fun searchPerformers(term: String, page: Int, perPage: Int = 40): Page<Performer>

    suspend fun performer(id: String): Performer?

    suspend fun studios(filter: StudioFilter, page: Int, perPage: Int = 40): Page<Studio>

    suspend fun studio(id: String): Studio?

    suspend fun tags(filter: TagFilter, page: Int, perPage: Int = 40): Page<Tag>

    suspend fun tagCategories(): List<TagCategory>

    suspend fun tag(id: String): Tag?

    suspend fun setFavoritePerformer(id: String, favorite: Boolean)

    suspend fun setFavoriteStudio(id: String, favorite: Boolean)
}
