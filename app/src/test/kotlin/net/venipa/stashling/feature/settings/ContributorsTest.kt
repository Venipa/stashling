package net.venipa.stashling.feature.settings

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals

class ContributorsTest {
    @Test
    fun keepsOnlyGitHubLinks() {
        val json =
            """[{"login":"a","html_url":"https://github.com/a","avatar_url":"https://avatars.githubusercontent.com/u/1?v=4","contributions":3,"type":"User"},
               {"login":"b","html_url":"javascript:alert(1)","avatar_url":"https://avatars.githubusercontent.com/u/2"}]"""
        assertEquals(listOf(Contributor("a", "https://github.com/a", "https://avatars.githubusercontent.com/u/1?v=4", 3)), parseContributors(json))
    }

    @Test
    fun brokenFileShowsNone() = assertEquals(emptyList(), parseContributors("{"))

    @Test
    fun committedFallbackParses() = assertEquals(listOf("Venipa"), parseContributors(File("contributors.json").readText()).map { it.login })
}
