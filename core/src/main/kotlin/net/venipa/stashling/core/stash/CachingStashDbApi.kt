package net.venipa.stashling.core.stash

/**
 * In-memory cache over [StashDbApi]: navigating back to a list or detail shows the last result without a request.
 * Only an explicit refresh (pull-to-refresh) calls [invalidate] and reloads. Errors are never cached.
 * shortcut: process-lifetime LRU, no TTL; add a TTL if stale data ever matters.
 */
class CachingStashDbApi(
    private val delegate: StashDbApi,
    private val maxEntries: Int = 300,
) : StashDbApi by delegate {
    private val cache =
        object : LinkedHashMap<String, Any?>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Any?>) = size > maxEntries
        }

    /** Drops everything ([key] null) or the entries whose key contains [key] (an id or an operation name). */
    fun invalidate(key: String? = null) = synchronized(cache) {
        if (key == null) cache.clear() else cache.keys.removeAll { it.contains(key) }
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> cached(
        key: String,
        load: suspend () -> T,
    ): T {
        synchronized(cache) { if (cache.containsKey(key)) return cache[key] as T }
        val value = load()
        synchronized(cache) { cache[key] = value }
        return value
    }

    override suspend fun scenes(filter: SceneFilter, page: Int, perPage: Int) = cached("scenes|$filter|$page|$perPage") { delegate.scenes(filter, page, perPage) }

    override suspend fun searchScenes(term: String, page: Int, perPage: Int) = cached("searchScenes|$term|$page|$perPage") { delegate.searchScenes(term, page, perPage) }

    override suspend fun scene(id: String) = cached("scene|$id") { delegate.scene(id) }

    override suspend fun performers(filter: PerformerFilter, page: Int, perPage: Int) = cached("performers|$filter|$page|$perPage") { delegate.performers(filter, page, perPage) }

    override suspend fun searchPerformers(term: String, page: Int, perPage: Int) = cached("searchPerformers|$term|$page|$perPage") { delegate.searchPerformers(term, page, perPage) }

    override suspend fun performer(id: String) = cached("performer|$id") { delegate.performer(id) }

    override suspend fun studios(filter: StudioFilter, page: Int, perPage: Int) = cached("studios|$filter|$page|$perPage") { delegate.studios(filter, page, perPage) }

    override suspend fun studio(id: String) = cached("studio|$id") { delegate.studio(id) }

    override suspend fun tags(filter: TagFilter, page: Int, perPage: Int) = cached("tags|$filter|$page|$perPage") { delegate.tags(filter, page, perPage) }

    override suspend fun tagCategories() = cached("tagCategories") { delegate.tagCategories() }

    override suspend fun tag(id: String) = cached("tag|$id") { delegate.tag(id) }

    // Favorite changes: drop the entity and every list that can show favorites, so the heart and favorite lists stay true.
    override suspend fun setFavoritePerformer(id: String, favorite: Boolean) {
        delegate.setFavoritePerformer(id, favorite)
        dropFavorites(id)
    }

    override suspend fun setFavoriteStudio(id: String, favorite: Boolean) {
        delegate.setFavoriteStudio(id, favorite)
        dropFavorites(id)
    }

    private fun dropFavorites(id: String) = synchronized(cache) {
        cache.keys.removeAll { it.contains(id) || it.startsWith("performers|") || it.startsWith("studios|") || (it.startsWith("scenes|") && !it.contains("favorites=null")) }
    }
}
