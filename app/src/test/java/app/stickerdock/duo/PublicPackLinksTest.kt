package app.stickerdock.duo

import org.junit.Assert.*
import org.junit.Test

class PublicPackLinksTest {
    @Test fun sharedMessageResolvesOnePublicId() {
        assertEquals("chai-reactions", PublicPackLinks.parse("SD:chai-reactions"))
        assertEquals("chai-reactions", PublicPackLinks.parse(PublicPackLinks.link("chai-reactions")))
        assertEquals("chai-reactions", PublicPackLinks.fromSharedText(PublicPackLinks.shareText("Chai", "chai-reactions")))
        assertNull(PublicPackLinks.fromSharedText("SD:first-pack\nSD:second-pack"))
    }

    @Test fun externalHostsEncodedPathsAndExtraParametersAreRejected() {
        for (bad in listOf("https://evil.example/pack/name", "stickerdock://pack.evil/name", "stickerdock://user@pack/name",
            "stickerdock://pack/../name", "stickerdock://pack/%6Eame", "stickerdock://pack/name?url=https://evil.example",
            "stickerdock://pack/name#auto-save", "stickerdock://pack/name/", "SD:../name", "SD:a", "SD:" + "x".repeat(61))) {
            assertNull(bad, PublicPackLinks.parse(bad))
        }
    }
}
