package net.venipa.stashling.feature.common

import kotlinx.coroutines.CancellationException
import net.venipa.stashling.R
import net.venipa.stashling.core.stash.SceneFilter
import net.venipa.stashling.core.stash.StashDbApi
import net.venipa.stashling.core.stash.TagFilter

/** Scene region (Home and Browse filter sheets): JAV = scenes tagged JAV, Western = every scene without that tag. */
enum class Region(val label: Int) {
    All(R.string.home_region_all),
    Jav(R.string.home_region_jav),
    Western(R.string.home_region_western),
}

/** [jav] null = tag unknown, region has no effect. Western's exclude is ignored by stash-box while tags are set. */
fun SceneFilter.withRegion(region: Region, jav: String?): SceneFilter = if (jav == null) {
    this
} else {
    when (region) {
        Region.All -> this
        Region.Jav -> copy(tagIds = tagIds + jav)
        Region.Western -> copy(excludeTagIds = excludeTagIds + jav)
    }
}

/** The "JAV" tag id (by name, then alias); null when not found or the call fails (the region control stays hidden). */
suspend fun javTagId(stash: StashDbApi): String? = try {
    val q = stash.tags(TagFilter(names = "JAV"), 1).items
    (q.firstOrNull { it.name.equals("JAV", true) } ?: q.firstOrNull { t -> t.aliases.any { it.equals("JAV", true) } })?.id
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    null
}
