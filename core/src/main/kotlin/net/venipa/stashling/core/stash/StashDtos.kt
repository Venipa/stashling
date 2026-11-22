package net.venipa.stashling.core.stash

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Wire shapes of the queries in StashQueries; everything optional the schema allows to be null or that a query leaves out.

@Serializable
internal data class ImageDto(val id: String, val url: String, val width: Int = 0, val height: Int = 0) {
    fun toModel() = Image(id, url, width, height)
}

@Serializable
internal data class NameDto(val name: String)

@Serializable
internal data class UrlDto(val url: String, val site: NameDto? = null) {
    fun toModel() = Link(url, site?.name.orEmpty())
}

@Serializable
internal data class StudioRefDto(val id: String, val name: String, val parent: StudioRefDto? = null) {
    fun toModel(): StudioRef = StudioRef(id, name, parent?.toModel())
}

@Serializable
internal data class PerformerAppearanceDto(@SerialName("as") val asName: String? = null, val performer: PerformerDto)

@Serializable
internal data class TagDto(
    val id: String,
    val name: String,
    val description: String? = null,
    val aliases: List<String> = emptyList(),
    val category: NameDto? = null,
) {
    fun toModel() = Tag(id, name, description, aliases, category?.name)

    fun toRef() = TagRef(id, name, category?.name)
}

@Serializable
internal data class SceneDto(
    val id: String,
    val title: String? = null,
    val details: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("production_date") val productionDate: String? = null,
    val code: String? = null,
    val director: String? = null,
    val duration: Int? = null,
    val deleted: Boolean = false,
    val studio: StudioRefDto? = null,
    val performers: List<PerformerAppearanceDto> = emptyList(),
    val tags: List<TagDto> = emptyList(),
    val images: List<ImageDto> = emptyList(),
    val urls: List<UrlDto> = emptyList(),
) {
    // Scenes usually carry one landscape and maybe a portrait image: the card wants the landscape one.
    fun toCard() = SceneCard(
        id = id,
        title = title,
        releaseDate = releaseDate,
        durationSec = duration,
        studio = studio?.toModel(),
        cover = (images.firstOrNull { it.width >= it.height } ?: images.firstOrNull())?.toModel(),
        performers = performers.map { it.performer.name },
    )

    fun toModel() = Scene(
        id = id,
        title = title,
        details = details,
        releaseDate = releaseDate,
        productionDate = productionDate,
        code = code,
        director = director,
        durationSec = duration,
        deleted = deleted,
        studio = studio?.toModel(),
        performers =
        performers.map {
            PerformerRef(it.performer.id, it.performer.name, it.performer.disambiguation, it.asName, it.performer.thumbnail?.toModel())
        },
        tags = tags.map { it.toRef() },
        images = images.map { it.toModel() },
        urls = urls.map { it.toModel() },
    )
}

@Serializable
internal data class BodyModDto(val location: String, val description: String? = null)

@Serializable
internal data class PerformerStudioDto(@SerialName("scene_count") val sceneCount: Int = 0, val studio: StudioRefDto)

@Serializable
internal data class PerformerDto(
    val id: String,
    val name: String,
    val disambiguation: String? = null,
    val aliases: List<String> = emptyList(),
    val gender: String? = null,
    @SerialName("birth_date") val birthDate: String? = null,
    @SerialName("death_date") val deathDate: String? = null,
    val age: Int? = null,
    val country: String? = null,
    val ethnicity: String? = null,
    @SerialName("eye_color") val eyeColor: String? = null,
    @SerialName("hair_color") val hairColor: String? = null,
    val height: Int? = null,
    @SerialName("cup_size") val cupSize: String? = null,
    @SerialName("band_size") val bandSize: Int? = null,
    @SerialName("waist_size") val waistSize: Int? = null,
    @SerialName("hip_size") val hipSize: Int? = null,
    @SerialName("breast_type") val breastType: String? = null,
    @SerialName("career_start_year") val careerStart: Int? = null,
    @SerialName("career_end_year") val careerEnd: Int? = null,
    val tattoos: List<BodyModDto>? = null,
    val piercings: List<BodyModDto>? = null,
    @SerialName("scene_count") val sceneCount: Int = 0,
    @SerialName("is_favorite") val isFavorite: Boolean = false,
    val deleted: Boolean = false,
    @SerialName("merged_into_id") val mergedIntoId: String? = null,
    val thumbnail: ImageDto? = null,
    val images: List<ImageDto> = emptyList(),
    val urls: List<UrlDto> = emptyList(),
    val studios: List<PerformerStudioDto> = emptyList(),
) {
    fun toModel() = Performer(
        id = id,
        name = name,
        disambiguation = disambiguation,
        aliases = aliases,
        gender = gender,
        birthDate = birthDate,
        deathDate = deathDate,
        age = age,
        country = country,
        ethnicity = ethnicity,
        eyeColor = eyeColor,
        hairColor = hairColor,
        heightCm = height,
        cupSize = cupSize,
        bandSize = bandSize,
        waistSize = waistSize,
        hipSize = hipSize,
        breastType = breastType,
        careerStart = careerStart,
        careerEnd = careerEnd,
        tattoos = tattoos.orEmpty().map { BodyMod(it.location, it.description) },
        piercings = piercings.orEmpty().map { BodyMod(it.location, it.description) },
        sceneCount = sceneCount,
        isFavorite = isFavorite,
        deleted = deleted,
        mergedIntoId = mergedIntoId,
        // Lists ask for thumbnail only; fall back to the first image on the detail.
        thumbnail = (thumbnail ?: images.firstOrNull())?.toModel(),
        images = images.map { it.toModel() },
        urls = urls.map { it.toModel() },
        studios = studios.map { it.studio.toModel() to it.sceneCount },
    )
}

@Serializable
internal data class StudioDto(
    val id: String,
    val name: String,
    val aliases: List<String> = emptyList(),
    val parent: StudioRefDto? = null,
    @SerialName("child_studios") val children: List<StudioRefDto> = emptyList(),
    @SerialName("is_favorite") val isFavorite: Boolean = false,
    val deleted: Boolean = false,
    val images: List<ImageDto> = emptyList(),
    val urls: List<UrlDto> = emptyList(),
) {
    fun toModel() = Studio(
        id = id,
        name = name,
        aliases = aliases,
        parent = parent?.toModel(),
        children = children.map { it.toModel() },
        isFavorite = isFavorite,
        deleted = deleted,
        images = images.map { it.toModel() },
        urls = urls.map { it.toModel() },
    )
}

@Serializable
internal data class TagCategoryDto(val id: String, val name: String, val group: String, val description: String? = null) {
    fun toModel() = TagCategory(id, name, group, description)
}

@Serializable
internal data class MeDto(val id: String? = null, val name: String? = null, @SerialName("api_key") val apiKey: String? = null)

// Result wrappers: one per query root field.
@Serializable
internal data class ScenesResult(val count: Int = 0, val scenes: List<SceneDto> = emptyList())

@Serializable
internal data class PerformersResult(val count: Int = 0, val performers: List<PerformerDto> = emptyList())

@Serializable
internal data class StudiosResult(val count: Int = 0, val studios: List<StudioDto> = emptyList())

@Serializable
internal data class TagsResult(val count: Int = 0, val tags: List<TagDto> = emptyList())

@Serializable
internal data class TagCategoriesResult(@SerialName("tag_categories") val categories: List<TagCategoryDto> = emptyList())
