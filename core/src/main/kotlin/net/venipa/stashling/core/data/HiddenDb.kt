package net.venipa.stashling.core.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class HiddenKind { SCENE, PERFORMER }

/** A scene or performer the user swiped away on Home; managed (unhidden) in Settings. */
data class Hidden(val kind: HiddenKind, val stashId: String, val name: String, val imageUrl: String?, val hiddenAt: Long)

/** Ids to filter scene lists with: a scene is hidden when its id or any of its performer ids is in here. */
data class HiddenIds(val scenes: Set<String> = emptySet(), val performers: Set<String> = emptySet()) {
    fun hides(sceneId: String, performerIds: List<String>) = sceneId in scenes || performerIds.any { it in performers }
}

@Entity(tableName = "hidden", primaryKeys = ["kind", "stashId"])
data class HiddenEntity(
    val kind: HiddenKind,
    val stashId: String,
    val name: String,
    val imageUrl: String?,
    val hiddenAt: Long,
)

@Dao
interface HiddenDao {
    @Query("SELECT * FROM hidden ORDER BY hiddenAt DESC")
    fun all(): Flow<List<HiddenEntity>>

    @Upsert
    suspend fun upsert(e: HiddenEntity)

    @Query("DELETE FROM hidden WHERE kind = :kind AND stashId = :stashId")
    suspend fun delete(kind: HiddenKind, stashId: String)
}

class HiddenRepository(private val dao: HiddenDao) {
    fun observe(): Flow<List<Hidden>> = dao.all().map { rows -> rows.map { Hidden(it.kind, it.stashId, it.name, it.imageUrl, it.hiddenAt) } }

    fun ids(): Flow<HiddenIds> = dao.all().map { rows ->
        HiddenIds(
            scenes = rows.filter { it.kind == HiddenKind.SCENE }.mapTo(HashSet()) { it.stashId },
            performers = rows.filter { it.kind == HiddenKind.PERFORMER }.mapTo(HashSet()) { it.stashId },
        )
    }

    suspend fun hide(item: Hidden) = dao.upsert(HiddenEntity(item.kind, item.stashId, item.name, item.imageUrl, item.hiddenAt))

    suspend fun unhide(kind: HiddenKind, stashId: String) = dao.delete(kind, stashId)
}
