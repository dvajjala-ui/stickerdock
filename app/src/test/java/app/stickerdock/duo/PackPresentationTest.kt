package app.stickerdock.duo

import org.junit.Assert.*
import org.junit.Test

class PackPresentationTest {
    @Test fun metadataMatchesStayGroupedInTheirPackAndRespectFilters() {
        val pack = StickerPack("p", "Friends", 1, 1, StickerKind.ANIMATED, category = PackCategory.REACTIONS)
        assertTrue(PackPresentation.matches(pack, "laugh", "All", null, setOf("p")))
        assertFalse(PackPresentation.matches(pack, "laugh", "Static", null, setOf("p")))
        assertFalse(PackPresentation.matches(pack, "laugh", "All", PackCategory.WORK, setOf("p")))
        assertFalse(PackPresentation.matches(pack, "laugh", "All", null, emptySet()))
        assertTrue(PackPresentation.matches(pack, "FRIEND", "Animated", null, emptySet()))
    }

    @Test fun oneInstalledAppGetsADirectActionOnlyAfterAllChecksComplete() {
        val personal = WhatsAppTarget.PERSONAL
        val business = WhatsAppTarget.BUSINESS
        assertNull(PackPresentation.singleTarget(emptyMap()))
        assertNull(PackPresentation.singleTarget(mapOf(personal to WhatsAppPackStatus(true, false))))
        assertEquals(personal, PackPresentation.singleTarget(mapOf(personal to WhatsAppPackStatus(true, false), business to WhatsAppPackStatus(false, false))))
        assertEquals(business, PackPresentation.singleTarget(mapOf(personal to WhatsAppPackStatus(false, false), business to WhatsAppPackStatus(true, true))))
        assertNull(PackPresentation.singleTarget(WhatsAppTarget.entries.associateWith { WhatsAppPackStatus(true, false) }))
        assertNull(PackPresentation.singleTarget(WhatsAppTarget.entries.associateWith { WhatsAppPackStatus(false, false) }))
    }

    @Test fun addedStateChangesTheActionWithoutClaimingToSendASticker() {
        val target = WhatsAppTarget.BUSINESS
        assertEquals("Add to Business", PackPresentation.actionLabel(target, mapOf(target to WhatsAppPackStatus(true, false))))
        assertEquals("Open Business", PackPresentation.actionLabel(target, mapOf(target to WhatsAppPackStatus(true, true))))
        assertEquals("Use in WhatsApp", PackPresentation.actionLabel(null, emptyMap()))
    }
}
