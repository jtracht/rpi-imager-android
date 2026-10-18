package de.jtracht.piimager.data

import org.junit.Assert.*
import org.junit.Test

class CatalogSelectionTest {
    private val pi5 = PiModel("Pi 5", "", setOf("pi5-64bit", "pi5-32bit"), inclusive = false)
    private val pi4 = PiModel("Pi 4", "", setOf("pi4-64bit", "pi4-32bit"), inclusive = true)
    private fun image(name: String, tags: Set<String>? = null, description: String = "") = OsEntry(name, description, url = "https://example.invalid/$name.img", devices = tags)

    @Test fun matchesAnyAdvertisedBoardTagRatherThanAllTags() {
        val compatible = image("64-bit", setOf("pi5-64bit", "pi4-64bit"))
        val wrong = image("Pi 1 only", setOf("pi1-32bit"))
        assertEquals(listOf(compatible), CatalogSelection.compatible(listOf(compatible, wrong), pi5))
        assertTrue(CatalogSelection.supports(compatible, pi4))
    }

    @Test fun inclusiveKeepsUnspecifiedButExclusiveDropsIt() {
        val unknown = image("generic")
        assertEquals(listOf(unknown), CatalogSelection.compatible(listOf(unknown), pi4))
        assertTrue(CatalogSelection.compatible(listOf(unknown), pi5).isEmpty())
    }

    @Test fun explicitEmptyDevicesNeverMatchesASelectedBoard() {
        val empty = image("no supported boards", emptySet())
        assertFalse(CatalogSelection.supports(empty, pi4))
        assertFalse(CatalogSelection.supports(empty, pi5))
        assertTrue(CatalogSelection.supports(empty, null))
    }

    @Test fun noFilteringRestoresAllImagesIncludingExplicitEmptyTags() {
        val entries = listOf(image("unknown"), image("old", setOf("pi1-32bit")), image("empty", emptySet()))
        assertEquals(entries, CatalogSelection.compatible(entries, null))
        assertEquals(entries, CatalogSelection.compatible(entries, pi5.copy(tags = emptySet())))
    }

    @Test fun recursivelyPrunesEmptyCategoriesWithoutMutatingOriginal() {
        val keep = image("match", setOf("pi5-32bit"))
        val drop = image("old", setOf("pi1-32bit"))
        val group = OsEntry("OS", children = listOf(keep, drop), devices = setOf("parent metadata ignored"))
        val empty = OsEntry("Old", children = listOf(drop))
        val filtered = CatalogSelection.compatible(listOf(group, empty), pi5)
        assertEquals(listOf(group.copy(children = listOf(keep))), filtered)
        assertEquals(listOf(keep, drop), group.children)
    }

    @Test fun remoteCategoriesRemainNavigableUntilTheirContentsAreKnown() {
        val remote = OsEntry("Remote", sublist = "https://example.invalid/list.json")
        assertEquals(listOf(remote), CatalogSelection.compatible(listOf(remote), pi5))
        assertTrue(CatalogSelection.compatible(listOf(remote.copy(devices = setOf("pi1-32bit"))), pi5).isEmpty())
        assertTrue(CatalogSelection.compatible(listOf(OsEntry("empty category")), pi5).isEmpty())
    }

    @Test fun searchMatchesAllWordsAcrossNameAndDescriptionIgnoringCase() {
        val entry = image("Ubuntu Server", description = "64-bit Linux")
        assertEquals(listOf(entry), CatalogSelection.search(listOf(entry), "  UBUNTU\t64-bit  "))
        assertTrue(CatalogSelection.search(listOf(entry), "ubuntu desktop").isEmpty())
        assertEquals(listOf(entry), CatalogSelection.search(listOf(entry), " \n "))
    }

    @Test fun descendantMatchesPreserveNavigationAndMatchingCategoriesRetainTheirContents() {
        val entry = image("Ubuntu")
        val other = image("Debian")
        val group = OsEntry("Linux", children = listOf(entry, other))
        val parent = OsEntry("Systems", children = listOf(group))
        assertEquals(listOf(parent.copy(children = listOf(group.copy(children = listOf(entry))))), CatalogSelection.search(listOf(parent), "Ubuntu"))
        assertEquals(listOf(parent), CatalogSelection.search(listOf(parent), "Linux"))
        assertEquals(listOf(parent), CatalogSelection.search(listOf(parent), ""))
    }

    @Test fun searchCannotReintroduceIncompatibleImages() {
        val group = OsEntry("Ubuntu", children = listOf(image("old", setOf("pi1-32bit")), image("new", setOf("pi5-64bit"))))
        val results = CatalogSelection.search(CatalogSelection.compatible(listOf(group), pi5), "Ubuntu")
        assertEquals(listOf("new"), results.single().children.map { it.name })
    }
}
