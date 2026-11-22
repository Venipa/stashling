package net.venipa.stashling.core.stash

// Hand-written operations, from stash-box master (graphql/schema, 2026-11-22). The deployed stashdb.org may lag master:
// the fixture recorder (StashFixtureRecorderTest, needs STASHDB_API_KEY) is the check against the live schema.
// Release dates use release_date, never the deprecated date.
internal object StashQueries {
    private const val IMAGE = "id url width height"
    private const val URLS = "urls { url site { name } }"

    private const val SCENE_CARD =
        "fragment SceneCard on Scene { id title release_date duration studio { id name } images { $IMAGE } performers { performer { id name gender } } tags { id name } }"

    // List fields only; the detail query adds the rest.
    private const val PERFORMER_CARD =
        "fragment PerformerCard on Performer { id name disambiguation aliases gender country scene_count is_favorite deleted merged_into_id images { $IMAGE } }"

    const val SCENES = "query Scenes(\$input: SceneQueryInput!) { queryScenes(input: \$input) { count scenes { ...SceneCard } } } $SCENE_CARD"

    const val SEARCH_SCENES =
        "query SearchScenes(\$term: String!, \$page: Int!, \$per: Int!) " +
            "{ searchScenes(term: \$term, page: \$page, per_page: \$per) { count scenes { ...SceneCard } } } $SCENE_CARD"

    const val SCENE =
        "query Scene(\$id: ID!) { findScene(id: \$id) { id title details director code release_date production_date duration deleted " +
            "studio { id name parent { id name } } images { $IMAGE } " +
            "performers { as performer { id name disambiguation images { $IMAGE } } } " +
            "tags { id name category { name } } $URLS } }"

    const val PERFORMERS =
        "query Performers(\$input: PerformerQueryInput!) { queryPerformers(input: \$input) { count performers { ...PerformerCard } } } $PERFORMER_CARD"

    const val SEARCH_PERFORMERS =
        "query SearchPerformers(\$term: String!, \$page: Int!, \$per: Int!, \$filter: PerformerSearchFilter) " +
            "{ searchPerformers(term: \$term, page: \$page, per_page: \$per, filter: \$filter) { count performers { ...PerformerCard } } } $PERFORMER_CARD"

    const val PERFORMER =
        "query Performer(\$id: ID!) { findPerformer(id: \$id) { id name disambiguation aliases gender birth_date death_date age country " +
            "ethnicity eye_color hair_color height cup_size band_size waist_size hip_size breast_type career_start_year career_end_year " +
            "tattoos { location description } piercings { location description } $URLS images { $IMAGE } " +
            "scene_count is_favorite deleted merged_into_id studios { scene_count studio { id name parent { id name } } } } }"

    const val STUDIOS =
        "query Studios(\$input: StudioQueryInput!) { queryStudios(input: \$input) { count studios " +
            "{ id name aliases is_favorite deleted images { $IMAGE } parent { id name } } } }"

    // child_studios is deprecated on master in favour of sub_studios(input); kept because an older deployed schema has only child_studios.
    const val STUDIO =
        "query Studio(\$id: ID!) { findStudio(id: \$id) { id name aliases is_favorite deleted images { $IMAGE } $URLS " +
            "parent { id name } child_studios { id name } } }"

    const val TAGS = "query Tags(\$input: TagQueryInput!) { queryTags(input: \$input) { count tags { id name description aliases category { name } } } }"

    const val TAG_CATEGORIES = "query TagCategories { queryTagCategories { count tag_categories { id name group description } } }"

    const val TAG = "query Tag(\$id: ID!) { findTag(id: \$id) { id name description aliases category { name } } }"

    const val ME = "query Me { me { id name } }"

    const val ME_KEY = "query MeKey { me { api_key } }"

    const val FAV_PERFORMER = "mutation FavPerformer(\$id: ID!, \$f: Boolean!) { favoritePerformer(id: \$id, favorite: \$f) }"

    const val FAV_STUDIO = "mutation FavStudio(\$id: ID!, \$f: Boolean!) { favoriteStudio(id: \$id, favorite: \$f) }"

    /** The operation name, sent as operationName (the test MockEngine routes on it). */
    fun nameOf(query: String): String = query.substringAfter(' ').substringBefore('(').substringBefore(' ').trim()
}
