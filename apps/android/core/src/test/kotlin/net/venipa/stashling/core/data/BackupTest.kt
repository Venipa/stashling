package net.venipa.stashling.core.data

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BackupTest {
    private val settings = Settings(
        theme = ThemeMode.DARK,
        oledBlack = true,
        appLock = true,
        appLockTimeoutSec = 300,
        sceneListMode = ListMode.ROWS,
        performerGender = PerformerGender.FEMALE,
        whisparr = WhisparrDefaults("/data", 4, "none", false, listOf(1, 2)),
        presets = listOf(WhisparrPreset("p1", "4K", "/4k", 7)),
        tagRules = listOf(TagRule("t1", "Tag one", "p1")),
        recentSearches = listOf("a", "b"),
        haptics = false,
    )
    private val backup = Backup(
        createdAt = 1,
        settings = settings,
        bookmarks = listOf(Bookmark(BookmarkKind.SCENE, "s1", "Scene", null, "https://img", 10), Bookmark(BookmarkKind.TAG, "t1", "Tag", "sub", null, 11)),
        hidden = listOf(Hidden(HiddenKind.PERFORMER, "p9", "Name", null, 20)),
        accounts = BackupAccounts(StashAccount("https://stashdb.org", "k"), WhisparrAccount("http://10.0.0.2:6969", "w", true)),
    )

    @Test
    fun `round trip keeps everything`() {
        assertEquals(backup, BackupCodec.decode(BackupCodec.encode(backup)))
        val noAccounts = backup.copy(accounts = null)
        assertEquals(noAccounts, BackupCodec.decode(BackupCodec.encode(noAccounts)))
    }

    @Test
    fun `rejects garbage, wrong app, newer version and bad shapes`() {
        fun reason(text: String) = assertFailsWith<BackupException> { BackupCodec.decode(text) }.reason
        val good = BackupCodec.encode(backup)
        assertEquals(BackupException.Reason.Invalid, reason("not json"))
        assertEquals(BackupException.Reason.Invalid, reason("[1,2]"))
        assertEquals(BackupException.Reason.Invalid, reason("{}"))
        assertEquals(BackupException.Reason.Invalid, reason(good.replace("\"stashling\"", "\"other\"")))
        assertEquals(BackupException.Reason.Newer, reason(good.replace("\"version\": 1", "\"version\": 2")))
        assertEquals(BackupException.Reason.Invalid, reason(good.replace("\"version\": 1", "\"version\": 0")))
        assertEquals(BackupException.Reason.Invalid, reason(good.replace("\"DARK\"", "\"PURPLE\"")))
        assertEquals(BackupException.Reason.Invalid, reason("""{"app":"stashling","version":1,"createdAt":1}"""))
        assertEquals(BackupException.Reason.Invalid, reason(good.replace("https://stashdb.org", "http://stashdb.org")))
    }

    @Test
    fun `merge unions by key, newer wins, keeps local cursor`() {
        val local = Restored(
            Settings(historyCursor = 99, presets = listOf(WhisparrPreset("p1", "old"), WhisparrPreset("p2", "local")), tagRules = listOf(TagRule("t1", "x", "p2"), TagRule("t2", "y", "p2")), recentSearches = listOf("b", "c")),
            listOf(Bookmark(BookmarkKind.SCENE, "s1", "Newer local", null, null, 50), Bookmark(BookmarkKind.SCENE, "s2", "Only local", null, null, 1)),
            listOf(Hidden(HiddenKind.PERFORMER, "p9", "Old", null, 5)),
        )
        val m = backup.restore(local, replace = false)
        assertEquals(99, m.settings.historyCursor)
        assertEquals(ThemeMode.DARK, m.settings.theme)
        assertEquals(listOf("p2", "p1"), m.settings.presets.map { it.id })
        assertEquals("4K", m.settings.presets.last().name)
        assertEquals(listOf("t1" to "p1", "t2" to "p2"), m.settings.tagRules.map { it.stashTagId to it.presetId })
        assertEquals(listOf("a", "b", "c"), m.settings.recentSearches)
        assertEquals(setOf("Newer local", "Only local", "Tag"), m.bookmarks.map { it.title }.toSet())
        assertEquals("Name", m.hidden.single().name)

        val r = backup.restore(local, replace = true)
        assertEquals(backup.bookmarks, r.bookmarks)
        assertEquals(backup.settings.copy(historyCursor = 99), r.settings)
    }
}
