package app.stickerdock.duo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CommunityCatalogTest {
    private val base = "https://example.supabase.co/storage/v1/object/public/factory/catalog/"
    private val hashA = "a".repeat(64)
    private val hashB = "b".repeat(64)

    @Test
    fun exposesOnlyExplicitGeneralEntries() {
        val listings = parse(
            pack("safe-pack", "safe.zip", hashA, "GENERAL") + "," +
                pack("mature-pack", "mature.zip", hashB, "MATURE") + "," +
                packWithoutRating("missing-rating", "missing.zip", "c".repeat(64))
        )

        assertEquals(1, listings.size)
        assertEquals("safe-pack", listings.single().id)
        assertEquals(base + "packs/safe.zip", listings.single().downloadUrl)
        assertTrue(listings.single().categories.isEmpty())
    }

    @Test
    fun parsesValidatedFactoryCategories() {
        val listing = parse(
            """{"id":"topic-pack","name":"Topics","creator":"StickerDock","file":"topics.zip","sha256":"$hashA","rating":"GENERAL","categories":["NEWS","MOVIES","HINGLISH"]}"""
        ).single()

        assertEquals(listOf(PackCategory.NEWS, PackCategory.MOVIES, PackCategory.HINGLISH), listing.categories)
        assertRejected("Duplicate public pack category") {
            parse(
                """{"id":"duplicate-topics","name":"Topics","creator":"StickerDock","file":"topics.zip","sha256":"$hashA","rating":"GENERAL","categories":["NEWS","NEWS"]}"""
            )
        }
        assertRejected("Invalid public pack category") {
            parse(
                """{"id":"bad-topic","name":"Topics","creator":"StickerDock","file":"topics.zip","sha256":"$hashA","rating":"GENERAL","categories":["TRENDING"]}"""
            )
        }
    }

    @Test
    fun rejectsDuplicateGeneralIds() {
        assertRejected("Duplicate public pack id") {
            parse(
                pack("same-id", "one.zip", hashA, "GENERAL") + "," +
                    pack("same-id", "two.zip", hashB, "GENERAL")
            )
        }
    }

    @Test
    fun rejectsDuplicateGeneralFiles() {
        assertRejected("Duplicate public pack file") {
            parse(
                pack("first-id", "same.zip", hashA, "GENERAL") + "," +
                    pack("second-id", "same.zip", hashB, "GENERAL")
            )
        }
    }

    @Test
    fun rejectsMalformedChecksumAndControlText() {
        assertRejected("Invalid public pack checksum") {
            parse(pack("bad-hash", "bad.zip", "not-a-hash", "GENERAL"))
        }
        assertRejected("Invalid public pack name") {
            parse(
                """{"id":"bad-name","name":"Bad\nName","creator":"Tester","file":"bad-name.zip","sha256":"$hashA","rating":"GENERAL"}"""
            )
        }
    }

    @Test
    fun rejectsUnsupportedVersionAndOversizedCatalog() {
        assertRejected("Unsupported catalog version") {
            CommunityCatalog.parseCatalog("""{"version":2,"packs":[]}""".toByteArray(), base)
        }

        val entries = (0..100).joinToString(",") { index ->
            pack("pack-$index", "pack-$index.zip", hashA, "GENERAL")
        }
        assertRejected("Catalog has too many packs") { parse(entries) }
    }

    private fun parse(entries: String): List<CommunityCatalog.Listing> =
        CommunityCatalog.parseCatalog(
            """{"version":1,"packs":[$entries]}""".toByteArray(),
            base
        )

    private fun pack(id: String, file: String, hash: String, rating: String): String =
        """{"id":"$id","name":"Pack $id","creator":"StickerDock","file":"$file","sha256":"$hash","rating":"$rating"}"""

    private fun packWithoutRating(id: String, file: String, hash: String): String =
        """{"id":"$id","name":"Pack $id","creator":"StickerDock","file":"$file","sha256":"$hash"}"""

    private fun assertRejected(message: String, block: () -> Unit) {
        try {
            block()
            fail("Expected rejection containing '$message'")
        } catch (error: IllegalArgumentException) {
            assertTrue(
                "Expected '$message' but was '${error.message}'",
                error.message?.contains(message) == true
            )
        }
    }
}
