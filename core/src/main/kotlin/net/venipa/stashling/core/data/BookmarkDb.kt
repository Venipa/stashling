package net.venipa.stashling.core.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Entity(tableName = "bookmark", primaryKeys = ["kind", "stashId"], indices = [Index("addedAt")])
data class BookmarkEntity(
    val kind: BookmarkKind,
    val stashId: String,
    val title: String,
    val subtitle: String?,
    val imageUrl: String?,
    val addedAt: Long,
)

@Dao
interface BookmarkDao {
    // rowid breaks ties: of two rows added in the same millisecond the later one sorts first.
    @Query("SELECT * FROM bookmark ORDER BY addedAt DESC, rowid DESC")
    fun all(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmark WHERE kind = :kind ORDER BY addedAt DESC, rowid DESC")
    fun byKind(kind: BookmarkKind): Flow<List<BookmarkEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM bookmark WHERE kind = :kind AND stashId = :stashId)")
    fun exists(kind: BookmarkKind, stashId: String): Flow<Boolean>

    @Upsert
    suspend fun upsert(e: BookmarkEntity)

    @Query("DELETE FROM bookmark WHERE kind = :kind AND stashId = :stashId")
    suspend fun delete(kind: BookmarkKind, stashId: String)

    // OR REPLACE: when newId is already bookmarked, that row wins the key and the old one goes.
    @Query("UPDATE OR REPLACE bookmark SET stashId = :newId WHERE kind = :kind AND stashId = :oldId")
    suspend fun rekey(kind: BookmarkKind, oldId: String, newId: String)
}

@Database(entities = [BookmarkEntity::class], version = 1, exportSchema = true)
abstract class StashlingDatabase : RoomDatabase() {
    abstract fun bookmarks(): BookmarkDao

    companion object {
        /** File name is fixed: data_extraction_rules.xml keeps it (and -wal/-shm) out of backup. */
        const val NAME = "stashling.db"

        // Platform SQLite (no bundled driver).
        fun open(context: Context): StashlingDatabase = Room.databaseBuilder(context.applicationContext, StashlingDatabase::class.java, NAME).build()
    }
}

class RoomBookmarkRepository(private val dao: BookmarkDao) : BookmarkRepository {
    override fun observe(kind: BookmarkKind?): Flow<List<Bookmark>> = (if (kind == null) dao.all() else dao.byKind(kind)).map { rows -> rows.map { it.toModel() } }

    override fun isBookmarked(kind: BookmarkKind, stashId: String): Flow<Boolean> = dao.exists(kind, stashId)

    override suspend fun add(bookmark: Bookmark) = dao.upsert(bookmark.toEntity())

    override suspend fun remove(kind: BookmarkKind, stashId: String) = dao.delete(kind, stashId)

    override suspend fun rekey(kind: BookmarkKind, oldId: String, newId: String) {
        if (oldId != newId) dao.rekey(kind, oldId, newId)
    }
}

private fun BookmarkEntity.toModel() = Bookmark(kind, stashId, title, subtitle, imageUrl, addedAt)

private fun Bookmark.toEntity() = BookmarkEntity(kind, stashId, title, subtitle, imageUrl, addedAt)
