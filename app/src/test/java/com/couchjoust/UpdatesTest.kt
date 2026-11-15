package com.couchjoust

import org.junit.Assert.*
import org.junit.Test

class UpdatesTest {
    private fun release(tag: String, preview: Boolean) = AppRelease(tag, "Notes for $tag", "https://github.com/example.apk", 123, preview)

    @Test fun semanticVersionOrderingIncludesNumericPrereleasesAndStablePromotion() {
        val ordered = listOf("1.9.0", "1.10.0", "2.0.0-alpha.1", "2.0.0-alpha.2", "2.0.0-alpha.10", "2.0.0-beta", "2.0.0-rc.1", "2.0.0", "2.0.1")
        assertEquals(ordered, ordered.reversed().sortedBy { ReleaseVersion.parse(it) })
        assertEquals(0, ReleaseVersion.parse("v2.0.0+build.1").compareTo(ReleaseVersion.parse("2.0.0+build.2")))
    }

    @Test fun stableBuildsExcludePrereleasesAndNeverOfferOlderOrEqualVersions() {
        val releases = listOf(release("v2.0.0", false), release("v1.9.0", false), release("v3.0.0-alpha.1", true), release("v2.1.0", true), release("v2.0.1", false))
        assertEquals(listOf("v2.0.1"), GitHubUpdates.newer(releases, ReleaseVersion.parse("2.0.0")).map { it.tag })
    }

    @Test fun previewBuildsReceiveAllNewerNotesInDescendingSemanticOrder() {
        val releases = listOf(release("v2.0.0-alpha.2", true), release("v1.0.0", false), release("v2.0.0", false), release("v2.0.0-alpha.10", true))
        assertEquals(listOf("v2.0.0", "v2.0.0-alpha.10", "v2.0.0-alpha.2"), GitHubUpdates.newer(releases, ReleaseVersion.parse("2.0.0-alpha.1")).map { it.tag })
        assertTrue(GitHubUpdates.newer(releases, ReleaseVersion.parse("2.0.0")).isEmpty())
    }
}
